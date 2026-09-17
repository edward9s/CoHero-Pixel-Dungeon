#!/usr/bin/env python3
from pathlib import Path
import sys


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_source_layout.py <repo-root>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    java_root = root / "core" / "src" / "main" / "java"
    allowed_root = java_root / "com" / "spd" / "cohero"

    if not allowed_root.is_dir():
        print(f"missing CoHero package: {allowed_root}", file=sys.stderr)
        return 1

    offenders = []
    for path in java_root.rglob("*.java"):
        try:
            path.relative_to(allowed_root)
        except ValueError:
            offenders.append(path.relative_to(root))

    if offenders:
        print("CoHero-owned Java source must stay under com/spd/cohero:", file=sys.stderr)
        for path in offenders:
            print(f"  {path}", file=sys.stderr)
        return 1

    print("source layout: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
