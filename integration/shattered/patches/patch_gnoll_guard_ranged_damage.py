#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gnoll_guard_ranged_damage.py <GnollGuard.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\tif (enemy != null && !Dungeon.level.adjacent(pos, enemy.pos)){\n\t\t\treturn Random.NormalIntRange( 16, 22 );\n\t\t} else {\n\t\t\treturn Random.NormalIntRange( 6, 12 );\n\t\t}\n\t}\n"
addition = "\n\t@Override\n\tpublic int coHeroRangedDamageRoll(Char enemy) {\n\t\treturn Random.NormalIntRange(16, 22);\n\t}\n"

if "coHeroRangedDamageRoll" in text:
    raise SystemExit("CoHero ranged damage probe is already present")
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one ranged damage anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, anchor + addition, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
