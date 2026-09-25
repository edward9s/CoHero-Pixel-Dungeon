# Shattered integration port map

This profile currently patches 46 upstream Java files, plus `build.gradle`, `AndroidManifest.xml`, and the message-resource directory. The number is useful as an upper bound, but the files do not all have the same portability cost.

## Recommended port order

### 1. Lifecycle and persistence

Get these working first. They establish the companion lifecycle and saved-game contract.

- `Dungeon.java` — CoHero store/restore and save preview.
- `Level.java` — saves CoHero with the level mob set.
- `Hero.java` — transition interception and shared identification progress.
- `HeroSelectScene.java` — companion class selection.
- `GameScene.java` — restores/starts CoHero after the scene is ready. This file also contains presentation hooks, so it is a high-churn integration point.
- `GamesInProgress.java` / `StartScene.java` — save-slot companion metadata and preview.

Do not change CoHero Bundle keys or the serialized `CoHeroAlly` class while adapting these hooks.

### 2. Actor semantics

These make stock SPD rules treat CoHero as a real second combatant instead of assuming every player-controlled actor is `Hero`.

- `Char.java` — class-trait seams.
- `Mob.java` — attack probes, surprise semantics, sleeping detection, remote attack presentation, and held-allies exclusion.
- `GreatCrab.java` — Great Crab surprise handling.
- `HighGrass.java` — Huntress grass semantics.
- `Dread.java` — CoHero fear behavior.
- `Chasm.java` — redirects CoHero chasm falls through the stock Hero fall transition.
- `PitfallTrap.java` — defers shared Hero/CoHero pitfall transitions until the trap finishes scanning affected cells.
- `LockedFloor.java` — boss-floor relocation lifecycle.

`Mob.java` is a high-value, high-risk port target because several CoHero behaviors depend on the host fork's real mob attack semantics.

### 3. Equipment and ranged combat

Port these after basic melee movement/combat works.

- `Weapon.java`, `Armor.java`, `Ring.java` — identification progress.
- `RingOfArcana.java` — CoHero ring-trait behavior.
- `MissileWeapon.java` — CoHero projectile resolution.
- `MagesStaff.java` / `SpiritBow.java` — class-specific weapons.
- `Wand.java`, `DamageWand.java`, `WandOfMagicMissile.java` — common CoHero wand context.
- individual wand classes — caster-specific effects and AI-safe semantics.

The individual wand group is the largest concrete-class dependency surface. Keep fork differences in the integration profile; do not add fork switches to `CoHeroWandAdapter` merely to satisfy a host layout difference.

### 4. Encounter-specific safety

These hooks protect CoHero from host mechanics that rewrite terrain or expose delayed hazards.

- `PrisonBossLevel.java` — Tengu arena rewrites.
- `DelayedRockFall.java` — telegraphed rockfall.
- `VaultBossElemental.java` — vault firewall.
- `ElementalBlast.java` / `WandOfLivingEarth.java` — Living Earth ownership/lifecycle interactions.

Treat these as explicit host integrations. If another fork has replaced the encounter, write a fork-specific patch instead of forcing the Shattered anchor to match.

### 5. Presentation and UI

These are important for the finished port but should not block early gameplay bring-up.

- `FogOfWar.java` — companion visual FOV.
- `TitleScene.java` / `MenuPane.java` — version branding.
- `WndSettings.java` — Android/Desktop save export/import controls in the Interface tab.
- `WndGame.java` — CoHero game-menu integration.
- `GameScene.java` — locator, inventory tag, remote view, hazard overlays, and examination visibility.
- message resources — CoHero strings.

## Patch-target invariant

The Shattered profile now keeps Java patch ownership one-to-one:

- 46 Java patch calls target 46 unique upstream Java files.
- each Java patch script edits exactly one upstream Java file;
- each upstream Java file is owned by exactly one patch script.

Keep this invariant when adding or changing Shattered hooks. It localizes fork drift: a changed Lightning implementation should fail the Lightning patch, not every wand patch.

The highest-churn individual targets are still:

1. `GameScene.java` / `patch_gamescene.py` — lifecycle plus several UI/presentation seams.
2. `Mob.java` / `patch_mob_cohero.py` — core mob attack, surprise, sleep and transport semantics.
3. individual wand classes — concrete caster/effect behavior, now isolated into one script per wand.
4. `Hero.java` / `patch_hero.py` — transition interception plus shared identification EXP.
5. `Wand.java` / `patch_wand_base.py` — common non-Hero wand-caster context.

The number of upstream files has not been artificially reduced; instead, failures are now localized to the actual host file that diverged.

## Porting rules

- Keep common CoHero Java fork-agnostic.
- Keep host-specific anchors under `integration/<fork>/patches/`.
- Preserve fail-fast exact anchors; do not use fuzzy patching.
- Preserve patch order explicitly in the fork's `apply.sh`.
- Keep Android save-transfer storage permission wiring host-specific; common CoHero code must not depend on SMM. Desktop folder selection stays reflection-based in common code so Android does not acquire a desktop library dependency.
- Bring up lifecycle first, then actor semantics, then equipment/combat, encounter safety, and presentation.
- A successful compile is necessary but not sufficient: verify loading an existing CoHero save after any lifecycle or persistence change.
