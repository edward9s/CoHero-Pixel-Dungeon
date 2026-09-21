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

## 1.1 版本

CoHero 自己的版本與宿主 SPD / SMM 版本分開管理。

- 唯一權威來源是 `CoHeroVersion.VERSION`；目前為 `0.1.0`，版本值本身不含 `v` / `c` prefix。
- `CoHero.version()` 只委派給 `CoHeroVersion.version()`，避免出現第二份版本常數。
- TitleScene 右下角與遊戲內 MenuPane 都顯示短格式：`SPD v<host> | CoH v<cohero>`。例如 `SPD v4.0.0 | CoH v0.1.0`；`CoH` 作為 CoHero 的短標記。
- UI 使用宿主執行時的 `Game.version`，因此 source patch build 與未來 binary APK injection 都不需要把 SPD 版本複製進 CoHero。
- App package 固定為原版 package 加 `.cohero`：`com.shatteredpixel.shatteredpixeldungeon.cohero`；一般 CoHero build 的顯示名稱為 `CoShattered Pixel Dungeon`。
- TitleScene 保留原版 Shattered Pixel Dungeon banner 資產，另外在 banner 上方加入 `CoHero` 文字標記，不複製或重畫上游 logo。
- CoHero 存檔會另外寫入 `cohero_version`，目前只保留作未來 save migration 的版本識別，不改變既有載入規則。
- build workflow 從 `CoHeroVersion.java` 抽取版本，artifact 名稱同時標示 CoHero、SPD；SMM build 另外標示 SMM 版本。
- 開啟 CoHero 背包時會非同步查詢本 repo 的 GitHub Releases；一小時內不重複查詢。若存在高於目前 `CoHeroVersion.VERSION` 的 CoHero release，背包 title 以 postfix ` (new <version>)` 顯示取得的新版本號；網路或 GitHub 查詢失敗不改變遊戲流程，之後再次開啟背包可重試。

## 2. 核心玩法

玩家直接控制一名正常的 Hero；另一名英雄是 AI 同伴。

已確定的基本規則：

- 玩家不能直接控制同伴的移動格、攻擊目標或每一步行動。
- 同伴有自己的 AI 路徑。
- 同伴有自己的背包，控制介面與玩家 Hero 分開。
- 地城中的可用資源需要在兩名角色之間分配。
- 玩家 Hero 死亡：Game Over。
- 同伴 Hero 死亡：Game Over。
- 真正的樓層 transition 永遠由玩家 Hero 觸發；CoHero 不直接切換樓層，也不需要先抵達出口附近。
- Hero 觸發普通樓層 transition 時，系統先保存 CoHero 當下的 HP、裝備、背包、buff 與 AI 狀態，再直接換層；下一層由保存狀態在 Hero 附近重新生成 CoHero。
- 同伴沒有「停止行走」開關；持續前進本身就是壓力來源。
- Hero 與 CoHero 不共享遊戲規則層的 FOV：`Dungeon.level.heroFOV` 始終只代表玩家 Hero 視野。CoHero 另外維持自己的 `fieldOfView`，只在畫面呈現層合併，用來顯示 CoHero 周圍地形、角色與動畫，不讓 CoHero 遠端戰鬥影響 Hero 的行動、技能或其他依賴 `heroFOV` 的原版規則。放大鏡／右鍵檢查是唯一的 UI 例外：若某個 actor 位於 CoHero 當前 FOV，即使不在 Hero FOV，也允許檢視該 actor 的 info；地板、物品、陷阱等仍只依 Hero FOV。CoHero 的基礎 `viewDistance` 每次進層與行動前直接同步 `Dungeon.level.viewDistance`，所以「沒入黑暗」、DARK feeling、特殊 Boss 關卡或其他上游樓層視距調整都會同樣影響 CoHero；若 CoHero 具有原版 `Light` buff，則依原版規則至少提升至 6 格。
- 載入存檔的 `StartScene` 存檔槽預覽同時顯示 Hero 與 CoHero 的全身 sprite：CoHero 畫在 Hero 後層，與 Hero 使用相同 Y，X 向右偏半個 12px 角色寬（6px），因此只露出右半；兩者各自使用存檔中的職業與護甲 tier。舊存檔若沒有 CoHero armor preview metadata，顯示 tier 0，但不影響實際載入狀態。

設計重點不是「護送一個完全無能的 NPC」，而是：

> 玩家只直接控制其中一名英雄，卻必須為兩名英雄共同負責。

## 3. 同伴探索與路徑

存檔重新載入同一樓層時，CoHero 恢復保存時的原始格子，不重新生成到 Hero 身旁；這是狀態恢復，不視為重新踏入該格，因此不重播 `Level.occupyCell()`，避免讀檔時再次觸發高草、陷阱、植物等 entry effect。只有真正進入新樓層或從被排除樓層重新會合時，才在 Hero 鄰近可用格重新生成並執行正常 occupancy。GameScene 的 CoHero restore hook 必須位於 terrain / fog tilemap 建立完成之後、Hero 開始 actor scheduling 之前。

同伴不應從樓層開始就知道出口位置，否則它會變成出口指南針。

目前共識：

1. 出口尚未發現時，同伴自主探索未知區域，但探索範圍限制在「以 Hero 為中心、依實際可走 path distance 計算最近約 30% 的可通行／可探索區域」。這是範圍上限，不是每回合只從 30% 候選抽樣；普通探索的目的地、可達性判定與實際尋路每一步都必須留在這個範圍內，不能為了走最短路徑暫時繞出 30%。Hero 移動時範圍會跟著重新置中；若重新置中後 CoHero 當下位於範圍外，只允許保留當前格作為尋路起點，下一步必須回到允許區域。出口只要透過任何合法地圖情報成為已知，也就是該格已為 `visited` 或 `mapped`，此 30% 限制立即解除；不要求 Hero 或 CoHero 必須以正常視野親自看見，因此 Magic Mapping 等合法映射資訊也會解除限制。戰鬥、逃跑、低血量 rally 與獨立的物品回收／拾取行為不受此普通探索 path mask 限制。
2. 探索路徑可以帶有一定隨機性，但只能存在於合理選擇之間。
3. **發現出口本身不代表探索結束。** 它只解除 30% 前期探索範圍限制；只要仍有一般未探索 frontier，CoHero 可以繼續探索。出口不是安全區，也不是 CoHero 必須前往等待的集合點。
4. 「尚有 frontier」只計算 CoHero 目前實際可在當前探索範圍內經由 passable path 抵達的未知格；不能把「只有先離開 30% 才走得到」的格子算成可達 frontier。秘密區、隔離格或其他目前無路可達的未知格不會讓 AI 卡在反覆尋路。若已無可達 frontier，CoHero 會在「以 Hero 為中心、依實際可走 path distance 計算最近約 25% 的已探索可通行區域」自主遊走；roaming 的候選與實際路徑同樣限制在該 25% 區域內。出口未發現時，25% roaming 區還必須同時位於當前 30% 探索區內。這個 25% 是「無 frontier 後的遊走區」，與出口發現前的 30% 探索範圍是不同規則。
5. Hero 到達普通樓層出口時，不需要等待 CoHero、也不檢查 CoHero 是否位於出口附近；Hero 可直接觸發原生 transition。
6. 普通換層前會先保存 CoHero 當下狀態；進入下一層後，CoHero 以該狀態在 Hero 附近的合法格重新生成，因此不需要把 CoHero 實際走到舊樓層出口。
7. 出口發現後 CoHero 不會因為「準備下樓」而停止戰鬥、撿取高優先物品或一般探索；是否離層完全由玩家 Hero 何時觸發 transition 決定。

AI 不需要模擬真人玩家的完整戰術推理。毒氣等危險可優先沿用 SPD 現有 mob / ally 的避險與 pathfinding 行為；陷阱也不值得另外建立複雜推理系統。

### 視野與火把

- CoHero 背包支援原版 `Torch`；在 `Dungeon.level.viewDistance < Light.DISTANCE` 的低視距樓層且目前沒有 `Light` buff 時，CoHero 會自動消耗一支火把，使用原版 `Light.DURATION` 與 `Light.DISTANCE` 規則，並花費原版 `Torch.TIME_TO_LIGHT` 的行動時間。
- CoHero 被麻痺時不會點火。點火後立即重算 CoHero 自己的 FOV 與 CoHero 側 fog，不改動 `Dungeon.level.heroFOV`。
- 在低視距樓層，CoHero 會把已知且可安全取得的火把列為偏好拾取物；正常視距樓層不會為了囤積火把而偏離探索。

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

### Boss 封鎖生命週期

SPD 在正式 Boss 戰開始時會由 `Level.seal()` 對 Hero 掛上 `LockedFloor`（中文「背水一戰」），Boss 戰結束時則由 `Level.unseal()` 解除。CoHero 直接把這個 buff 的 attach / detach 當作共通 Boss 戰生命週期訊號：

- `LockedFloor.attachTo(Dungeon.hero)` 成功時，如果 CoHero 選擇同行，立即把 CoHero 搬到 Hero 身邊最近的合法空格，再讓各 Boss 關卡繼續鎖門、封入口或改地形。`LockedFloor` 會把「本次鎖層已完成開始搬運」寫進存檔；讀取 Boss 戰中的存檔雖然原版會重新呼叫 `attachTo()`，但不會被誤判成新的 Boss 開始而再次搬運。
- `LockedFloor.detach()` 時再次把 CoHero 搬到 Hero 身邊，確保 Boss 戰結束時兩人重新會合。
- 搬運不依賴特定 Boss class、arena `Rect` 或門的位置，因此 Goo、DM-300、矮人王、Yog 等使用標準 `seal()/unseal()` 的 Boss 都共用同一套規則。
- CoHero 若被玩家明確留在該 Boss 樓層外，當前樓層不存在 CoHero actor，因此 hook 自然 no-op。
- 這仍不把 CoHero 加回 stock `Mob.holdAllies()/restoreAllies()`；CoHero 的跨樓層 companion state 與「留在外面」語意保持獨立。

### Boss 決策診斷

Boss 樓層鎖定期間，CoHero 會以 `GLog` 輸出簡短決策診斷，協助實測 AI 是否卡在錯誤狀態。只有決策 key 改變時才輸出，避免每回合洗版。

目前可看到的資訊包括：

- 沒有可見威脅時的敵人總數、CoHero FOV 內數量、隱形敵人與睡眠敵人數量。
- 當前選擇近戰、投擲武器、Spirit Bow、法杖、接近目標、戰術走位或撤退。
- 進入 survival / retreat 時的目前攻擊者數、TTD 與 TTK。
- hazard avoidance、麻痺、點火等會搶先於一般戰鬥決策的狀態。

這些訊息是除錯輸出，不是新的玩家控制介面，也不改變 AI 本身的決策權。

### 天狗 Boss 樓層的階段重建

天狗是例外，因為同一個 `LockedFloor` 生命週期內會多次 destructive map rewrite，而原版 `clearEntities()` 會直接銷毀不在保留區的 mob。因此除了共通的 Boss 開始／結束 hook 外，仍保留最少量的 phase seam：

- 第一階段結束、`clearEntities(tenguCell)` 前，把 CoHero 確保在 `tenguCell`。
- Hero 走到第二階段入口、`clearEntities(pauseSafeArea)` 前，把 CoHero 搬到 `pauseSafeArea`。
- 第二階段打敗天狗時，原版會先 `unseal()`，之後才強制移動 Hero、`setMapEnd()` 並重新放置 ally；因此 `LockedFloor.detach()` 的會合時機對最終地圖太早。等 stock ally preservation 把 ally 放回結束地圖後，再額外把 CoHero 搬到 Hero 身邊一次。
- 第一階段入場不再有天狗專屬搬運；它由 `LockedFloor.attachTo()` 的共通 Boss 開始 hook 處理。

### 特殊樓層的同行選擇

一般樓層仍維持 CoHero 與 Hero 一起跨層；但進入高風險、可獨立完成的特殊樓層時，玩家可以決定是否讓 CoHero 同行：

- 從主線以 `REGULAR_EXIT` 進入下一個 Boss depth（5 / 10 / 15 / 20 / 25）時顯示一次選擇。
- 從主線以 `BRANCH_EXIT` 進入 quest branch floor 時顯示一次選擇；`BRANCH_ENTRANCE` 是從支線返回主線，不再次詢問。
- 「一起進入」不要求 CoHero 位於 transition 附近；選擇後會先保存 CoHero 當下狀態，再於目的樓層在 Hero 附近重新生成。玩家也仍可選擇「留在外面」。
- 選擇留在外面時，CoHero 的 HP、裝備、背包、buff 與 AI 狀態先保存，但該目的 `depth + branch` 不生成 CoHero。這個排除狀態會寫進遊戲存檔，因此在特殊樓層內存檔／重開也不會把 CoHero 重新生出來。
- CoHero 明確排除於 SPD 原版 `Mob.holdAllies()/restoreAllies()` 的跨層搬運；其跨層生命週期只由 CoHero 自己的 companion state 管理，避免「留在外面」仍被 stock ally transport 偷帶進去。
- 當 Hero 離開被排除的特殊樓層後，排除狀態清除，CoHero 在下一個正常樓層由保存狀態重新生成在 Hero 附近。離開特殊樓層時不要求一個本來就被刻意留在外面的 CoHero 站在出口旁。
- Ankh、死亡與 Game Over 語意不因留隊選擇改變；CoHero 在被留在外面的期間視為暫停，不會在看不見的舊樓層自行行動或受傷。

### 睡眠中的敵人

同伴不應主動吵醒正在睡覺的怪物。

- 睡眠怪物若在同伴的可視範圍內，不應被選為主動攻擊目標。
- 目前實作把 CoHero 可視範圍內的睡眠敵人所在格與距離 1 格的相鄰位置視為不安全，尋路不主動踏入；能繞行就繞行。
- 避讓只影響 CoHero 的路徑選擇，不改寫敵人的睡眠機制；若 CoHero 實際進入睡眠敵人的偵測範圍，仍依 SPD Sleeping detection 的距離、stealth、隱形與飛行規則決定是否醒來。Hero 與 CoHero 同時位於睡眠敵人 FOV 時，應以其中實際 detection chance 最高、也就是最容易被發現的 hostile 決定喚醒擲骰；不能讓遠處 Hero 的低偵測率蓋掉貼近的 CoHero。一般未隱形、未飛行的 CoHero 走到相鄰格時，應正常把敵人吵醒。
- 這是避讓偏好，不需要為此建立完整戰術規劃；完全無路可繞時的處理仍可依 prototype 行為再調整。

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
- CoHero 同時把原版持續性環境危險納入同一套移動遮罩：火焰、毒氣、酸蝕氣體、麻痺／混亂／惡臭氣體、電流、冰凍／暴風雪、Inferno、Vault flame traps、特殊房間的 `MagicalFireRoom.EternalFire`，以及 `VaultBossElemental.FireWall`。`EternalFire` 會在每次 evolve 時點燃火牆本格與四方向相鄰角色，因此 CoHero 會把火牆旁一格也視為危險；Boss FireWall 則另外讀取目前兩列燃燒區與下一列推進方向作為安全緩衝。若 CoHero 對實際效果免疫，該危險不納入遮罩。
- 若目前正站在這些環境危險中，會像預告格一樣優先尋找相鄰安全格；一般尋路也不主動踏入已存在的危險 blob。
- `DelayedRockFall` 在存檔載入重建特效時，會按 buff 剩餘 `cooldown()` 重新登記危險格，因此地動法師／DM-300 已預告但尚未落下的岩石不會因讀檔而被 CoHero 忘記。
- 因此 Yog-Dzewa 光線、Gnoll Geomancer / DM-300 落石、Ripper Demon 跳躍、Vault Laser 等使用原版 targeted-cell 警示的攻擊可共用同一套避讓邏輯。Eye 的蓄力光線不是走這個 API，目前不在此泛用層內。
- 若 CoHero 被定身、麻痺，或所有相鄰合法格本身都危險／不可通行，AI 不會假裝能躲開，會繼續執行其他可行生存或戰鬥行為。

### CoHero 職業固有能力

職業特色是 CoHero 額外的固有 trait，不是假戒指，也不占用兩個實際 ring slot；CoHero 真正裝備的戒指仍照原版生效，並與固有 trait 疊加。

- **Warrior**：等價 `RingOfMight +0` 與 `RingOfTenacity +0`：+1 STR、HT ×1.035，並依缺失 HP 比例取得原版 Tenacity 的漸進減傷；真正的 Ring of Might / Ring of Tenacity 可再依原版公式疊加。
- **Mage**：等價 `RingOfEnergy +0` 的 wand charge 與 `RingOfElements +0`：法杖自然充能 ×1.175，並對原版 `RingOfElements.RESISTS` 涵蓋的元素／魔法效果套用 ×0.825 effectiveness；真正的 Ring of Energy / Ring of Elements 可再依原版公式疊加。
  - 起始 `MagesStaff(WandOfMagicMissile)` 仍是原版 Mage's Staff；CoHero AI 直接使用 Staff 內嵌的原版 wand 與同一個 charge pool，不建立複製 wand。Staff 近戰與 wand 遠程能力都可使用。
- **Rogue**：移動速度 ×1.15，等價 `RingOfHaste +0`；真正的 Ring of Haste 可再疊加。
- **Huntress**：投擲武器傷害等級 +1、耐久 ×1.2，等價 `RingOfSharpshooting +0`；真正的 Sharpshooting 可再疊加。另保留原版 Huntress 的草地固有語意：踩高草只壓成 `FURROWED_GRASS`，踩已犁過的草不再壓平成普通草；不繼承 Hero 專屬草地 talents。
  - 起始 `SpiritBow` 仍是原版專武。CoHero 透過原版 `SpiritArrow` 射擊；箭為無限彈藥，不進普通投擲物耐久、掉落或回收流程。傷害使用 CoHero 自己的 STR、實際 Sharpshooting 戒指與 Huntress 固有 Sharpshooting +0，並排除 Hero-only talents。
- **Duelist**：等價 `RingOfFuror +0` 與 `RingOfTenacity +0`：裝備近戰武器時攻擊速度 ×1.09051，並依缺失 HP 比例取得原版 Tenacity 的漸進減傷；真正的 Furor / Tenacity 可再依原版公式疊加。
- **Cleric**：Cleric CoHero 自身永久視為受到原版 `Bless` 的戰鬥加護；玩家 Hero 位於 Cleric 3 格內（`Level.distance()` ≤ 3）時也分享同一效果。實作不建立永久 `Bless` buff，而是在原版 `Char.hit()` 的最終 accuracy / evasion 擲骰處，把「原版 Bless buff 或 Cleric aura」視為同一個 Bless，因此各自只套一次 ×1.25，不會與正常 Bless 疊成第二層。Cleric 與 Hero 不要求直線視野。視覺上，每個受影響格子各自畫一個稍明顯的灰色半透明方框；共用邊只畫一次，避免內部格線因 alpha 疊加而變深。此範圍與 30%／25% 探索／遊走規則完全獨立。
- **其他／第三方 HeroClass**：Generalist，HT ×1.05；未知職業使用通用短劍作為安全起始武器，不因缺少 stock case 直接失敗。

### Hero / CoHero 畫面外監控

CoHero 自主探索不應迫使玩家反覆拖動畫面找人，因此 GameScene 提供雙向畫面外 locator：

- Hero 與 CoHero 都位於目前主鏡頭 viewport 內時 locator 隱藏。
- 若只有一人離開畫面，locator 自動切換成離屏者的頭像、HP 與 buff icons，並沿正確方向指向該角色。
- 點擊 locator 只將主鏡頭平滑移向目前代表的角色；鏡頭移到 CoHero 後，如果 Hero 因此離屏，locator 立即切成 Hero，再點即可切回。控制權始終仍在 Hero。
- 若使用者手動把鏡頭移到兩人都不在畫面，locator 顯示距離目前鏡頭中心較近的角色。
- locator 以 `ME` / `CO` 明確標示目前代表的是玩家 Hero 或 CoHero，並永久顯示該角色的即時 HP bar；血條下方最多顯示 6 個小型 buff icons。
- CoHero 處於低血量 rally 狀態且 locator 正代表 CoHero 時顯示固定警示符號，不使用持續閃爍。
- locator 的活動邊界排除 Status/Menu/Boss/Toolbar/Inventory 與 tag 控制區，不覆蓋主要操作按鈕。
- CoHero 背包使用 GameScene 標準 Tag stack 提供常駐入口：CoHero 存在且存活時顯示背包圖示加 CoHero 頭像徽章，與 Attack／Loot／Action／Resume indicators 由 `layoutTags()` 統一排列，不占用 Toolbar 空間也不與其他 Tag 重疊；點擊直接開啟 CoHero 背包。locator 目前代表哪一名角色，長按就開啟該角色的背包：CoHero 開啟 CoHero 背包，Hero 開啟原生 Hero 背包。locator 的左右邊界可直接貼齊可用畫面；不再永久排除整條 Tag 欄。locator 每次定位後會讀取目前實際排好的 Tag stack 矩形，只有真正與 Attack／Loot／Action／CoHero Bag／Resume 的可見 Tag 範圍重疊時，才向畫面內側避讓 1px。
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

戰鬥前先做生存風險判斷，優先級高於任何伏擊／狹口站位：

- 每回合先估算目前所有可見、清醒敵人的總 incoming DPT。已能直接攻擊 CoHero 的敵人權重最高；下一步即可進入合法攻擊位置者也納入風險。命中率用攻防值近似，傷害估算使用獨立 RNG stack 取樣，不消耗正式戰鬥 RNG。
- TTD（time to death）以目前 `HP + shield` 為核心；正在進行的 Healing 與「下一瓶」已鑑定生存藥只提供保守的近程緩衝，不能把整個背包藥量當作額外血條。Ankh 完全不計入可揮霍戰力。
- CoHero 被推落 chasm 仍視為致命事件：沒有 Ankh 時照常死亡並 Game Over；有 Ankh 時才由 CoHero 自己的復活流程救回。普通 Ankh 維持傳送復活；blessed Ankh 若死亡原因是 chasm，也必須先移到本層合法、非 pit 的安全格，再保留其 15 回合無敵。Ankh 不因此被納入 AI 可冒險的有效生命值。
- TTK（time to kill）依 CoHero 實際當前攻擊規則估算；已建立正確近戰距離時仍以近戰為主，否則比較可用投擲武器、Spirit Bow 與法杖的預期輸出。
- 臨戰優先序分成「安全／必要狀態處理 → anti-ranged 貼身 → 直接遠程輸出 → 非緊急戰鬥消耗品／buff → 特殊近戰走位 → 普通近戰／接近」。只要目前沒有建立應有的近戰距離，而且存在合法射線，投擲武器、Spirit Bow 或法杖會被視為正常攻擊手段，而不是等所有走位與 setup 都失敗後才使用。若目前首要威脅沒有合法遠程攻擊線，才會在其他可見威脅中選最近的合法遠程目標；但首要威脅已進入正確近戰距離時不會轉頭射遠處敵人。
- 三名以上敵人目前同時能攻擊 CoHero 時直接視為 overwhelmed，優先撤退；即使未滿三隻，只要預估一輪傷害接近致死，或 TTD 明顯不優於 TTK，也進入撤退。Boss 是明確例外：Boss 的整體 HP 並不代表 CoHero 必須單獨完成的擊殺工作量，因此不以「CoHero 個人 TTD ≤ 打完整個 Boss 所需 TTK」作為撤退理由；立即致命與被多名敵人壓制等風險仍照常生效。
- 無敵敵人不納入可攻擊目標：真正的戰鬥無敵仍沿用 SPD 的 `mob.isInvulnerable(CoHeroAlly.class)` 語意；但 `Challenge.SpectatorFreeze` 明確排除，因為它同時用於 Duelist Challenge 的旁觀者凍結與存檔載入期間的暫時 freeze，不代表應觸發逃跑。被 `SpectatorFreeze` 的角色直接不算當前臨戰威脅。無敵不代表退出整場戰鬥：只要任一真正無敵敵人目前能從其所在格攻擊 CoHero，脫離該敵人的有效攻擊範圍會取得臨戰優先權；移動選格先降低無敵敵人的可攻擊者數量與 incoming DPT，再避免把自己送進其他敵人的火力。離開無敵敵人的射程後，若仍有可傷害敵人，CoHero 立即恢復原本的近戰／投擲／Spirit Bow／法杖決策；若只剩無敵敵人且它們已打不到 CoHero，則原地保持安全距離，不主動靠近。一般移動無法改善無敵火力時，依序嘗試 Blink、Teleportation、Invisibility，最後才用立即生存資源撐住。
- 撤退有 hysteresis：進入撤退後，不會只拉開一格就立刻回頭。必須降到最多 1 名即時攻擊者、HP 至少 45%，且 TTD 對 TTK 取得明顯安全餘裕，才恢復攻擊。
- 逃跑路徑不再只最大化「離最近敵人的距離」，而是優先降低候選格上的即時攻擊者數量與總預期 incoming DPT，再以距離作 tie-break。這能處理被多名敵人包圍時「躲開 A 卻走進 B/C 火力」的問題。
- 若完全沒有合法逃生格，才消耗緊急生存資源；此時 `PotionOfShielding` 因立即生效優先於逐回合恢復的治療藥。若被定身，不能用撤退邏輯非法移動。

近戰在真正出手前還有一層地形戰術：

- CoHero 有自己的純戰鬥 surprise 判定：若敵人尚未看見 CoHero，或 CoHero 已離開該敵人的 FOV，該次近戰採用 surprise 的零防禦語意。這不會走原版 Hero-only 的 `Mob.surprisedBy()` 統計／天賦路徑，因此不增加 Hero sneak-attack 統計。
- `GreatCrab` 看見目前攻擊者時仍照原版格擋；若 CoHero 無法 surprise，AI 不會站在旁邊反覆空砍，而會在自身可見、已知且安全的 5 格範圍內尋找斷視線的位置誘敵。螃蟹追過轉角／遮蔽物、尚未重新看見 CoHero 時才進行近戰。
- 面對 `Swarm` 或同時多個純近戰威脅時，CoHero 優先尋找只有 2–3 個可通行鄰格的狹口；兩向單格通道最佳。Swarm 仍可照原版分裂，但分裂體會被地形排在後方，避免開闊地上多隻同時貼身輸出。
- 若有遠程敵人已能從距離外攻擊 CoHero，不會為了守狹口原地等待；地形戰術讓位給實際生存／逃跑決策。
- 對目前能從非相鄰距離攻擊 CoHero 的遠程型敵人，若 CoHero 有近戰武器，會啟用 anti-ranged engagement，而且理想距離明確定義為「與敵人相鄰」，不以武器 `canAttack()` 射程代替。長鞭、長矛等延伸近戰即使在 2–3 格已可攻擊，也不會讓 CoHero 停在遠距與 Shaman / Warlock / DM100 / Scorpio 等敵人交換傷害；AI 會優先規劃通往敵人相鄰安全格的路徑，兼顧路徑長度、暴露步數與其他敵人壓力。若暫時無法直接貼身，才沿用 LOS cover／誘敵策略，而不是因延伸近戰已可命中就原地攻擊。這不硬編特定敵人類別，而是依敵人當下真正的遠距攻擊能力判斷。
- 到達掩體後，CoHero 暫時不使用自己的遠程武器破壞誘敵策略，而是等待敵人靠近；敵人重新進入視野且一個安全移動步即可建立近戰時，立即貼身。目標完全離開 CoHero 視野時不讀取其牆後新座標，只在既定 cover 最多等待 6 回合；逾時即放棄該 plan，避免永久卡住。低血撤退、立即致命風險與 hazard avoidance 都可中止此 tactic。
- Boss 不使用這套 ranged lure／LOS cover 誘敵流程。Boss 常有 scripted movement、teleport 或階段機制，若要求它先追進掩體可能讓戰鬥停滯；對 Boss 改回正常的投擲武器、Spirit Bow、法杖、近戰接敵與生存決策。

1. **沒有任何可用攻擊能力時**
   - 同伴不主動攻擊。
   - 盡量躲避敵人並繼續探索 / 前進。
   - 「沒有近戰武器」不等於沒有攻擊能力；只要背包中仍有合法投擲武器或合法法杖，CoHero 仍可進行遠程攻擊。

2. **有近戰武器時**
   - 可以進行普通近戰攻擊。
   - 近戰武器不要求鑑定；只要實際未詛咒即可裝備／使用。未知強化等級仍以 +0 的 `STRReq(0)` 判斷力量需求，避免由能否裝備直接反推出隱藏強化等級。
   - 當敵人已進入該近戰武器的合法攻擊距離時，普通情況只使用近戰武器，不改用投擲武器或法杖；但若目標正在從非相鄰距離施加遠程壓力，延伸近戰射程不代表理想交戰距離，AI 仍優先貼到相鄰格。

3. **有投擲武器時**
   - 在合法的遠程距離下，可以使用已明確支援的投擲武器攻擊。
   - 投擲武器不要求鑑定，也不要求 `cursedKnown`；只要屬於明確支援類型且實際 `cursed == false`，就可以被 AI 投擲。Spirit Bow 也只要求實際未詛咒。未鑑定本身不會降低使用優先級。
   - 第一版明確支援：`ThrowingStone`、`ThrowingKnife`、`ThrowingSpike`、`FishingSpear`、`ThrowingClub`、`ThrowingSpear`、`Kunai`、`Bolas`、`Javelin`、`Tomahawk`、`Trident`、`ThrowingHammer`。
   - `Shuriken`、`HeavyBoomerang`、`ForceCube`、`Dart/TippedDart` 等具有額外 Hero-specific 使用語意的類型先 fail closed。
   - 投出的武器以 `setID` 追蹤；沒有可見威脅時，CoHero 會優先走向並拾回自己仍留在本層地面的投擲武器。若沒有待回收的自己投擲物，CoHero 也會把已知地圖上的金錢，以及背包可容納且屬於目前明確支援類型的地面投擲武器與法杖視為高優先 loot，在一般探索前主動前往拾取。普通 loot 只從 `visited` / `mapped` 的已知格選擇，避免直接讀取未探索區 heap；路徑依實際安全可走距離選最近者，且不穿越 CoHero 已知 hazard 或會驚動睡眠敵人的格子。自己投出的武器仍高於其他 loot；同一 heap 沒有待回收投擲物時，金錢優先於一般投擲武器／法杖。金錢不進 CoHero 背包，而是直接加入共用 `Dungeon.gold`，並更新原版 `Statistics.goldCollected`、金錢徽章、拾取動畫與音效。目前未支援使用的特殊投擲武器或法杖不主動撿拾。
   - 已顯示且仍為 active 的陷阱視為 CoHero movement hazard：探索、撤退、戰術走位與前往拾取金錢／投擲武器／法杖時都不會主動踩入。未被發現的 `SECRET_TRAP` 不納入 AI 判斷，避免藉由 trap map 偷看隱藏資訊；若意外踩到，仍沿用 SPD 對非 Hero 角色的 soft-press 規則。
   - 換樓層時清除尚未回收的投擲物追蹤，不跨樓層追索。

4. **有法杖時**
   - 在合法目標與距離下，可以使用已明確支援的攻擊型法杖。
   - 法杖不要求鑑定，也不要求 `cursedKnown`；只要實際未詛咒、有足夠 charge，且屬於 CoHero adapter 已明確支援的類型，就是合法候選。原版法杖即使未鑑定也照常持有並回復 charge，因此未知鑑定狀態不會阻止 CoHero 使用。
   - 目前明確支援 `WandOfMagicMissile`、`WandOfBlastWave`、`WandOfFrost`、`WandOfDisintegration`、`WandOfLightning`、`WandOfLivingEarth`、`WandOfPrismaticLight`、`WandOfRegrowth`、`WandOfTransfusion`、`WandOfCorruption`、`WandOfCorrosion`、`WandOfFireblast`、`WandOfWarding`。
   - 靈壤法杖保留原版「命中敵人累積 RockArmor → 達門檻生成 EarthGuardian → 後續命中補充 Guardian → Guardian 離戰後把剩餘 HP 還原為 RockArmor」循環。Guardian 新增 owner ID；Hero 與 CoHero 可在同一樓層各自擁有一隻，不會互相吃掉 ownership。舊存檔中沒有 owner ID 的 Guardian 視為玩家 Hero 所有。
   - CoHero 的 `RockArmor` 會像 Hero 一樣在 `defenseProc()` 中吸收傷害。Guardian 的防禦仍以共用隊伍 level 為基礎；CoHero cast 不繼承 Hero 的 Wand Talent / PowerOfMany / Stasis 額外效果。Hero 的 Stasis / PowerOfMany / ElementalBlast 原版互動仍只作用於 Hero-owned Guardian；`EarthGuardian.setInfo(Hero, int, int)` 的原版 public API 也保留，供既有 Hero ability 與下游 fork 相容。
   - 一方的靈壤法杖若碰到另一方的 Guardian，會視為友軍而不造成傷害或補血；只有同 owner 的 Guardian 才會被該法杖補充。
   - `WandOfFrost` 不對已處於 `Frost` 的目標施放；其傷害評估會按目標目前的 `Chill` 程度折減。
   - 解離法杖會檢查整條有效射線，若會傷及友軍或主動波及睡眠敵人就不施放。
   - 雷霆法杖沿用原版 chain / `affected` 規則；AI 也用同一套連鎖範圍估算整體傷害，若連鎖會反彈到 CoHero、傷及中立角色或主動波及睡眠敵人則不施放。
   - 衝擊波法杖使用獨立 `CoHeroBlastWavePlanner`。AI 枚舉主目標周圍 3×3 的瞄準格，使用實際 `coHeroBallistica()` 取得爆心，再依原版 `throwChar()` 規則模擬 Boss 推力減半、`rooted` / `IMMOVABLE`、大型角色 `openSpace` 限制、角色阻擋、撞牆與最終落點。Hero / CoHero / 中立／友軍只要位於爆炸 3×3 作用範圍就直接淘汰，額外波及睡眠敵人也淘汰。可見且仍啟用的陷阱若會被 3×3 `pressCell()` 觸發（Tengu dart trap 依原版例外除外）則不施放。敵人被推入 chasm 視為擊殺價值；撞牆會計入平均碰撞傷害與 Paralysis 控場價值。撤退時若安全衝擊波可以降低下一回合能攻擊 CoHero 的敵人數，也可優先作為 escape utility。
   - 酸蝕法杖不固定瞄準敵人本格。AI 會枚舉目標本格與真正相鄰、可合法命中的落點，連同場上既有酸蝕氣體依原版 `Blob.evolve()` 規則模擬後續擴散；近期會波及 Hero、CoHero、中立／友軍或睡眠敵人的方案直接淘汰，再從剩餘方案中優先選友軍長期風險較低、清醒敵人覆蓋較高且能較快覆蓋主目標的落點。
   - 焰浪之杖依原版目前 charge 決定 1–3 charge、5/7/9 格與 50°/70°/90° cone。AI 會枚舉主目標附近的瞄準方向，以原版 `ConeAOE` 計算實際命中格；會直接命中友軍／中立角色或額外波及睡眠敵人的方向不施放，並重現 cone 外額外點燃可燃地形的規則。其餘方向優先降低火勢向我方與睡眠敵人擴散的風險，再比較整體預估傷害。
   - 哨衛法杖建立的 ward 會標記為 Hero-owned 或 CoHero-owned，兩邊分開計算原版 ward energy 上限，因此不會互相吃掉法杖提供的 ward 配額。額度仍是硬限制；CoHero 不會遠端刪除 ward 作弊，Hero 仍可依原版互動手動解除 CoHero 的 ward 來騰出 energy。若同一樓層的 CoHero ward energy 已滿、一般 planner 沒有合法布點，但忽略 energy 後存在明顯更有價值的新位置，CoHero 才會評估回收最低保留價值的 CoHero-owned ward。回收時必須實際走到 ward 鄰格並花一回合解除；敵人當下已能攻擊 CoHero 時不會為了整理 ward 離開戰鬥。tier 1–3 可較積極回收，tier 4–6 sentry 有大幅保留權重，只有舊位置幾乎失去戰術價值且新位置收益明顯更高時才可能撤收。換樓層不主動拆 ward：舊 ward 留在原樓層存檔，且不佔新樓層的 ward energy。
   - tier 1–3 ward 仍保留原版只有 1 HP 的脆弱性，新建 ward 也仍由原版 `GameScene.add(ward, 1f)` 延遲首次行動。放置前會把目前可見、清醒敵人在這一個時間單位內可能走到的格子全部展開，並從每個預測位置呼叫該敵人真正覆寫後的 `canAttack()`；只要任何敵人可能在 ward 首次行動前攻擊到低階 ward，就淘汰該放置／升級方案。這包含一般近戰、遠程怪、長武器與其他使用 `canAttack()` 表達的特殊射程。
   - 新 ward 另外要求首次偵測機率至少約 2/3，並用敵人移動後仍在 ward 視野／彈道中的位置數作為主要評分；因此不再只看「現在射不射得到」，而會偏好能覆蓋敵人下一步、同時不會被先手拆掉的位置。tier 3→4 首次取得實際耐久會提高升級價值，但升級既有 ward 不再無條件優先於新放置。睡眠敵人與環境危險的既有安全檢查仍保留。
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

CoHero 背包視窗頂部固定顯示目前即時基本數值：Lv、HP（有護盾時顯示為 `HP+shield/HT`）、STR、近戰 DMG 範圍、DR 範圍與實際 SPD 倍率。這些值直接由 CoHero 當前裝備、固有 trait、戒指與負重計算，不保存第二份 UI 專用數值。直向維持單欄與 5 欄背包格，縮小格子間距並在窄螢幕時微縮 slot。橫向使用左右配置：左側為數值、生成倍率、裝備與加入物品控制，右側背包固定維持 5 欄，使用較小 slot 與 1px 間距以控制高度。敵人生成倍率 slider 與「從玩家背包加入物品」按鈕縮窄後靠左對齊。「從玩家背包加入物品」採連續 selector：每成功加入一個物品後立即再次開啟玩家背包選擇器，可連續加入多個物品；沿用 SPD `WndBag.lastBag` 保留目前選中的袋子 tab，不會每次跳回第一個 tab；按取消才回到 CoHero 背包視窗。

背包不只是儲物空間，而是玩家間接塑造同伴 AI 的方式。背包視窗另外提供「敵人生成倍率」滑桿，作為 CoHero 模式的難度壓力控制：預設 1.5x，範圍 1.0x～3.0x，每 0.1x 一格；1.0x 是完整原版自然生成，較高倍率只擴張自然重生速率與自然敵人數量上限，不修改怪物本身。


- 給近戰武器 → 同伴取得近戰能力。
- 給投擲武器 → 同伴取得遠程物理攻擊選項。
- 給法杖 → 同伴取得魔法遠程攻擊選項。
- 不給任何合法攻擊能力 → 同伴不主動戰鬥，偏向避敵。
- CoHero 原則上不自行使用消耗品；目前例外是已鑑定的生存／逃生／戰鬥機動藥劑。一般低血量流程仍在 HP 低於 35% 時優先使用 `PotionOfHealing` / `ElixirOfHoneyedHealing`，治療正在進行或沒有治療藥時才用 `PotionOfShielding`；但若戰鬥風險模型已判定必須撤退、又完全沒有合法逃生格，緊急流程會反過來優先使用立即生效的 `PotionOfShielding`。有安全逃生步但正常速度仍會持續受到追擊壓力時，可使用 `PotionOfHaste` 作為短效逃跑資源；高威脅戰鬥則可使用 `PotionOfStamina` 作長效機動資源。這些行為不讀取 Hero 背包，也不觸發 Hero 專屬 Potion talents。
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

CoHero 不泛化成會自行決策各種 consumable；目前只支援少數明確定義的生存／逃生／戰鬥機動消耗品、六種 combat runestone 與 Ankh。

- CoHero 背包允許存放所有正常物品；Potion 只是其中一類。未鑑定 Potion 不會因其隱藏真實種類改變「能不能放」或 capability 框線，避免透過 UI 洩漏身份。
- 未鑑定 Potion 即使實際類型是治療藥也不會被 CoHero 自動使用。
- HP 低於 35% 時，CoHero 先嘗試消耗自己背包中的一瓶已鑑定 `PotionOfHealing` 或 `ElixirOfHoneyedHealing`。
- 治療期間不會連續喝下一瓶治療藥；若仍低於 35%，可以把已鑑定 `PotionOfShielding` 當作次順位生存資源。
- 已有有效 `Barrier` 時不會再喝第二瓶護盾藥，避免覆蓋仍有價值的護盾。
- `Pharmacophobia` 只讓玩家 Hero 對治療藥過敏；SPD 原版明確規定其他角色仍正常受治療，因此 CoHero 仍可正常使用治療藥。
- 已鑑定 `PotionOfInvisibility` 可作為緊急逃生資源，但不會因單純低於 35% HP 就立即飲用；只有戰鬥風險模型已判定 retreat、免費 escape utility 與安全走位都失敗，而且存在 3+ 當前攻擊者、立即致命風險、低血危險或 TTD ≤ 2 回合等條件時才使用。飲用後取得原版 `Invisibility.DURATION`，並在 `combatRetreating` 期間優先純移動脫離，避免下一回合主動攻擊立刻打破隱形。
- 已鑑定 `PotionOfHaste` 定位為逃跑資源。只有 CoHero 已進入 retreat、確實存在安全逃生步，而且移動一步後仍有敵人可直接攻擊、仍有能跟上的追兵，或 TTD 已縮短到約 3.5 回合內時才考慮。若當前一輪傷害已接近致命，反而不花一回合喝 Haste，直接走位／控制優先。Haste 沿用原版 `Haste.DURATION = 20` 與 3× movement speed。
- 已鑑定 `PotionOfStamina` 定位為戰鬥機動資源。只有非 retreat 狀態下遇到 2+ 可見威脅、遠程壓制／anti-ranged 接敵，或 Boss / Miniboss 戰時才會自動使用；單一普通敵人且預估很快能結束的戰鬥不浪費。Stamina 沿用原版 `Stamina.DURATION = 100` 與 1.5× movement speed。
- CoHero 不會主動把 Haste 與 Stamina 疊加：已有其中一種 buff 時，不自動消耗另一瓶。原版 `Char.speed()` 會將兩者相乘，因此這項限制避免 AI 為了 4.5× 移速浪費兩瓶藥。
- 已鑑定 `PotionOfCleansing` 可處理嚴重負面狀態：Roots、多個負面 buff、持續傷害類 debuff，或低血量下仍存在負面狀態時才會使用。若當前敵方一輪傷害已接近致命，不花一回合清狀態，仍讓 Blink／Teleport／立即護盾優先。若附近有已知 Mageroyal 可安全到達，AI 會先利用免費植物而不是消耗藥劑。
- 已鑑定 `PotionOfEarthenArmor` 定位為高威脅戰鬥的預防性防禦資源：非 retreat 狀態下遇到 2+ 威脅或 Boss / Miniboss，且目前沒有 Barkskin / Earthroot Armor 時才使用；沿用原版 `Barkskin.conditionallyAppend()`，強度為 `2 + level/3`、interval 50。
- Scroll 與其他物品一樣都可存放。目前只有已鑑定的 `ScrollOfTeleportation`、`ScrollOfTerror`、`ScrollOfDread` 具有自動使用語意；其他 Scroll 只作為背包資源，可交還 Hero。
- `ScrollOfTeleportation` 是 retreat 的最後直接脫離層：只有普通安全走位、wand escape utility 與可控 `StoneOfBlink` 都不可用時才消耗。它重用原版 `ScrollOfTeleportation.teleportChar(Char)`，所以可解除 Roots；若 teleport 失敗，卷軸會放回 CoHero 背包而不浪費。
- `ScrollOfTerror` 只在 retreat 且無安全逃生步時使用；若能影響至少 2 名當前可見、清醒敵人，或單一可恐懼敵人已造成立即致命風險，優先於隱形藥。作用範圍使用 CoHero 自己的 FOV，不借用 `Dungeon.level.heroFOV`；失明或 `MagicImmune` 時不讀。效果沿用原版 `Terror.DURATION`，並將恐懼來源設為 CoHero。
- `ScrollOfDread` 位於普通 Terror 之後，避免先消耗較稀有的高級卷軸。retreat 時至少有 2 名可見清醒威脅，且存在 2+ 當前攻擊者、立即致命風險或 TTD ≤ 2 回合時才用；可 Dread 的目標取得原版 Dread，免疫 Dread 但可 Terror 的目標退化成 Terror。上游 `Dread.act()` 原本把「離開視野且距離 ≥ 6 後消失」硬綁 `Dungeon.hero` / `heroFOV`；CoHero patch 改為依 Dread 保存的 caster `object` 找實際 `Char`，Hero 行為保持等價，CoHero cast 則使用 CoHero 自己的 FOV / 位置。
- CoHero 目前只會主動使用六種明確定義的戰鬥符石：`StoneOfAggression`、`StoneOfBlast`、`StoneOfFear`、`StoneOfDeepSleep`、`StoneOfBlink`、`StoneOfFlock`；其他 Runestone 仍可存放，但 AI 不會使用。Runestone 在 SPD 本來就永遠 identified，因此不存在用符石選擇洩漏未知身份的問題。
- 原版 `Runestone.onThrow()` 仍以玩家 Hero 為中心，會讀 `Dungeon.hero`、`curUser` 與 Hero Talent hook；CoHero 不直接呼叫這個入口，而是在自身 AI 中重現六種已確認安全的效果，仍更新 `Catalog.countUse()`、消耗一枚符石、解除 CoHero 自身隱形並花費一回合。
- `StoneOfBlast` 只在爆炸半徑內至少能命中 2 名可見清醒敵人時使用；只要會炸到 Hero、CoHero、其他友軍／中立角色、睡眠敵人或任何地面 heap 就放棄。實際爆炸仍使用原版 `Bomb.ConjuredBomb.explode()`，因此傷害與地形破壞語意保持原版。
- `StoneOfAggression` 只在至少 3 名可見清醒威脅時使用，目標必須不是 Boss / Miniboss，並偏好附近還有其他敵人且 HP 較高者，讓敵群互相轉火；已存在 Aggression 的目標不重複浪費。
- `StoneOfDeepSleep` 主要用於兩名高價值威脅的戰鬥（例如存在遠程壓制、Boss / Miniboss 戰），優先讓非當前近戰目標退出戰鬥；retreat 且無安全走位時也可作單體緊急控制。免疫 Sleep、已睡眠或已存在 `MagicalSleep` 的目標不使用。
- `StoneOfFear` 定位為 retreat 單體控制：無安全走位後，若目前一輪接近致命、TTD ≤ 2.5 回合或有 2+ 當前攻擊者，優先對最危險且可恐懼的敵人使用；免疫 Terror 或已在 Terror 中的目標不浪費。
- `StoneOfBlink` 定位為無安全相鄰逃生格時的直接脫離工具。目的地只從 CoHero 當前 FOV 內、已知、安全、可投射到達且至少讓最近敵人距離增加 2 格的格子選擇；可以在 Roots 定身時使用，因原版 `ScrollOfTeleportation.teleportToLocation()` 成功後會解除 Roots。
- `StoneOfFlock` 定位為封鎖／拖延：單一遠程敵人若沒有免費 LOS cover 可用，且距離足夠遠時可用羊群包住其周圍；retreat 時也可在 2+ 威脅下作最後的阻隔。中心點必須離 Hero 與 CoHero 超過 2 格，至少有 3 個合法 sheep spawn cell，避免 AI 反而把自己人直接困死。
- CoHero 有 `MagicImmune` 時不主動使用上述符石；免費走位、既有 wand escape utility 與 anti-ranged cover 仍優先於消耗符石。
- CoHero 會把已知、可安全到達的植物視為有限場景生存資源：無敵人時，HP < 60% 可走向 6 格 path distance 內的 Sungrass，觸發後留在原格直到補滿或戰鬥打斷；有嚴重負面狀態時，優先走向 4 格內的 Mageroyal。戰鬥中若 Mageroyal / Earthroot 就在安全鄰格，可分別用來清 debuff 或在高威脅戰鬥取得 Earthroot Armor；retreat 且風險很高時，安全鄰格上的 Fadeleaf 會被主動當成免費 teleport。只考慮已被 Hero 或 CoHero 視野實際揭露成 `visited` 的植物；單純因 Magic Mapping 成為 `mapped` 的格子不算已知植物，避免 AI 讀到未曾看見的植物種類。
- 其他 Potion 只作為背包資源，可交還 Hero；CoHero 不會自行飲用。

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

- 武器、防具、戒指、法杖屬於 CoHero 裝備／戰鬥系統。`RingOfTenacity` 另在 CoHero 的 `damage()` 補上與 Hero 相同的 `RingOfTenacity.damageMultiplier()`；Warrior / Duelist 固有的 +0 Tenacity 在同一處以相同 `0.85^missingHP%` 公式相乘，因此與真正裝備的 Tenacity 戒指保持原版等價疊加。`RingOfElements` 的真戒效果先沿用原版 `Char.resist()`；Mage 固有的 +0 Elements 再對同一組 `RingOfElements.RESISTS` 來源乘上 `0.825`，因此與真正裝備的 Elements 戒指同樣保持原版等價疊加。
- CoHero 的武器規則與防具／戒指分開：近戰武器只要求實際未詛咒，不要求已知詛咒狀態；防具與戒指仍維持 GhostHero 式的「已確認未詛咒」才能裝備。武器與防具若力量需求超過 CoHero STR 仍不能裝備。
- 武器／防具的強化等級若未知，力量檢查使用 +0 的 `STRReq(0)`，避免藉由能否裝備反推出隱藏強化等級。
- CoHero 已裝備但尚未完全鑑定的近戰武器、護甲與戒指，沿用 SPD 原版被動鑑定進度：武器／護甲需要實際使用並搭配正常戰鬥 EXP 解鎖後續鑑定次數，戒指則依裝備期間取得的正常 EXP 推進。CoHero 不套用 Hero 的 item-ID Talent 加速，倍率固定 1.0；進度仍保存於物品本身，因此 Hero 與 CoHero 之間轉交同一件物品不會重置。Potion of Experience 不推進此被動鑑定。
- CoHero 背包的「可存放」與「可由 CoHero 使用」是兩個獨立概念：任何正常 `Item` 都可交給 CoHero 保存，包括目前沒有 AI 語意的 Artifact、Trinket、食物、種子、未支援符石與第三方物品；storage-only 物品不會被 CoHero 主動使用，但可隨時交還 Hero。Wand 也遵守同一條規則：只有 `CoHeroWandAdapter.supported()` 的法杖在放入 CoHero 背包後會接上 CoHero 的 wand charge 流程；純 storage-only 的未知／未支援法杖不會因為只是存放在 CoHero 背包裡就被動充能。
- CoHero 背包 UI 以框線標示已有明確 CoHero 使用／裝備語意的物品。框線代表已實作 capability，不代表此刻一定能成功使用；裝備仍可能受詛咒或 STR 限制。未鑑定 Potion / Scroll 不依隱藏真實類型顯示 capability，避免從 UI 洩漏鑑定資訊。
- Potion 目前只有已鑑定的 `PotionOfHealing`、`ElixirOfHoneyedHealing`、`PotionOfShielding`、`PotionOfInvisibility`、`PotionOfHaste`、`PotionOfStamina`、`PotionOfCleansing`、`PotionOfEarthenArmor` 具有自動使用語意；Scroll 目前只有已鑑定的 `ScrollOfTeleportation`、`ScrollOfTerror`、`ScrollOfDread` 具有自動使用語意。Runestone 只有 `StoneOfAggression`、`StoneOfBlast`、`StoneOfFear`、`StoneOfDeepSleep`、`StoneOfBlink`、`StoneOfFlock` 具有 CoHero 戰鬥使用語意；其他符石只作為 storage-only 物品。`Ankh` 具有死亡時自動復活語意。
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
- 出口已找到，我要現在下樓，還是讓同伴繼續探索、撿取資源或處理戰鬥？

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
- 發現出口本身不停止探索，也不會要求 CoHero 前往出口集合；出口發現只解除前期 30% 探索範圍限制。
- 玩家 Hero 作為唯一樓層 transition 觸發者。
- 自己的背包 / 裝備資源。
- 共享 Hero STR、lvl / exp，但保有獨立 HP / HT。
- CoHero 擊殺沿用原版流程增加共同 EXP。
- 基礎自然回血，不處理 Hunger。
- CoHero 可自動使用少數已鑑定生存／逃生／戰鬥機動消耗品（治療／護盾／隱形／Haste／Stamina／Cleansing／Earthen Armor，以及 Teleportation／Terror／Dread 卷軸），並支援敵意、震爆、恐懼、沉睡、閃現、羊群六種戰鬥符石；另會利用已知 Sungrass / Mageroyal / Earthroot / Fadeleaf 作免費場景生存資源，並可由自己背包中的 Ankh 在死亡時復活。
- 完全由背包與裝備驅動的基本戰鬥行為。
- 近戰武器可及時只使用近戰武器。
- 高閃避目標優先法杖。
- 魔法免疫目標禁用法杖。
- 當前距離下使用最高傷害的合法攻擊選項。
- 投擲武器以 setID 追蹤並在無可見威脅時回收；不跨樓層追蹤。
- 避免主動吵醒可視範圍內的睡眠怪物。
- 讀取原版 targeted-cell 預告並優先避開即將爆發的危險格，也會避開已存在的有害氣體、火焰、電流與冰凍類 blob。
- 官方六職業具有固有 trait；Mage Staff、Spirit Bow 等已明確支援的專武使用原版物件與 CoHero-safe seam。
- CoHero 背包提供即時基本數值顯示，並提供 1.0x～3.0x、每 0.1x 一格的敵人生成倍率滑桿；1.0x 完全保留原版自然生成，較高倍率同時提高自然重生速率與自然敵人數量上限，但仍使用原版的選怪、放置、能力與掉落規則。倍率保存於 CoHero 狀態；CoHero 被刻意留在特殊樓層外時不作用。locator 提供 Hero / CoHero 雙向畫面外監控。
- 同伴死亡即 Game Over。
- Hero 可直接觸發普通樓層 transition，不要求 CoHero 抵達出口旁；換層前保存 CoHero 狀態，下一層在 Hero 附近重新生成。
- 無停止移動或攻擊政策開關。

如果只靠這些就已經大幅改變 SPD 的決策體驗，才有理由繼續增加更複雜的 Hero 能力。
---

設計原則：先以最小規則驗證核心張力。若一個新系統不是為了解決已觀察到的問題，就不要提前加入。