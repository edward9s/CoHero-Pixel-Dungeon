# Shattered portability audit

Snapshot: current `integration/shattered/apply.sh` profile after the CoHero settings-tab integration.

## Classification

Each row has one **primary** label, for triage only. A file can contain several kinds of hooks, as noted in the description. These labels do **not** define independent build profiles; omitting a patch may require an explicit change to common code and a review of the requested feature set.

- **A — core lifecycle or actor hook:** required to establish the companion and its basic interaction with the host.
- **B — host semantic adapter:** host rules assume a `Hero` or expose no suitable non-Hero entry point.
- **C — full feature parity:** class weapon, item, or wand support beyond the first playable melee companion.
- **D — presentation or branding:** preview, visual FOV, display and labels.
- **E — encounter or hazard rule:** particular enemy, boss, terrain or delayed effect.

The 52 Java targets are classified A 7, B 11, C 14, D 6, E 14. `build.gradle` (`patch_app_package.py`), `AndroidManifest.xml` (`patch_android_manifest.py`), and message resources (`patch_messages.py`) are outside this count; they belong to packaging, platform permissions, and presentation, respectively.

| Primary | Host target | Shattered patch owner | Existing responsibility |
| --- | --- | --- | --- |
| A | `Dungeon.java` | `patch_dungeon_save.py` | save/load, preview, and one-shot run-failure submission |
| A | `Level.java` | `patch_level_mobs.py` | persist companion in level mob set |
| A | `Hero.java` | `patch_hero.py` | transition gate; identification EXP is B |
| A | `HeroSelectScene.java` | `patch_hero_select.py` | new run companion selection; scene presentation is D |
| A | `GameScene.java` | `patch_gamescene.py` | scene-ready restore; UI is D and hazard display is E |
| A | `Mob.java` | `patch_mob_cohero.py` | combat target and ranged-damage base semantics; remote attack display is D |
| A | `WndGame.java` | `patch_wndgame.py` | restart availability after companion run end |
| B | `Char.java` | `patch_cohero_class_traits.py` | cleric Bless accuracy and evasion |
| B | `RingOfArcana.java` | `patch_cohero_ring_traits.py` | huntress ring effect |
| B | `Weapon.java` | `patch_weapon_identification.py` | shared identification on use and EXP |
| B | `Armor.java` | `patch_armor_identification.py` | shared identification on use and EXP |
| B | `Ring.java` | `patch_ring_identification.py` | shared identification EXP |
| B | `HighGrass.java` | `patch_highgrass.py` | huntress grass behavior |
| B | `Dread.java` | `patch_dread_cohero.py` | source-aware fear and FOV |
| B | `MissileWeapon.java` | `patch_missileweapon.py` | non-Hero owner, damage and durability |
| B | `Wand.java` | `patch_wand_base.py` | non-Hero caster context and charge flow |
| B | `DamageWand.java` | `patch_damage_wand.py` | caster-specific damage and hero talent |
| B | `WandOfMagicMissile.java` | `patch_magic_missile_wand.py` | caster-owned magic charge |
| C | `MagesStaff.java` | `patch_mages_staff.py` | mage staff wand access |
| C | `SpiritBow.java` | `patch_spirit_bow.py` | huntress bow damage and STR |
| C | `WandOfBlastWave.java` | `patch_blastwave_cohero.py` | caster FX source |
| C | `WandOfLivingEarth.java` | `patch_living_earth.py` | guardian ownership and persisted owner ID; E implications |
| C | `WandOfFrost.java` | `patch_wand_frost.py` | caster FX source |
| C | `WandOfDisintegration.java` | `patch_wand_disintegration.py` | caster beam source |
| C | `WandOfLightning.java` | `patch_wand_lightning.py` | caster and cast preparation |
| C | `WandOfPrismaticLight.java` | `patch_wand_prismatic_light.py` | caster visual source |
| C | `WandOfRegrowth.java` | `patch_wand_regrowth.py` | caster and cone preparation |
| C | `WandOfTransfusion.java` | `patch_wand_transfusion.py` | caster support target |
| C | `WandOfCorruption.java` | `patch_wand_corruption.py` | progression owner and effectiveness probe |
| C | `WandOfCorrosion.java` | `patch_wand_corrosion.py` | caster FX source |
| C | `WandOfFireblast.java` | `patch_wand_fireblast.py` | caster and cone preparation |
| C | `WandOfWarding.java` | `patch_wand_warding.py` | ward ownership, limit and persisted ownership flag |
| D | `FogOfWar.java` | `patch_cohero_visual_fov.py` | companion rendering FOV |
| D | `GamesInProgress.java` | `patch_games_in_progress.py` | save slot metadata for companion preview |
| D | `StartScene.java` | `patch_start_scene.py` | save slot portrait and label |
| D | `TitleScene.java` | `patch_title_scene.py` | CoHero branding |
| D | `MenuPane.java` | `patch_menu_pane.py` | version branding |
| D | `WndSettings.java` | `patch_wndsettings.py` | CoHero settings-tab hook |
| E | `GreatCrab.java` | `patch_great_crab_cohero.py` | special surprise defense |
| E | `Shaman.java` | `patch_shaman_ranged_damage.py` | exact ranged damage probe for close-vs-trade AI |
| E | `DM100.java` | `patch_dm100_ranged_damage.py` | exact ranged damage probe for close-vs-trade AI |
| E | `Warlock.java` | `patch_warlock_ranged_damage.py` | exact ranged damage probe for close-vs-trade AI |
| E | `Eye.java` | `patch_eye_ranged_damage.py` | exact ranged damage probe for close-vs-trade AI |
| E | `GnollGuard.java` | `patch_gnoll_guard_ranged_damage.py` | exact ranged spear damage probe for close-vs-trade AI |
| E | `Elemental.java` | `patch_elemental_ranged_damage.py` | marks subtype-specific effect attacks as non-comparable direct damage |
| E | `PrisonBossLevel.java` | `patch_prison_boss_cohero.py` | arena rewrite relocation |
| E | `LockedFloor.java` | `patch_locked_floor_cohero.py` | boss-floor relocation and persisted relocation flag |
| E | `Chasm.java` | `patch_chasm_cohero.py` | shared fall transition with actor-owned landing penalties |
| E | `PitfallTrap.java` | `patch_pitfalltrap_cohero.py` | record actual fallers and defer one party transition until trap scan completes |
| E | `DelayedRockFall.java` | `patch_delayed_rockfall.py` | delayed hazard warning |
| E | `VaultBossElemental.java` | `patch_vault_firewall.py` | vault firewall hazard probe |
| E | `ElementalBlast.java` | `patch_elemental_blast_living_earth.py` | Living Earth guardian ownership |

## Three high-churn files

### GameScene.java: keep lifecycle order explicit

`patch_gamescene.py` is one file owner, but contains many separate integration points: `CoHero.onGameSceneReady()` before Hero actor scheduling, inventory tag creation/layout, locator placement, range grid installation, remote view update, examination visibility, and targeted-cell hazard warning. The ready hook is an A dependency; inventory/locator/remote view/examination are D; targeted hazards are E. A change to the stock tag layout can break this file's patch even if persistence is intact. Keep one patch owner for `GameScene.java`; do not manufacture multiple scripts writing the same file to match the labels. A second fork can adapt its own scene layout without changing the save or combat rules.

The restore hook must remain after terrain/fog/UI construction and before Hero scheduling. Moving it to a generic startup abstraction requires checking the host's `Level.occupyCell()` and actor start order. The current implementation has deliberately chosen an ordering, so a narrower API is only useful when a real fork offers an equivalent stable point.

### Mob.java: semantics, not just a call site

`patch_mob_cohero.py` implements `coHeroCanAttackFrom()` by temporarily changing `pos` in a `try/finally`, thereby invoking the actual overridden `canAttack()`. A fork's override may depend on more mutable state or produce side effects, so copying this probe without inspecting the host's attack implementations is unsafe. The same script also adds CoHero surprise defense, a sleeping hostile FOV gate, remote attack presentation, and an exclusion from stock held-ally transport.

The sleeping patch replaces stock hostile selection (`highestChance = Float.POSITIVE_INFINITY` and smallest `detectionChance`) with the largest `detectionChance` candidate. That is an existing gameplay change in this profile, not a mechanical portability seam. Preserve it intentionally when porting, or explicitly review gameplay behavior before changing it. The remote attack callback is presentation; sleeping/attack/surprise are actor semantics; held-ally transport is lifecycle. Keep all of them visible under the single `Mob.java` owner.

### Wand.java: caster context and synchronous execution

`patch_wand_base.py` adds a transient non-Hero caster, `zapUser()`, `progressionHero()`, `coHeroPrepareZap()`, and the `coHeroCast()` path. `coHeroCast()` prepares and resolves `onZap()` before starting FX, then clears its caster in `finally`. This ordering makes the separate preparation hooks in Lightning, Regrowth and Fireblast necessary. A different fork's wand may initialize hit state inside FX, and an asynchronous callback must not assume the transient caster remains set.

`DamageWand.java` separates Hero talent damage from non-Hero damage, while concrete wand scripts adapt their own visual/caster/ownership details. A single universal `Wand` patch cannot replace these safely without inspecting each wand's effects. The actual opportunity to shrink this surface is a host-provided, explicit caster API that preserves gameplay, charge and FX order; investigate it with an actual target fork.

## Exactness and persistence

The one-owner-per-target invariant was checked against the 52 Java patch calls in `apply.sh`. It localizes fork drift to a host file. The follow-up in this branch tightens the five patch scripts identified by this audit:

- `patch_wand_lightning.py`, `patch_wand_regrowth.py`, `patch_wand_fireblast.py` and `patch_wand_prismatic_light.py` now check the complete set of source lines containing `curUser` before the existing replacement. The expected lines were taken from the Shattered v4.0.0 release. Extra, missing or changed source lines cause an explicit failure before writing the file.
- `patch_living_earth.py` now verifies the exact cardinality of each former unchecked replacement, including the two occurrences of guardian armor assignment and the two caster particle calls. All replacements still produce the same Java source when their known anchors match.
- `CoHeroAlly.storeInBundle()` and `restoreFromBundle()` staying unchanged establishes only the actor's schema. This existing integration profile also persists data in `LockedFloor` (`cohero_start_relocated`), `WandOfLivingEarth.EarthGuardian` (`owner_id`) and `WandOfWarding.Ward` (`cohero_owned`), in addition to CoHero game state. Review these before claiming whole-save compatibility. The anchor checks do not change those persisted fields.

## Next port and validation

Choose a concrete SPD fork. Implement its `integration/<fork>/apply.sh` and one-target patch owners in the existing port order: lifecycle and persistence, actor semantics, equipment and combat, encounter safety, then presentation. Begin with a **defined minimal feature set**, since C and D are classifications and the existing common Java may still call methods introduced by their patches. Avoid fork conditionals in common Java and avoid silently skipping an anchor.

When adapting these patches for another fork, establish its upstream release and exact anchor counts. Validate the host profile with the complete Android/Desktop overlay and SMM overlay where supported, then load an older CoHero save at runtime when touching lifecycle or persisted host actors. Build success alone does not establish runtime save compatibility. This branch changes the audit document and five patch scripts. It does not change the generated Java for Shattered v4.0.0 when the known anchors match. Build, patch execution and old-save loading are left for the user to run.
