#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_elemental_ranged_damage.py <Elemental.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\tif (!summonedALly) {\n\t\t\treturn Random.NormalIntRange(20, 25);\n\t\t} else {\n\t\t\tint regionScale = Math.max(2, (1 + Dungeon.scalingDepth()/5));\n\t\t\treturn Random.NormalIntRange(5*regionScale, 5 + 5*regionScale);\n\t\t}\n\t}\n"
addition = "\n\t@Override\n\tpublic int coHeroRangedDamageRoll(Char enemy) {\n\t\t// Elemental ranged attacks are effect-driven and differ by subtype.\n\t\treturn -1;\n\t}\n"

if "coHeroRangedDamageRoll" in text:
    raise SystemExit("CoHero ranged damage probe is already present")
text = java_source(insert_after_code_once(text, anchor, addition, "Elemental ranged damage"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
