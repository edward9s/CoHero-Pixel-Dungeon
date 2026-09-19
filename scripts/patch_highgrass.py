#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_highgrass.py <HighGrass.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = "ch instanceof Hero && ((Hero) ch).heroClass == HeroClass.HUNTRESS"
new = "(" + old + ") || com.spd.cohero.CoHeroClassTraits.isHuntress(ch)"

count = text.count(old)
if count != 2:
    raise SystemExit(f"expected exactly two Huntress grass anchors, found {count}")

text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
