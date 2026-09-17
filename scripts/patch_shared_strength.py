#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_shared_strength.py <PotionOfStrength.java> <ElixirOfMight.java>")

for raw in sys.argv[1:]:
    path = Path(raw)
    text = path.read_text(encoding="utf-8")
    old = "\t\thero.STR++;"
    new = "\t\tcom.spd.cohero.CoHero.increaseSharedStrength(hero);"

    if new in text:
        raise SystemExit(f"CoHero shared STR hook is already present in {path}")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one STR increment anchor in {path}, found {count}")

    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched {path}")
