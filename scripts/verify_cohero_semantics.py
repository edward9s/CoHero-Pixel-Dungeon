#!/usr/bin/env python3
from pathlib import Path
import re
import sys


OWNER_DECLARATION = "private final CoHeroAlly owner;"
UNLINKED_SPRITE_FACTORY = re.compile(r"\bowner\s*\.\s*sprite\s*\(")
COMPANION_DEATH_FAILURE_FLOW = """            CoHero.markCompanionDeathGameOver();
            Dungeon.hero.die(cause);
            Dungeon.fail(cause);
"""
HERO_FINAL_DEATH_ANKH_GATE = "if (!com.spd.cohero.CoHero.companionDeathEndedRun()) {"
FAILURE_CLAIM_HOOK = "com.spd.cohero.CoHero.claimRunFailureSubmission()"
HERO_SPIRIT_BOW_TRANSFER_GUARD = "return !(item instanceof SpiritBow) && canAddToBackpack(item);"
HERO_TRANSFER_SELECTOR_GUARD = "return inventory.canReceiveFromHero(item);"
HERO_TRANSFER_RUNTIME_GUARD = "if (!inventory.canReceiveFromHero(item)) {"


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
            "Hero.die(), then submit Dungeon.fail exactly once through the guarded path.",
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

    inventory_source = (package_root / "CompanionInventory.java").read_text(encoding="utf-8")
    if inventory_source.count(HERO_SPIRIT_BOW_TRANSFER_GUARD) != 1:
        print(
            "Hero Spirit Bow must remain in Hero belongings so stock SnipersMark can resolve it.",
            file=sys.stderr,
        )
        return 1

    inventory_window_source = (package_root / "WndCompanionInventory.java").read_text(encoding="utf-8")
    if inventory_window_source.count(HERO_TRANSFER_SELECTOR_GUARD) != 1:
        print(
            "Hero-to-CoHero item selection must enforce the transfer ownership boundary.",
            file=sys.stderr,
        )
        return 1
    if inventory_window_source.count(HERO_TRANSFER_RUNTIME_GUARD) != 1:
        print(
            "Hero-to-CoHero transfer execution must fail fast when the ownership boundary is violated.",
            file=sys.stderr,
        )
        return 1

    print("CoHero semantic boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
