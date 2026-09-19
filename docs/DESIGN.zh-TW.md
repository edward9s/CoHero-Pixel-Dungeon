# CoHero Pixel Dungeon 設計共識

> 本文件記錄目前已討論並大致取得共識的核心設計，以及尚未決定的問題。目標是避免之後把暫時構想誤當成正式規格。

## 1. 專案目標

CoHero Pixel Dungeon 的核心不是重做 Shattered Pixel Dungeon，而是在盡可能保留 SPD 原有機制的前提下，加入一名無法直接控制的第二英雄，讓既有的探索、戰鬥與資源分配產生新的壓力。

最高方向：

- 盡可能少改 SPD 原本的系統。
- 優先沿用 SPD 現成的 AI、尋路、戰鬥與角色機制。
- 希望能持續跟上最新版 SPD，而不是形成難以同步的大型 fork。
- 未來若可行，希望像 SMM 一樣支援注射到其他 SPD fork。
- 對未知 fork 的能力與語意不做猜測；必要 ABI 不成立時應明確失敗，而不是偷偷相容。

## 2. 核心玩法

玩家直接控制一名正常的 Hero；另一名英雄是 AI 同伴。

已確定的基本規則：

- 玩家不能直接控制同伴的移動格、攻擊目標或每一步行動。
- 同伴有自己的 AI 路徑。
- 同伴有自己的背包，控制介面與玩家 Hero 分開。
- 地城中的可用資源需要在兩名角色之間分配。
- 玩家 Hero 死亡：Game Over。
- 同伴 Hero 死亡：Game Over。
- 玩家不能在同伴抵達本層出口旁的可離開位置前進入下一層。
- 真正的樓層 transition 永遠由玩家 Hero 觸發；CoHero 不直接切換樓層。
- 同伴沒有「停止行走」開關；持續前進本身就是壓力來源。
- CoHero 位於玩家 Hero 的 FOV 之外時仍保持可見，而且其自身 FOV 會作為「顯示層第二視野」正常照亮周圍地形、顯示其中角色並播放移動動畫。這個合併視野只用於畫面呈現；遊戲規則中的 `Dungeon.level.heroFOV` 仍只代表玩家 Hero 視野，不會讓卷軸、技能或敵人觸發條件把 CoHero 視野當成 Hero 視野。

設計重點不是「護送一個完全無能的 NPC」，而是：

> 玩家只直接控制其中一名英雄，卻必須為兩名英雄共同負責。

## 3. 同伴探索與路徑

存檔重新載入同一樓層時，CoHero 恢復保存時的原始格子，不重新生成到 Hero 身旁；只有真正進入新樓層時才在 Hero 鄰近可用格重新生成。

同伴不應從樓層開始就知道出口位置，否則它會變成出口指南針。

目前共識：

1. 出口尚未發現時，同伴自主探索未知區域。
2. 探索路徑可以帶有一定隨機性，但只能存在於合理選擇之間。
3. **發現出口本身不代表探索結束。** 只要仍有一般未探索 frontier，CoHero 可以繼續探索；出口只是已知的離層集合點，不是安全區。
4. 「尚有 frontier」只計算 CoHero 目前實際可經由 passable path 抵達的未知格；秘密區、隔離格或其他目前無路可達的未知格不會讓 AI 卡在反覆尋路。若已無可達 frontier 且 Hero 尚未站上出口，CoHero 會在「以 Hero 為中心、依實際可走 path distance 計算最近約 25% 的已探索可通行區域」自主遊走；不會提前守在出口，也不會再跑遍整張地圖。
5. 若玩家 Hero 已站在正常樓層出口上等待，這視為明確的離層意圖：CoHero 停止一般探索／遊走並立即往出口集合。
6. 同伴不進入出口 transition 本身，而是前往其周圍可站立的鄰格；此時不要求與 Hero 保留一格距離。
7. 同伴仍位於出口鄰格時，玩家 Hero 才取得使用出口的資格；若 Hero 已在出口等待，同伴抵達鄰格後由 Hero 的原生 transition 流程自動下樓。
8. 「已抵達出口」不是永久 flag；同伴若因戰鬥、擊退、傳送等原因離開出口旁，離開資格立即失效。

AI 不需要模擬真人玩家的完整戰術推理。毒氣等危險可優先沿用 SPD 現有 mob / ally 的避險與 pathfinding 行為；陷阱也不值得另外建立複雜推理系統。

### 自然回血

CoHero 使用獨立的 `CompanionRegeneration`：基礎速率固定為每 10 回合回復 1 HP（0.1 HP/turn），沒有 Hero 的飢餓限制，也不繼承 Hero 的 Chalice、Ring of Energy、Salt Cube 等加速。當 SPD 全域 `Regeneration.regenOn()` 被 LockedFloor 等機制關閉時，CoHero 同樣停止自然回血。

### 低血量時靠近 Hero

低血量不代表出口比較安全；出口只是離層節點，不能當作避難點。

CoHero 在沒有立即可見威脅時採用 hysteresis 式靠攏：

- HP 低於 35% 時進入低血量 rally 狀態，不再繼續一般探索，而是往玩家 Hero 靠近。
- HP 恢復到至少 60% 後才退出 rally 狀態並恢復一般探索，避免在單一門檻附近反覆切換。
- 一般情況下，CoHero 與 Hero 的理想距離是 2 格，也就是兩者中間保留一格；2～3 格都視為可接受，不為了精確距離每回合抖動。
- 若兩者已相鄰，CoHero 只有在存在可通行、無角色占用且不會主動驚動睡眠敵人的位置時才主動拉開。
- 有可見敵人時仍先執行既有戰鬥／逃生判斷；低血量 rally 不會讓 CoHero 無視眼前威脅硬走向 Hero。
- Hero 已站在正常樓層出口等待時，出口集合規則優先；此時不要求保留一格距離。

### 睡眠中的敵人

同伴不應主動吵醒正在睡覺的怪物。

- 睡眠怪物若在同伴的可視範圍內，不應被選為主動攻擊目標。
- 尋路時應盡量保持不會吵醒它的距離，能繞行就繞行。
- 這是避讓偏好，不需要為此建立完整戰術規劃；具體喚醒距離與無路可繞時的處理可在 prototype 中驗證。

## 4. AI 與角色實作基礎

第一個實作基礎應直接參考或繼承 SPD 既有的：

- `DriedRose.GhostHero`
- `DirectableAlly`
- 一般 `Mob` 的 wandering / hunting / pathfinding 行為

也就是說，不應先打造一套「會玩完整 SPD 的 Hero AI」。

概念上角色是第二英雄，但行為骨架是強化版 intelligent ally。

### 4.1 不採第二個真正的 Hero instance

目前正式方向是：

> `CoHeroAlly` 維持 `DirectableAlly` / ally actor，另外擁有自己需要的 Hero-like progression、背包與裝備資料。

不把 SPD 全面改造成 multi-Hero 架構，也不透過切換 `Dungeon.hero` 來讓原版系統誤以為 CoHero 是玩家 Hero。

原因：

- `Hero`、`Belongings`、Talent、UI 與大量物品行為直接假設唯一的 `Dungeon.hero`。
- 強行建立第二個完整 `Hero` 會讓 upstream 同步與跨 fork 注射成本快速增加。
- `DriedRose.GhostHero` 已證明 ally actor 可以自行模擬需要的 Hero-like 數值與裝備語意，而不需要把整個 SPD 改成多 Hero。

因此工程原則是：

- 能直接重用 `Char` 層效果就直接重用。
- 原版 API 若硬綁 `Hero`，優先在 CoHero 層建立小型 adapter，而不是修改大量 SPD 類別。
- 只有真正需要接入原版流程的位置才 patch integration seam；共享 EXP / level 不需要修改 Mob 的 EXP 流程。

### 4.2 為什麼不是完整 Hero AI

完整 Hero 的決策空間太大，包括 Talent、Subclass、主動技能、法術、消耗品、裝備搭配、長期資源保存等。尤其 Cleric 具有大量法術後，AI 必須判斷何時施法、施放哪一個、何時保留 charge，這會把專案重新拖回「寫一個會玩 SPD 的 bot」。

因此目前不把完整 Hero AI 當作目標。

### 預告攻擊與環境危險避讓

CoHero 會讀取 SPD 原版 `GameScene.targetedCell(cell, delay)` 所建立的危險格預告，而不是針對單一敵人硬編碼。危險格的有效期限與畫面警示完全使用同一個 `Actor.now() + delay` 時鐘。

- CoHero 若目前站在仍有效的預告格上，會在一般戰鬥、喝藥、探索與靠近 Hero 之前優先走到相鄰安全格。
- 有 active warning 時，普通尋路會暫時把所有預告格視為不可通行，因此 CoHero 不會從安全位置主動走進即將爆發的攻擊範圍。
- warning 到期後該格立即恢復正常尋路；換樓層時警示紀錄清空。
- CoHero 同時把原版持續性環境危險納入同一套移動遮罩：火焰、毒氣、酸蝕氣體、麻痺／混亂／惡臭氣體、電流、冰凍／暴風雪、Inferno 與 Vault flame traps。若 CoHero 對對應效果免疫，該 blob 不視為危險。
- 若目前正站在這些環境危險中，會像預告格一樣優先尋找相鄰安全格；一般尋路也不主動踏入已存在的危險 blob。
- `DelayedRockFall` 在存檔載入重建特效時，會按 buff 剩餘 `cooldown()` 重新登記危險格，因此地動法師／DM-300 已預告但尚未落下的岩石不會因讀檔而被 CoHero 忘記。
- 因此 Yog-Dzewa 光線、Gnoll Geomancer / DM-300 落石、Ripper Demon 跳躍、Vault Laser 等使用原版 targeted-cell 警示的攻擊可共用同一套避讓邏輯。Eye 的蓄力光線不是走這個 API，目前不在此泛用層內。
- 若 CoHero 被定身、麻痺，或所有相鄰合法格本身都危險／不可通行，AI 不會假裝能躲開，會繼續執行其他可行生存或戰鬥行為。

### CoHero 職業固有能力

職業特色是 CoHero 額外的固有 trait，不是假戒指，也不占用兩個實際 ring slot；CoHero 真正裝備的戒指仍照原版生效，並與固有 trait 疊加。

- **Warrior**：等價 `RingOfMight +0`：+1 STR、HT ×1.035；真正的 Ring of Might 可再疊加。
- **Mage**：法杖自然充能 ×1.175，等價 `RingOfEnergy +0` 的 wand charge；真正的 Ring of Energy 可再疊加。
  - 起始 `MagesStaff(WandOfMagicMissile)` 仍是原版 Mage's Staff；CoHero AI 直接使用 Staff 內嵌的原版 wand 與同一個 charge pool，不建立複製 wand。Staff 近戰與 wand 遠程能力都可使用。
- **Rogue**：移動速度 ×1.15，等價 `RingOfHaste +0`；真正的 Ring of Haste 可再疊加。
- **Huntress**：投擲武器傷害等級 +1、耐久 ×1.2，等價 `RingOfSharpshooting +0`；真正的 Sharpshooting 可再疊加。另保留原版 Huntress 的草地固有語意：踩高草只壓成 `FURROWED_GRASS`，踩已犁過的草不再壓平成普通草；不繼承 Hero 專屬草地 talents。
  - 起始 `SpiritBow` 仍是原版專武。CoHero 透過原版 `SpiritArrow` 射擊；箭為無限彈藥，不進普通投擲物耐久、掉落或回收流程。傷害使用 CoHero 自己的 STR、實際 Sharpshooting 戒指與 Huntress 固有 Sharpshooting +0，並排除 Hero-only talents。
- **Duelist**：裝備近戰武器時攻擊速度 ×1.09051，等價 `RingOfFuror +0`；真正的 Furor 可再疊加。
- **Cleric**：與 Hero 距離不超過 6 格時，Hero 與 Cleric CoHero 的 accuracy / evasion 各 ×1.10，不要求直線視野。
- **其他／第三方 HeroClass**：Generalist，HT ×1.05；未知職業使用通用短劍作為安全起始武器，不因缺少 stock case 直接失敗。

### Hero / CoHero 畫面外監控

CoHero 自主探索不應迫使玩家反覆拖動畫面找人，因此 GameScene 提供雙向畫面外 locator：

- Hero 與 CoHero 都位於目前主鏡頭 viewport 內時 locator 隱藏。
- 若只有一人離開畫面，locator 自動切換成離屏者的頭像、HP 與 buff icons，並沿正確方向指向該角色。
- 點擊 locator 只將主鏡頭平滑移向目前代表的角色；鏡頭移到 CoHero 後，如果 Hero 因此離屏，locator 立即切成 Hero，再點即可切回。控制權始終仍在 Hero。
- 若使用者手動把鏡頭移到兩人都不在畫面，locator 顯示距離目前鏡頭中心較近的角色。
- locator 永久顯示目前代表角色的即時 HP bar；血條下方最多顯示 6 個小型 buff icons。
- CoHero 處於低血量 rally 狀態且 locator 正代表 CoHero 時顯示固定警示符號，不使用持續閃爍。
- locator 的活動邊界排除 Status/Menu/Boss/Toolbar/Inventory 與 tag 控制區，不覆蓋主要操作按鈕。
- locator 目前代表 CoHero 時，長按 locator 直接開啟 CoHero 背包；代表 Hero 時長按不執行額外動作。
- CoHero 本人在畫面內時，頭上血條即使滿血也始終顯示。

## 5. 玩家對同伴的控制

目前已取消所有直接的行為命令。

玩家不應有：

- 走 / 停開關。
- 攻擊 / 避戰開關。
- 指定目的地。
- 指定攻擊目標。

玩家對同伴的控制主要透過**背包與裝備配置**完成。

也就是說，玩家不是告訴同伴「現在要做什麼」，而是決定它「現在具備哪些行動能力與資源」。

這樣可以避免玩家把同伴停在安全處，也避免系統退化成第二角色的間接遙控。

## 6. 背包與裝備驅動 AI

目前最重要的設計共識是：

> 不要用職業名稱硬編同伴行為；同伴應根據自己實際擁有的裝備、當前距離、目標特性與可造成的傷害決定行動。

這同時改善玩法與跨 fork 相容性。

### 6.1 基本攻擊規則

1. **沒有任何可用攻擊能力時**
   - 同伴不主動攻擊。
   - 盡量躲避敵人並繼續探索 / 前進。
   - 「沒有近戰武器」不等於沒有攻擊能力；只要背包中仍有合法投擲武器或合法法杖，CoHero 仍可進行遠程攻擊。

2. **有近戰武器時**
   - 可以進行普通近戰攻擊。
   - 當敵人已進入該近戰武器的合法攻擊距離時，只使用近戰武器，不改用投擲武器或法杖。

3. **有投擲武器時**
   - 在合法的遠程距離下，可以使用已明確支援的投擲武器攻擊。
   - 第一版明確支援：`ThrowingStone`、`ThrowingKnife`、`ThrowingSpike`、`FishingSpear`、`ThrowingClub`、`ThrowingSpear`、`Kunai`、`Bolas`、`Javelin`、`Tomahawk`、`Trident`、`ThrowingHammer`。
   - `Shuriken`、`HeavyBoomerang`、`ForceCube`、`Dart/TippedDart` 等具有額外 Hero-specific 使用語意的類型先 fail closed。
   - 投出的武器以 `setID` 追蹤；沒有可見威脅時，CoHero 會優先走向並拾回自己仍留在本層地面的投擲武器。
   - 換樓層時清除尚未回收的投擲物追蹤，不跨樓層追索。

4. **有法杖時**
   - 在合法目標與距離下，可以使用已明確支援的攻擊型法杖。
   - 法杖必須已鑑定、未詛咒且有足夠 charge 才是合法候選。
   - 目前明確支援 `WandOfMagicMissile`、`WandOfFrost`、`WandOfDisintegration`、`WandOfLightning`、`WandOfPrismaticLight`、`WandOfRegrowth`、`WandOfTransfusion`、`WandOfCorruption`、`WandOfCorrosion`、`WandOfFireblast`。
   - `WandOfLivingEarth` 暫不支援，因為 Earth Guardian / RockArmor ownership 與多個 Hero-specific 系統高度耦合。
   - `WandOfFrost` 不對已處於 `Frost` 的目標施放；其傷害評估會按目標目前的 `Chill` 程度折減。
   - 解離法杖會檢查整條有效射線，若會傷及友軍或主動波及睡眠敵人就不施放。
   - 雷霆法杖沿用原版 chain / `affected` 規則；AI 也用同一套連鎖範圍估算整體傷害，若連鎖會反彈到 CoHero、傷及中立角色或主動波及睡眠敵人則不施放。
   - 酸蝕法杖不固定瞄準敵人本格。AI 會枚舉目標本格與真正相鄰、可合法命中的落點，連同場上既有酸蝕氣體依原版 `Blob.evolve()` 規則模擬後續擴散；近期會波及 Hero、CoHero、中立／友軍或睡眠敵人的方案直接淘汰，再從剩餘方案中優先選友軍長期風險較低、清醒敵人覆蓋較高且能較快覆蓋主目標的落點。
   - 焰浪之杖依原版目前 charge 決定 1–3 charge、5/7/9 格與 50°/70°/90° cone。AI 會枚舉主目標附近的瞄準方向，以原版 `ConeAOE` 計算實際命中格；會直接命中友軍／中立角色或額外波及睡眠敵人的方向不施放，並重現 cone 外額外點燃可燃地形的規則。其餘方向優先降低火勢向我方與睡眠敵人擴散的風險，再比較整體預估傷害。
   - 稜光法杖對不死／惡魔的額外傷害會納入傷害估算。
   - 不要求 AI 做完整的長期 charge 規劃。
   - 未知或無法安全判斷用途的 Wand 不應由 AI 猜測使用方式。

### 6.2 遠程攻擊選擇

戰鬥目標目前取 CoHero 視野內最近的可見、清醒敵人；睡眠中的敵人不列入主動攻擊目標。

遠程選擇不應依職業決定，而應依目標與當前可用裝備決定。

規則如下：

1. **近戰武器可及時只用近戰武器。**
   - 不使用法杖或投擲武器取代合法的近戰攻擊。

2. **魔法免疫目標不使用法杖。**
   - 對具有魔法免疫或其他明確免疫法杖攻擊的目標，法杖直接排除出候選。

3. **高閃避目標優先考慮法杖。**
   - 第一版將「高閃避」定義為：目標 `defenseSkill` 高於目前可用投擲武器中最高的物理 `attackSkill`。
   - 若符合此條件且存在合法法杖候選，優先從法杖中選擇。

4. **最重要的通則：在當前距離下，傷害能力使用可用選項中預估傷害最高者。**
   - 比較的是當前距離下實際可使用的候選。
   - 距離限制、魔法免疫與高閃避等條件先決定候選與優先資格，再由傷害決定實際使用哪個攻擊。
   - 不應因為角色職業名稱而強制固定武器類型。
   - AI 評分不得為了比較候選而提前消耗真正攻擊用的亂數；第一版使用 min/max 的算術平均作穩定傷害估算，投擲武器再加上平均剩餘力量加成。
   - 相同估算傷害時優先投擲武器，避免無必要消耗 wand charge。
   - CoHero 自己裝備的 `RingOfSharpshooting` 會影響其投擲武器傷害與耐久；玩家 Hero 的 Sharpshooting、Talent 或其他 Hero-only 投擲加成不得洩漏到 CoHero。
   - CoHero 法杖傷害使用自己的普通 RNG，不繼承玩家 Hero 的 Clover 類 RNG、`WandEmpower` 或其他 Hero-only cast 效果。
   - 腐化屬控制能力：若依原版 resistance 計算本次能直接跨過腐化門檻，優先於一般遠程傷害；否則只在沒有直接傷害候選時作為 fallback debuff/control。
   - 注魂對不死敵人視為直接傷害；對活敵視為 fallback Charm/control，不拿 0 傷害去和普通武器比較。
   - 再生屬逃生 utility，不進入傷害排名。第一版只在 CoHero 原本就要逃跑時使用，且 cone 必須至少能定身一名尚未 Root 的追兵，並且不能波及友軍或睡眠敵人。
   - 注魂支援第一版只用於玩家 Hero：脫離戰鬥時，Hero 低於 50% HP、CoHero 至少 75% HP，且 5% HT 的自傷後 CoHero 仍高於 50% HP 才允許血量轉移。

### 6.3 背包就是控制介面

CoHero 背包視窗頂部固定顯示目前即時基本數值：Lv、HP（有護盾時顯示為 `HP+shield/HT`）、STR、近戰 DMG 範圍、DR 範圍與實際 SPD 倍率。這些值直接由 CoHero 當前裝備、固有 trait、戒指與負重計算，不保存第二份 UI 專用數值。

背包不只是儲物空間，而是玩家間接塑造同伴 AI 的方式：

- 給近戰武器 → 同伴取得近戰能力。
- 給投擲武器 → 同伴取得遠程物理攻擊選項。
- 給法杖 → 同伴取得魔法遠程攻擊選項。
- 不給任何合法攻擊能力 → 同伴不主動戰鬥，偏向避敵。
- CoHero 原則上不自行使用消耗品；目前例外是已鑑定的生存型藥劑。當 HP 低於 35% 時，CoHero 會優先使用 `PotionOfHealing` / `ElixirOfHoneyedHealing`；若治療正在進行或沒有可用治療藥，則可使用 `PotionOfShielding`。這些行為不讀取 Hero 背包，也不觸發 Hero 專屬 Potion talents。
- CoHero 背包可持有 `Ankh`。CoHero 死亡時優先消耗祝福 Ankh：回滿 HP 並獲得 15 回合 `Invulnerability`；未祝福 Ankh 則回滿 HP 並隨機傳送到本層一個合法、非秘密、無角色占用的可走格。Ankh 成功觸發時不進入 CoHero Game Over 流程。

因此玩家不是直接命令同伴，而是透過資源配置限制或擴張它可以採取的行動。

## 7. 兩個背包與物品使用語意

兩個背包不是單純增加儲物容量，而是核心玩法之一。

同一份地城資源必須分配給兩個 Game Over 點：

- 好武器給自己，玩家能更有效利用，但同伴變脆弱。
- 好裝備給同伴，可降低不可控角色死亡風險，但玩家本身會變弱。
- 遠程武器交給同伴，可以提高它的生存與輸出，但玩家失去精確控制該資源的能力。
- 投擲武器與法杖交給哪一方，會決定誰能使用這些有限的遠程資源。

核心張力：

> 共同資源，但不是共同控制。

### 7.1 消耗品

CoHero 仍不使用卷軸，也不泛化成會自行決策各種 consumable；目前只支援少數明確定義的生存型消耗品與 Ankh。

- CoHero 背包允許存放 Potion。這是刻意的：若 UI 只允許真正的 `PotionOfHealing` 放入，會藉由「能不能選」洩漏未鑑定藥水的真實種類。
- 未鑑定 Potion 即使實際類型是治療藥也不會被 CoHero 自動使用。
- HP 低於 35% 時，CoHero 先嘗試消耗自己背包中的一瓶已鑑定 `PotionOfHealing` 或 `ElixirOfHoneyedHealing`。
- 治療期間不會連續喝下一瓶治療藥；若仍低於 35%，可以把已鑑定 `PotionOfShielding` 當作次順位生存資源。
- 已有有效 `Barrier` 時不會再喝第二瓶護盾藥，避免覆蓋仍有價值的護盾。
- `Pharmacophobia` 只讓玩家 Hero 對治療藥過敏；SPD 原版明確規定其他角色仍正常受治療，因此 CoHero 仍可正常使用治療藥。
- 其他 Potion 只作為背包資源，可交還 Hero；CoHero 不會自行飲用。
- Scroll 仍不接受、不使用。

### 7.2 力量是共享資源

CoHero 不保存獨立 STR。

> CoHero 的基礎有效 STR 取目前玩家 Hero 的有效 STR；CoHero 自己的 Ring of Might 與 Warrior 固有 trait 再額外疊加。

理由：地城中的力量藥劑數量是共同且有限的永久資源，不應讓兩名英雄產生兩條互相競爭或不同步的力量成長曲線。

因此：

- Hero STR 增加時，CoHero 的基礎 STR 同時增加；CoHero 自己裝備的 Ring of Might 與 Warrior 固有 +1 STR 另外疊加。
- 力量藥劑只由玩家 Hero 使用；增加的共同 STR 仍同時影響 CoHero。
- 武器與護甲的 STRReq 使用這個共享 STR 計算；依 GhostHero 規則，力量需求超過 CoHero STR 的裝備直接不能裝備，因此正常流程不應產生「已裝備但力量不足」狀態。
- 不應維護兩份 STR 再做同步；共享值應只有一個權威來源。

### 7.3 等級與 EXP 共享

與 STR 類似，CoHero 不保存獨立的 lvl / exp。

> 玩家 Hero 的 `lvl` / `exp` 是隊伍唯一的等級與經驗權威；CoHero 的 `level()` 直接讀取 `Dungeon.hero.lvl`。

因此：

- 不論 Hero 或 CoHero 擊殺敵人，都沿用 SPD 原版流程把 EXP 加到 `Dungeon.hero`。
- 不需要為擊殺者歸屬 patch `Mob.java`。
- 經驗藥水只由玩家 Hero 使用，並增加共同 EXP。
- CoHero 的 HP、命中、閃避等 level-based 數值仍以共同 level 計算，但各自保有 HP / HT 與裝備狀態。
- CoHero 不維護第二套 EXP 曲線，也不存在尾刀搶 EXP 的問題。

### 7.4 回血

CoHero 的基礎回血比照 Hero，但目前不處理飢餓值。

最低規格：

- 基礎自然回血採 Hero 的基礎速率：每 10 回合恢復 1 HP。
- 不直接把原版 `Regeneration` Buff 強掛到 CoHero，因為原版包含 `Hero` cast、飢餓、神器與其他 Hero-specific 行為。
- 在 CoHero 層實作只包含必要基礎語意的 regeneration。
- 自然回血之外，CoHero 可依 7.1 的規則自動使用已鑑定治療／護盾藥；這些消耗品行為與基礎自然回血是彼此獨立的系統。

### 7.5 物品支援邊界

目前仍不因為「第二英雄」而全面支援所有 Hero 系統。

已確定：

- 武器、防具、戒指、法杖屬於 CoHero 裝備／戰鬥系統。
- CoHero 的裝備安全規則比照乾燥玫瑰的 GhostHero：只有已確認沒有詛咒的裝備才能穿戴；武器與防具若力量需求超過 CoHero STR 也不能裝備。
- 武器／防具的強化等級若未知，力量檢查使用 +0 的 `STRReq(0)`，避免藉由能否裝備反推出隱藏強化等級。
- Potion 可放入 CoHero 背包，但只有已鑑定的 `PotionOfHealing`、`ElixirOfHoneyedHealing`、`PotionOfShielding` 具有自動使用語意；`Ankh` 具有死亡時自動復活語意。Scroll 仍不接受、不使用。
- Artifact 與 Trinket 目前仍不支援。
- `BrokenSeal.WarriorShield` 是 stock SPD 的 Hero-only 被動（會直接 cast `Hero` 並讀取 Hero Talent / Combo 狀態），因此 CoHero 不啟用 Broken Seal 護盾；新建 Warrior CoHero 的起始 Cloth Armor 也不附帶 Broken Seal。
- 未知物品或效果不得猜測相容；沒有明確 CoHero semantics 時就不允許 AI 使用。
- Wand 目前需要額外 integration seam，因為 SPD 的使用入口與不少個別 Wand 效果仍依賴 `Hero` / `curUser` / `Dungeon.hero`；這是上游 API 的 owner 假設，不代表 AI 設計上應以法杖類別硬編行為。

### 7.6 法杖為何目前需要額外 adapter

近戰武器的大部分戰鬥 API 原生以 `Char` 為 owner / attacker，因此 CoHero 可以直接沿用；一般投擲武器也能透過很小的 seam 重用既有命中、耐久與落地流程。

Wand 不同。SPD 的 Wand 使用流程歷史上以玩家 Hero 為中心：

- 使用入口與 targeting 流程以 `Hero` / 靜態 `curUser` 為核心。
- 部分 `onZap()` 會讀取 `Dungeon.hero`、Hero Talent、Hero buff 或 Hero belongings。
- 不同 Wand 的效果語意差異很大；有些是直接傷害，有些是 AOE、位移、地形、召喚、治療、控制或持續效果，不能只用「平均傷害最高」安全概括。

因此採用 fail-closed capability adapter：Wand 保留原版本身的效果與動畫，CoHero adapter 只負責判斷 targeting、安全性與「直接傷害／控制／逃生／支援」語意。未知 Wand 類型不猜測、不自動使用。

## 8. Talent 與職業能力

完整 Talent 支援目前**不列入最小版本的必要條件**。

原因有兩類：

### 8.1 工程問題

SPD 有不少 Hero / Talent 相關程式直接依賴 `Dungeon.hero`。即使保存第二個 `Hero` instance，也不代表整個 Talent 系統天然支援 multi-Hero。

### 8.2 AI 問題

更大的問題是 Talent 會增加新的決策種類。只要能力需要 AI 回答：

- 現在要不要用？
- 對誰用？
- 要不要保留資源？
- 哪一種技能現在最值得？

就開始逼近完整 Hero AI。

因此目前原則是：

- 不為了「第二英雄」這個名稱而強行追求完整 Talent tree。
- 若未來加入能力，優先考慮不增加複雜 action choice 的被動或 capability。
- 不把職業名稱當作跨 fork 行為判定的主要依據。

Talent 是否能以有限、安全的方式加入，保留為後續研究問題。

## 9. 跨 fork / 注射相容性

未來若要像 SMM 一樣注射到其他 SPD fork，核心 Companion AI 不應依賴固定的官方職業清單。

應優先使用 capability-based 行為：

- 是否有可用近戰武器。
- 是否有可用投擲武器。
- 是否有可用法杖。
- 當前距離下哪些攻擊合法。
- 目標是否具有高閃避、魔法免疫等可明確判斷的性質。
- 各合法候選在當前距離下的傷害。
- 是否具有明確、可安全判斷的被動能力。

戰鬥與探索的**行動選擇**不要依職業名稱硬編，例如不應寫成「Mage 一律優先 wand」或「Huntress 一律只用 Spirit Bow」；仍應以目前實際 capability、距離、命中、安全性與傷害決定。

官方六職業的**固有 trait**則是明確例外：`CoHeroClassTraits` 可以依 stock `HeroClass` 套用已定義的被動特色與專武 seam。這些 trait 不取代 capability-based AI，也不占用實際裝備槽。遇到第三方／未知職業時不猜測其能力，改用 Generalist（HT ×1.05）與通用起始武器。

未知能力若不是核心所需，可明確忽略；核心 ABI 缺失則應直接判定不支援。

跨 fork 的 progression / consumable contract 也遵守相同原則：只依賴已確認存在且語意清楚的能力，不把未知 fork 的 Hero-specific API 猜成官方 SPD 等價物。

## 10. 最重要的玩法假說

這個專案真正需要驗證的不是「能不能做出第二 Hero」，而是：

> 把一名會自主探索、會消耗資源、死亡即敗的同伴放進 SPD，是否會讓原版每一個探索與資源決策產生新的張力？

理想情況下，玩家會自然遇到這些問題：

- 我還想搜這個房間，但同伴已經走遠。
- 我要繼續搜刮，還是去追它？
- 好裝備應該給我還是給同伴？
- 我該把遠程武器交給它，還是自己保留？
- 出口已找到，同伴正在前往，我是否還有時間處理其他事情？

如果這些決策自然發生，核心玩法就成立。

## 11. 三個主要失敗風險

### 11.1 AI 太蠢

如果同伴經常做出玩家無法理解的愚蠢選擇，死亡會被認為是 AI 害玩家輸，而不是玩家承擔風險失敗。

目標不是讓 AI 非常聰明，而是讓它**簡單、合理、可預期**。

### 11.2 同伴太強

第二個角色本質上增加額外 HP、輸出與裝備槽。如果它的戰鬥收益高於護送成本，遊戲可能反而比原版更簡單。

### 11.3 出現固定最佳策略

如果最後所有 run 都變成同一套解法，例如永遠把最好的防具交給同伴、一路緊跟著它，新的決策很快就會消失。

這三點應作為 prototype 的主要驗證標準。

## 12. 最小可玩原型

目前可玩原型仍不需要完整複製第二套 Hero 系統。

目前 baseline 包含：

- 一名以 `GhostHero` / `DirectableAlly` 為基礎的 companion actor。
- 自主探索未知區域。
- 發現出口本身不停止探索；只有 Hero 明確站上正常出口等待時，CoHero 才停止一般探索並前往出口集合。
- 玩家 Hero 作為唯一樓層 transition 觸發者。
- 自己的背包 / 裝備資源。
- 共享 Hero STR、lvl / exp，但保有獨立 HP / HT。
- CoHero 擊殺沿用原版流程增加共同 EXP。
- 基礎自然回血，不處理 Hunger。
- CoHero 不使用卷軸；可自動使用少數已鑑定生存藥劑，並可由自己背包中的 Ankh 在死亡時復活。
- 完全由背包與裝備驅動的基本戰鬥行為。
- 近戰武器可及時只使用近戰武器。
- 高閃避目標優先法杖。
- 魔法免疫目標禁用法杖。
- 當前距離下使用最高傷害的合法攻擊選項。
- 投擲武器以 setID 追蹤並在無可見威脅時回收；不跨樓層追蹤。
- 避免主動吵醒可視範圍內的睡眠怪物。
- 讀取原版 targeted-cell 預告並優先避開即將爆發的危險格，也會避開已存在的有害氣體、火焰、電流與冰凍類 blob。
- 官方六職業具有固有 trait；Mage Staff、Spirit Bow 等已明確支援的專武使用原版物件與 CoHero-safe seam。
- CoHero 背包提供即時基本數值顯示，locator 提供 Hero / CoHero 雙向畫面外監控。
- 同伴死亡即 Game Over。
- 同伴抵達出口旁前禁止玩家下樓。
- 無停止移動或攻擊政策開關。

如果只靠這些就已經大幅改變 SPD 的決策體驗，才有理由繼續增加更複雜的 Hero 能力。

## 13. 尚未決定

以下內容目前仍是開放問題，不應視為已確定規格：

- 一般地面物品是否由同伴自主撿取；目前只明確要求回收自己投出的投擲武器。
- 睡眠怪物的具體安全距離，以及完全無法繞行時是否允許喚醒。
- `WandOfLivingEarth` 的 Earth Guardian / RockArmor ownership 是否值得泛化成非 Hero caster；在此之前維持不支援。
- 再生法杖是否要從純逃生擴充到主動伏擊，以及該如何定義不浪費 charge 的觸發條件。
- 注魂法杖是否要進一步支援治療其他友軍，而不只玩家 Hero。
- 是否保留任何 Talent、Subclass 或 Hero Armor Ability。
- 同伴裝備切換由玩家直接管理到什麼程度。
- Boss 戰中的特殊 AI 行為。
- 兩名英雄的具體故事關係與劇情。
- 注射到不同 SPD fork 時的正式 compatibility contract。

---

設計原則：先以最小規則驗證核心張力。若一個新系統不是為了解決已觀察到的問題，就不要提前加入。