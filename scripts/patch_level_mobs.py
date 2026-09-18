#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_level_mobs.py <Level.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t\tbundle.put( MOBS, mobs );"
new = "\t\tcom.spd.cohero.CoHero.storeLevelMobs(bundle, MOBS, mobs);"

if new in text:
    raise SystemExit("CoHero level mob save hook is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Level mob save anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
