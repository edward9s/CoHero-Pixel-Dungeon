#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_great_crab_cohero.py <GreatCrab.java>")

path = Path(sys.argv[1])
great_crab = path.read_text(encoding="utf-8")

great_crab_anchor = """		if (enemySeen
				&& state != SLEEPING
				&& paralysed == 0
				&& enemy == this.enemy
				&& enemy.invisible == 0){
"""
great_crab_patch = """		if (enemySeen
				&& state != SLEEPING
				&& paralysed == 0
				&& enemy == this.enemy
				&& enemy.invisible == 0
				&& !coHeroSurprisedBy(enemy)){
"""
if "&& !coHeroSurprisedBy(enemy))" in great_crab:
    raise SystemExit("CoHero GreatCrab surprise seam is already present")
if great_crab.count(great_crab_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GreatCrab defense anchor, found {great_crab.count(great_crab_anchor)}"
    )
great_crab = great_crab.replace(great_crab_anchor, great_crab_patch, 1)


path.write_text(great_crab, encoding="utf-8")
print(f"patched {path}")
