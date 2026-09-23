#!/usr/bin/env bash
set -euo pipefail

phase="${1:?usage: apply.sh <base|wndgame|messages> <upstream-root> <cohero-root>}"
upstream="${2:?missing upstream root}"
cohero="${3:?missing CoHero root}"

if [[ ! -d "$upstream" ]]; then
  echo "CoHero integration: upstream root does not exist: $upstream" >&2
  exit 2
fi
patches="$cohero/integration/shattered/patches"
if [[ ! -d "$patches" ]]; then
  echo "CoHero integration: Shattered patch directory does not exist: $patches" >&2
  exit 2
fi

case "$phase" in
  base)
    python "$patches/patch_app_package.py" "$upstream/build.gradle"
    python "$patches/patch_gamescene.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java"
    python "$patches/patch_cohero_visual_fov.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/tiles/FogOfWar.java"
    python "$patches/patch_hero_transition.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java"
    python "$patches/patch_cohero_class_traits.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/Char.java"
    python "$patches/patch_cohero_ring_traits.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/rings/RingOfArcana.java"
    python "$patches/patch_equipment_identification.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/Weapon.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/armor/Armor.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/rings/Ring.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/Hero.java"
    python "$patches/patch_hero_select.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/HeroSelectScene.java"
    python "$patches/patch_dungeon_save.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java"
    python "$patches/patch_games_in_progress.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/GamesInProgress.java"
    python "$patches/patch_start_scene.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/StartScene.java"
    python "$patches/patch_version_ui.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/scenes/TitleScene.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/ui/MenuPane.java"
    python "$patches/patch_level_mobs.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java"
    python "$patches/patch_mob_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Mob.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/GreatCrab.java"
    python "$patches/patch_prison_boss_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonBossLevel.java"
    python "$patches/patch_locked_floor_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/buffs/LockedFloor.java"
    python "$patches/patch_chasm_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/features/Chasm.java"
    python "$patches/patch_highgrass.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/features/HighGrass.java"
    python "$patches/patch_dread_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/buffs/Dread.java"
    python "$patches/patch_delayed_rockfall.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/DelayedRockFall.java"
    python "$patches/patch_vault_firewall.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/quest/vault/VaultBossElemental.java"
    python "$patches/patch_missileweapon.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/MissileWeapon.java"
    python "$patches/patch_special_weapons.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/melee/MagesStaff.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/weapon/SpiritBow.java"
    python "$patches/patch_wands.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/Wand.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/DamageWand.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfMagicMissile.java"
    python "$patches/patch_blastwave_cohero.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfBlastWave.java"
    python "$patches/patch_living_earth.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfLivingEarth.java"
    python "$patches/patch_elemental_blast_living_earth.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/hero/abilities/mage/ElementalBlast.java"
    python "$patches/patch_cohero_wand_types.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfFrost.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfDisintegration.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfLightning.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfPrismaticLight.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfRegrowth.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfTransfusion.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfCorruption.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfCorrosion.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfFireblast.java" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/wands/WandOfWarding.java"
    ;;

  wndgame)
    python "$patches/patch_wndgame.py" \
      "$upstream/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/windows/WndGame.java"
    ;;

  messages)
    python "$patches/patch_messages.py" \
      "$upstream/core/src/main/assets/messages/misc" \
      "$cohero/messages"
    ;;

  *)
    echo "CoHero integration: unknown phase '$phase' (expected base, wndgame, or messages)" >&2
    exit 2
    ;;
esac
