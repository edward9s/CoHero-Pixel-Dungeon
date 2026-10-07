#!/usr/bin/env python3
from pathlib import Path
import re
import sys


OWNER_DECLARATION = "private final CoHeroAlly owner;"
UNLINKED_SPRITE_FACTORY = re.compile(r"\bowner\s*\.\s*sprite\s*\(")
COMPANION_DEATH_FAILURE_FLOW = """            CoHero.markCompanionDeathGameOver();
            Dungeon.hero.die(cause);
            Dungeon.fail(rankingCause);
"""
COMPANION_RANKING_CAUSE_RESOLUTION = "Object rankingCause = rankingCause(cause);"
COMPANION_NULL_DEATH_CAUSE_FAILURE = 'throw new IllegalStateException("CoHero final death has no cause");'
COMPANION_MOB_CAUSE_UNWRAP = "Mob.class.isAssignableFrom(enclosing)"
HERO_FINAL_DEATH_ANKH_GATE = "if (!com.spd.cohero.CoHero.companionDeathEndedRun()) {"
FAILURE_CLAIM_HOOK = "com.spd.cohero.CoHero.claimRunFailureSubmission()"
COHERO_WARD_HERO_FOV_EXCLUSION = "&& ((WandOfWarding.Ward) m).coHeroOwned()) {"
COHERO_WARD_VISION_MERGE = "private boolean mergeOwnedWardVision() {"
EYE_DEATH_GAZE_TRACKING_HOOK = "public boolean coHeroDeathGazeTracks(Char target)"
AUTO_LOOT_TARGET_METHOD = "private PickupTarget autoPickupTarget(Item item) {"
AUTO_LOOT_RESOURCE_METHOD = "private static boolean isHeroRoutedResource(Item item) {"
AUTO_LOOT_HERO_COLLECT = "item.collect(Dungeon.hero.belongings.backpack)"
AUTO_LOOT_RESOURCE_TYPES = (
    "item instanceof Runestone",
    "item instanceof Plant.Seed",
    "item instanceof Potion",
    "item instanceof Scroll",
)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_cohero_semantics.py <repo-root>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    package_root = root / "core" / "src" / "main" / "java" / "com" / "spd" / "cohero"
    if not package_root.is_dir():
        print(f"missing CoHero package: {package_root}", file=sys.stderr)
        return 1

    offenders = []
    for path in sorted(package_root.glob("*.java")):
        source = path.read_text(encoding="utf-8")
        for match in UNLINKED_SPRITE_FACTORY.finditer(source):
            line = source.count("\n", 0, match.start()) + 1
            offenders.append((path.relative_to(root), line))

    if offenders:
        print(
            "CoHero-owned code must use attachedSprite() for the live CoHero sprite; "
            "owner.sprite() creates a new unlinked sprite:",
            file=sys.stderr,
        )
        for path, line in offenders:
            print(f"  {path}:{line}", file=sys.stderr)
        return 1

    ally_source = (package_root / "CoHeroAlly.java").read_text(encoding="utf-8")
    if ally_source.count(COMPANION_DEATH_FAILURE_FLOW) != 1:
        print(
            "CoHeroAlly final death must mark the shared run over, kill the Hero through "
            "Hero.die(), then submit the normalized ranking cause through Dungeon.fail exactly once.",
            file=sys.stderr,
        )
        return 1

    if ally_source.count(COMPANION_RANKING_CAUSE_RESOLUTION) != 1:
        print(
            "CoHeroAlly must resolve the ranking cause before revival/final-death handling.",
            file=sys.stderr,
        )
        return 1

    if ally_source.count(COMPANION_NULL_DEATH_CAUSE_FAILURE) != 1:
        print(
            "CoHeroAlly must fail immediately when a death arrives without a cause.",
            file=sys.stderr,
        )
        return 1

    if ally_source.count(COMPANION_MOB_CAUSE_UNWRAP) != 1:
        print(
            "CoHeroAlly must map nested mob damage-source classes back to their enclosing mob.",
            file=sys.stderr,
        )
        return 1

    hero_patch = (
        root / "integration" / "shattered" / "patches" / "patch_hero.py"
    ).read_text(encoding="utf-8")
    if hero_patch.count(HERO_FINAL_DEATH_ANKH_GATE) != 1:
        print(
            "Hero final death caused by CoHero must bypass the Hero inventory Ankh path.",
            file=sys.stderr,
        )
        return 1

    dungeon_patch = (
        root / "integration" / "shattered" / "patches" / "patch_dungeon_save.py"
    ).read_text(encoding="utf-8")
    if dungeon_patch.count(FAILURE_CLAIM_HOOK) != 1:
        print(
            "Dungeon.fail integration must claim CoHero run-failure submission exactly once.",
            file=sys.stderr,
        )
        return 1

    level_patch = (
        root / "integration" / "shattered" / "patches" / "patch_level_mobs.py"
    ).read_text(encoding="utf-8")
    if level_patch.count(COHERO_WARD_HERO_FOV_EXCLUSION) != 1:
        print(
            "Level integration must exclude CoHero-owned wards from Hero gameplay FOV.",
            file=sys.stderr,
        )
        return 1

    vision_source = (package_root / "CoHeroVision.java").read_text(encoding="utf-8")
    if vision_source.count(COHERO_WARD_VISION_MERGE) != 1:
        print(
            "CoHeroVision must merge CoHero-owned ward vision into CoHero FOV.",
            file=sys.stderr,
        )
        return 1

    eye_patch = (
        root / "integration" / "shattered" / "patches" / "patch_eye_ranged_damage.py"
    ).read_text(encoding="utf-8")
    if eye_patch.count(EYE_DEATH_GAZE_TRACKING_HOOK) != 1:
        print(
            "Eye integration must expose whether a charged Death Gaze is still tracking CoHero.",
            file=sys.stderr,
        )
        return 1

    loot_source = (package_root / "CoHeroLoot.java").read_text(encoding="utf-8")
    target_start = loot_source.find(AUTO_LOOT_TARGET_METHOD)
    resource_start = loot_source.find(AUTO_LOOT_RESOURCE_METHOD)
    hero_collect = loot_source.find(AUTO_LOOT_HERO_COLLECT)
    if target_start < 0 or resource_start < 0 or hero_collect < 0:
        print(
            "CoHero auto-loot must keep an explicit pickup-target policy and Hero routing path.",
            file=sys.stderr,
        )
        return 1

    target_body = loot_source[target_start:resource_start]
    if "owner.inventory().canUse(item)" not in target_body:
        print(
            "CoHero auto-loot must use CompanionInventory.canUse(item) as its capability authority.",
            file=sys.stderr,
        )
        return 1
    if "owner.inventory().canAddToBackpack(item)" not in target_body:
        print(
            "CoHero-usable auto-loot must respect companion backpack capacity.",
            file=sys.stderr,
        )
        return 1

    resource_body = loot_source[resource_start:hero_collect]
    if any(resource_type not in resource_body for resource_type in AUTO_LOOT_RESOURCE_TYPES):
        print(
            "Unsupported runestones, seeds, potions, and scrolls must all route to Hero during auto-loot.",
            file=sys.stderr,
        )
        return 1

    print("CoHero semantic boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
