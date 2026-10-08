# Shattered portability audit

Snapshot: current `integration/shattered/apply.sh` profile after the CoHero settings-tab integration.

## Classification

Each row has one **primary** label, for triage only. A file can contain several kinds of hooks, as noted in the description. These labels do **not** define independent build profiles; omitting a patch may require an explicit change to common code and a review of the requested feature set.

- **A — core lifecycle or actor hook:** required to establish the companion and its basic interaction with the host.
- **B — host semantic adapter:** host rules assume a `Hero` or expose no suitable non-Hero entry point.
- **C — full feature parity:** class weapon, item, or wand support beyond the first playable melee companion.
- **D — presentation or branding:** preview, visual FOV, display and labels.
- **E — encounter or hazard rule:** particular enemy, boss, terrain or delayed effect.

The table below classifies the major Java integration owners. The executable inventory is `integration/shattered/apply.sh`; documentation deliberately does not duplicate a numeric target count. `build.gradle` (`patch_app_package.py`), `AndroidManifest.xml` (`patch_android_manifest.py`), and message resources (`patch_messages.py`) are non-Java integration owners.

| Primary | Host target | Shattered patch owner | Existing responsibility |
| --- | --- | --- | --- |
| A | `Dungeon.java` | `patch_dungeon_save.py` | save/load, preview, and one-shot run-failure submission |
| A | `Level.java` | `patch_level_mobs.py` | persist companion in level mob set; keep CoHero-owned ward vision out of Hero gameplay FOV |
| A | `Hero.java` | `patch_hero.py` | transition gate; identification EXP is B |
| A | `HeroSelectScene.java` | `patch_hero_select.py` | new run companion selection; scene presentation is D |
| A | `GameScene.java` | `patch_gamescene.py` | scene-ready restore; UI is D and hazard display is E |
| A | `Mob.java` | `patch_mob_cohero.py` | combat target and ranged-damage base semantics; remote attack display is D |
| A | `Necromancer.java` | `patch_necromancer_cohero.py` | exact death-removes relationship for its own summoned skeleton |
| A | `SpectralNecromancer.java` | `patch_spectral_necromancer_cohero.py` | exact death-removes relationships for its tracked wraiths |
| A | `WndGame.java` | `patch_wndgame.py` | restart availability after companion run end |
| B | `Char.java` | `patch_cohero_class_traits.py` | cleric Bless accuracy and evasion |
| B | `RingOfArcana.java` | `patch_cohero_ring_traits.py` | huntress ring effect |
| B | `RingOfForce.java` | `patch_ring_force.py` | stock unarmed force damage seam |
| B | `Weapon.java` | `patch_weapon_identification.py` | shared identification on use and EXP |
| B | `Armor.java` | `patch_armor_identification.py` | shared identification on use and EXP |
| B | `Ring.java` | `patch_ring_identification.py` | shared identification EXP; scopes `EnhancedRings` to the actual Hero-owned ring buff |
| B | `RingOf*.java` | `patch_ring_info_owner.py` | owner-aware combined ring stats in item info |
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
| E | `Eye.java` | `patch_eye_ranged_damage.py` | exact ranged damage probe for close-vs-trade AI; expose the live Death Gaze target for hazard avoidance |
| E | `RipperDemon.java` | `patch_ripper_demon_leap.py` | expose the pending leap target so CoHero can avoid the actual leap collision cell |
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

`patch_mob_cohero.py` implements `coHeroCanAttackFrom()` by temporarily changing `pos` in a `try/finally`, thereby invoking the actual overridden `canAttack()`. A fork's override may depend on more mutable state or produce side effects, so copying this probe without inspecting the host's attack implementations is unsafe. It also adds `coHeroRangedDamageRoll()`: standard ranged attacks default to `damageRoll()`, while enemy classes whose real ranged attack uses a different formula override that seam in their own one-target patch. A negative result marks effect-driven ranged behavior whose direct damage is not comparable, so the enemy-damage 1.5× preference rule is skipped for that target. `Mob.coHeroDeathRemoves()` is the default-false strategic relation seam; concrete host mobs override it only when their own death semantics immediately remove a specific dependent. `Necromancer` reports its exact `mySkeleton` / restored skeleton ID, while `SpectralNecromancer` reports only Wraith IDs it actually created. The same Mob script also adds CoHero surprise defense, a sleeping hostile FOV gate, remote attack presentation, and an exclusion from stock held-ally transport. It additionally exposes `coHeroSleepingDetectionChanceAt()` for non-combat navigation: the probe temporarily moves the hostile actor, calls the mob's **current live `SLEEPING` state's** `detectionChance()` implementation (including `StealthGameplaySleeping` overrides), applies the stock flying stealth rule, and restores the hostile position in `finally`. Ports must preserve that live-state dispatch rather than duplicate the stock distance formula in CoHero code.

The sleeping patch replaces stock hostile selection (`highestChance = Float.POSITIVE_INFINITY` and smallest `detectionChance`) with the largest `detectionChance` candidate. That is an existing gameplay change in this profile, not a mechanical portability seam. Preserve it intentionally when porting, or explicitly review gameplay behavior before changing it. The navigation-side detection probe is separate: it does not roll wake-up RNG or change mob state; it only asks the host mob how detectable CoHero would be from a hypothetical cell so loot recovery and idle exploration can reject moves that increase wake risk. The remote attack callback is presentation; sleeping/attack/surprise are actor semantics; held-ally transport is lifecycle. Keep all of them visible under the single `Mob.java` owner.

### Wand.java: caster context and synchronous execution

`patch_wand_base.py` adds a transient non-Hero caster, `zapUser()`, `progressionHero()`, `coHeroPrepareZap()`, and the `coHeroCast()` path. `coHeroCast()` prepares and resolves `onZap()` before starting FX, then clears its caster in `finally`. This ordering makes the separate preparation hooks in Lightning, Regrowth and Fireblast necessary. A different fork's wand may initialize hit state inside FX, and an asynchronous callback must not assume the transient caster remains set.

`DamageWand.java` separates Hero talent damage from non-Hero damage, while concrete wand scripts adapt their own visual/caster/ownership details. A single universal `Wand` patch cannot replace these safely without inspecting each wand's effects. The actual opportunity to shrink this surface is a host-provided, explicit caster API that preserves gameplay, charge and FX order; investigate it with an actual target fork.

## Structural patching, tolerant matching, and persistence

The project is designed for unattended builds, so integration patching uses layered strictness rather than whole-source exactness.

1. **Exact first.** If the known upstream fragment is unchanged, the original replacement path is used.
2. **Token-equivalent fallback for every Java patch.** All Java patch owners read source through `java_source()`. Whitespace and comments are ignored for fallback matching, but Java identifiers, literals, operators and punctuation must still match. Cardinality remains strict.
3. **Safe insertion-boundary fallback.** Only when the requested change is a pure insertion, the matcher may fall back to a unique token prefix/suffix boundary. This preserves unrelated upstream statements inserted inside the old anchor instead of deleting them.
4. **Structural scoping for high-churn semantics.** `patch_wand_warding.py` and `patch_mob_cohero.py` locate the owning class/method/nested-class before applying local semantic edits and then verify postconditions.
5. **Fail on real ambiguity or semantic drift.** Changed operators, changed API calls, missing methods, duplicate candidate regions, or materially changed host behavior still stop the build. The system is tolerant of presentation drift, not permissive about gameplay semantics.

`java_patch.py` implements comment/literal-aware Java block scanning, method/class lookup, token-aware matching and insertion boundaries. `scripts/test_java_patch.py` derives the Java patch inventory from `apply.sh`, verifies every Java patch uses `java_source()`, and exercises overload/nested-class handling, local-variable renames, whitespace/comment drift, ambiguous matches and upstream insertion preservation. Build, CoHero+SMM build, and release workflows compile-check all Python integration scripts before running these tests.

Non-Java owners are also semantic rather than formatting-sensitive:

- `patch_app_package.py` matches the Gradle identity assignments regardless of quote style and harmless spacing.
- `patch_android_manifest.py` detects permissions by the `android:name` attribute and inserts only the missing permissions before the unique application element.
- `patch_messages.py` validates CoHero key/placeholder sets without requiring locale key order. If upstream adds a locale before CoHero has a translation, the base CoHero messages are used for that locale so an unattended build still completes; source locales not used by that upstream release are harmless.

This remains intentionally different from fuzzy source matching. If a host changes the actual semantic tokens required by a patch, the correct behavior is to fail early and adapt that patch owner rather than guess.

Persistence is a separate review surface. `CoHeroAlly.storeInBundle()` and `restoreFromBundle()` establish only the actor schema; the integration profile also persists data in `LockedFloor` (`cohero_start_relocated`), `WandOfLivingEarth.EarthGuardian` (`owner_id`) and `WandOfWarding.Ward` (`cohero_owned`). Matching tolerance does not relax those persisted-field contracts.

## Next port and validation

Choose a concrete SPD fork. Implement its `integration/<fork>/apply.sh` and one-target patch owners in the existing port order: lifecycle and persistence, actor semantics, equipment and combat, encounter safety, then presentation. Begin with a **defined minimal feature set**, since C and D are classifications and the existing common Java may still call methods introduced by their patches. Avoid fork conditionals in common Java and avoid silently skipping an anchor.

When adapting these patches for another fork, establish its upstream release, structural assumptions and semantic anchor cardinality. Validate the host profile with the complete Android/Desktop overlay and SMM overlay where supported, then load an older CoHero save at runtime when touching lifecycle or persisted host actors. Build success alone does not establish runtime save compatibility. Changes that add or alter generated Java, including the ranged-damage probes, require full patch execution and build validation for the target host.
