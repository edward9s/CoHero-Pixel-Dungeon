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
PASSIVE_STATUE_FILTER = "boolean passiveStatue = mob instanceof Statue && mob.state == mob.PASSIVE;"
PIRANHA_DANGER_MASK_METHOD = "private boolean[] piranhaDangerMask() {"
PIRANHA_POOL_FLOOD = "|| !level.water[adjacent]"
PIRANHA_SHORE_EXPANSION = "int waterCount = tail;"
PIRANHA_DANGER_CELL_REUSE = "piranhaDangerCells = queue;"
PIRANHA_ESCAPE_METHOD = "Boolean tryLeavePiranhaDanger() {"
PIRANHA_TRAPPED_WAIT = 'owner.setMovementDecision("piranha_trapped", owner.pos);'
PIRANHA_SAFE_DEST = "|| !context.isPiranhaSafe(cell)"
PIRANHA_NON_WATER_DEST = "|| Dungeon.level.water[cell]"
PIRANHA_SAFE_RANGED_METHOD = "Boolean tryPiranhaSafeRangedPositioning(Mob targetMob) {"
PIRANHA_SAFE_RANGED_MASK = "boolean[] safePassable = owner.ordinarySafePassable(true);"
PIRANHA_SAFE_RANGED_SLEEP_FILTER = "targetMob.state == targetMob.SLEEPING"
PIRANHA_SAFE_RANGED_DECISION = '"piranha_safe_ranged"'
HERO_SUPPORT_BEFORE_PASSIVE_FILTER = "heroSupportCandidates.add(mob);"
AUTO_LOOT_DESTINATION_METHOD = "private PickupDestination autoPickupDestination(Item item) {"
AUTO_LOOT_RESOURCE_METHOD = "private static boolean isAutoLootResource(Item item) {"
AUTO_LOOT_DEWDROP_METHOD = "private PickupDestination dewdropDestination() {"
AUTO_LOOT_HERO_COLLECT = "item.collect(Dungeon.hero.belongings.backpack)"
AUTO_LOOT_RESOURCE_TYPES = (
    "item instanceof Runestone",
    "item instanceof Plant.Seed",
    "item instanceof Potion",
    "item instanceof Scroll",
    "item instanceof Food",
    "item instanceof Stylus",
    "item instanceof ArcaneResin",
    "item instanceof LiquidMetal",
    "item instanceof GooBlob",
    "item instanceof MetalShard",
)
AUTO_LOOT_SPECIAL_ROUTES = (
    ("item instanceof Key", "PickupDestination.KEYRING"),
    ("item instanceof EnergyCrystal", "PickupDestination.ENERGY_POOL"),
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

    turn_context_source = (package_root / "CoHeroTurnContext.java").read_text(encoding="utf-8")
    if turn_context_source.count(PASSIVE_STATUE_FILTER) != 1:
        print(
            "CoHero threat scanning must exclude only passive Statue instances from combat/support.",
            file=sys.stderr,
        )
        return 1

    support_add = turn_context_source.find(HERO_SUPPORT_BEFORE_PASSIVE_FILTER)
    passive_filter = turn_context_source.find(PASSIVE_STATUE_FILTER)
    if support_add < 0 or passive_filter < 0 or support_add > passive_filter:
        print(
            "Hero-support candidates must be collected before passive-Statue attack filtering.",
            file=sys.stderr,
        )
        return 1

    if turn_context_source.count(PIRANHA_DANGER_MASK_METHOD) != 1:
        print(
            "CoHero must build one cached Piranha danger mask per decision turn.",
            file=sys.stderr,
        )
        return 1

    if PIRANHA_POOL_FLOOD not in turn_context_source:
        print(
            "Piranha danger must flood the connected passable water body, not a fixed radius.",
            file=sys.stderr,
        )
        return 1

    if PIRANHA_SHORE_EXPANSION not in turn_context_source:
        print(
            "Piranha danger must expand from the connected water cells to the shoreline.",
            file=sys.stderr,
        )
        return 1

    if PIRANHA_DANGER_CELL_REUSE not in turn_context_source:
        print(
            "Piranha danger must reuse its primitive cell queue for later mask merges.",
            file=sys.stderr,
        )
        return 1

    navigation_source = (package_root / "CoHeroNavigation.java").read_text(encoding="utf-8")
    if PIRANHA_SAFE_DEST not in navigation_source or PIRANHA_NON_WATER_DEST not in navigation_source:
        print("Piranha destination guard missing.", file=sys.stderr)
        return 1

    if navigation_source.count(PIRANHA_ESCAPE_METHOD) != 1:
        print(
            "CoHero navigation must leave a Piranha attack zone before ordinary combat.",
            file=sys.stderr,
        )
        return 1

    if PIRANHA_TRAPPED_WAIT not in navigation_source:
        print(
            "A CoHero trapped in Piranha danger must not fall through into ordinary combat.",
            file=sys.stderr,
        )
        return 1

    if "context.maskPiranhaDanger(result);" not in navigation_source:
        print(
            "All shared movement-safety masks must reuse the cached Piranha danger mask.",
            file=sys.stderr,
        )
        return 1

    combat_source = (package_root / "CoHeroCombatController.java").read_text(encoding="utf-8")
    if combat_source.count(PIRANHA_SAFE_RANGED_METHOD) != 1:
        print(
            "CoHero combat must have one Piranha safe-ranged positioning phase.",
            file=sys.stderr,
        )
        return 1

    if (PIRANHA_SAFE_RANGED_MASK not in combat_source
            or PIRANHA_SAFE_RANGED_SLEEP_FILTER not in combat_source
            or PIRANHA_SAFE_RANGED_DECISION not in combat_source):
        print(
            "Piranha ranged positioning must reuse safe passability and ignore sleeping Piranhas.",
            file=sys.stderr,
        )
        return 1

    direct_ranged = ally_source.find("combat.tryDirectRangedAttack(")
    piranha_ranged = ally_source.find("combat.tryPiranhaSafeRangedPositioning(")
    if direct_ranged < 0 or piranha_ranged < 0 or direct_ranged > piranha_ranged:
        print(
            "Piranha firing-position search must run only after direct ranged offense is unavailable.",
            file=sys.stderr,
        )
        return 1

    loot_source = (package_root / "CoHeroLoot.java").read_text(encoding="utf-8")
    destination_start = loot_source.find(AUTO_LOOT_DESTINATION_METHOD)
    dewdrop_start = loot_source.find(AUTO_LOOT_DEWDROP_METHOD)
    resource_start = loot_source.find(AUTO_LOOT_RESOURCE_METHOD)
    hero_collect = loot_source.find(AUTO_LOOT_HERO_COLLECT)
    if destination_start < 0 or dewdrop_start < 0 or resource_start < 0 or hero_collect < 0:
        print(
            "CoHero auto-loot must keep an explicit pickup-destination policy and Hero routing path.",
            file=sys.stderr,
        )
        return 1

    destination_body = loot_source[destination_start:resource_start]
    if "owner.inventory().canUse(item)" not in destination_body:
        print(
            "CoHero auto-loot must use CompanionInventory.canUse(item) as its capability authority.",
            file=sys.stderr,
        )
        return 1
    if "owner.inventory().canAddToBackpack(item)" not in destination_body:
        print(
            "CoHero auto-loot must prefer the companion backpack only when it can accept the item.",
            file=sys.stderr,
        )
        return 1

    resource_body = loot_source[resource_start:hero_collect]
    if any(resource_type not in resource_body for resource_type in AUTO_LOOT_RESOURCE_TYPES):
        print(
            "Safe inventory resources must route to Hero when CoHero cannot use them.",
            file=sys.stderr,
        )
        return 1

    if any(
        item_check not in destination_body or destination not in destination_body
        for item_check, destination in AUTO_LOOT_SPECIAL_ROUTES
    ):
        print(
            "Keys and energy crystals must keep explicit shared-resource pickup routes.",
            file=sys.stderr,
        )
        return 1

    if "collectKey((Key) selected)" not in loot_source:
        print(
            "Key auto-loot must use the dedicated shared keyring handler.",
            file=sys.stderr,
        )
        return 1

    if "collectEnergyCrystal((EnergyCrystal) selected)" not in loot_source:
        print(
            "Energy crystal auto-loot must use the dedicated shared energy handler.",
            file=sys.stderr,
        )
        return 1

    dewdrop_body = loot_source[dewdrop_start:resource_start]
    dewdrop_requirements = (
        "owner.HP * 100 < owner.HT * 60",
        "PickupDestination.COHERO_DEW_HEAL",
        "Dungeon.hero.belongings.getItem(Waterskin.class)",
        "!waterskin.isFull()",
        "PickupDestination.HERO_WATERSKIN",
        "boolean injured = owner.HP < owner.HT",
    )
    if any(requirement not in dewdrop_body for requirement in dewdrop_requirements):
        print(
            "Dewdrop routing must heal CoHero below 60%, otherwise fill Hero Waterskin, "
            "then fall back to CoHero healing when the Waterskin cannot accept dew.",
            file=sys.stderr,
        )
        return 1

    dewdrop_handlers = (
        "consumeDewForCoHero((Dewdrop) selected)",
        "collectDewForHero((Dewdrop) selected)",
        "Catalog.countUse(Dewdrop.class)",
        "waterskin.collectDew(dew)",
    )
    if any(handler not in loot_source for handler in dewdrop_handlers):
        print(
            "Dewdrop auto-loot must keep explicit CoHero-heal and Hero-Waterskin handlers.",
            file=sys.stderr,
        )
        return 1

    print("CoHero semantic boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
