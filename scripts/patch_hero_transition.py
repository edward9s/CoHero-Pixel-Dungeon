#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero_transition.py <Hero.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t\t\tif (Dungeon.level.activateTransition(this, transition)){"
new = (
    "\t\t\tif (com.spd.cohero.CoHero.canUseTransition(transition)\n"
    "\t\t\t\t\t&& Dungeon.level.activateTransition(this, transition)){"
)

if new in text:
    raise SystemExit("CoHero transition gate is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Hero transition anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
