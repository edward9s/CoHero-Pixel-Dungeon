#!/usr/bin/env python3
"""Allow known-cursed wands to be melted into arcane resin."""
from pathlib import Path
import sys

from java_patch import java_source


def main(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    old = ("\t\t\t\t\t&& ingredients.get(0).cursedKnown\n"
           "\t\t\t\t\t&& !ingredients.get(0).cursed;")
    new = "\t\t\t\t\t&& ingredients.get(0).cursedKnown;"
    if source.count(old) != 1:
        raise SystemExit("ArcaneResin.java: expected one curse-gate anchor")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
    print("patched ArcaneResin.java: cursed wand inputs")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_arcane_resin.py <ArcaneResin.java>")
    main(Path(sys.argv[1]))
