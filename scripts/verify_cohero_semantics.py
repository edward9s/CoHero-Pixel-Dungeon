#!/usr/bin/env python3
from pathlib import Path
import re
import sys


OWNER_DECLARATION = "private final CoHeroAlly owner;"
UNLINKED_SPRITE_FACTORY = re.compile(r"\bowner\s*\.\s*sprite\s*\(")
COMPANION_DEATH_FAILURE_FLOW = """            CoHero.markCompanionDeathGameOver();
            Hero.reallyDie(cause);
            Dungeon.fail(cause);
"""
FAILURE_CLAIM_HOOK = "com.spd.cohero.CoHero.claimRunFailureSubmission()"


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
            "CoHeroAlly death must mark companion game-over, run stock Hero death cleanup, "
            "then submit Dungeon.fail exactly once through the guarded failure path.",
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

    print("CoHero semantic boundaries: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
