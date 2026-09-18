#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_smm_longpress.py <ModAssassinate.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = """            for (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {\n                if (mob.sprite != null && mob.sprite.overlapsPoint(p.x, p.y)) {\n"""
new = """            for (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {\n                if (mob instanceof com.spd.cohero.CompanionHero) {\n                    continue;\n                }\n                if (mob.sprite != null && mob.sprite.overlapsPoint(p.x, p.y)) {\n"""

if new in text:
    raise SystemExit("CoHero SMM long-press guard is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one SMM mob hit-test anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
