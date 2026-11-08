#!/usr/bin/env python3
"""Allow known-cursed thrown weapons to be melted into liquid metal."""
from pathlib import Path
import sys


def main(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = ("\t\t\t\t\t&& ingredients.get(0).cursedKnown\n"
           "\t\t\t\t\t&& !ingredients.get(0).cursed;")
    new = "\t\t\t\t\t&& ingredients.get(0).cursedKnown;"
    if source.count(old) != 1:
        raise SystemExit("LiquidMetal.java: expected one curse-gate anchor")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
    print("patched LiquidMetal.java: cursed missile inputs")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_liquid_metal.py <LiquidMetal.java>")
    main(Path(sys.argv[1]))
