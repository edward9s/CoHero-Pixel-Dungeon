#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_warlock_ranged_damage.py <Warlock.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\treturn Random.NormalIntRange( 12, 18 );\n\t}\n"
addition = "\n\t@Override\n\tpublic int coHeroRangedDamageRoll(Char enemy) {\n\t\tint damage = Random.NormalIntRange(12, 18);\n\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));\n\t}\n"

if "coHeroRangedDamageRoll" in text:
    raise SystemExit("CoHero ranged damage probe is already present")
text = java_source(insert_after_code_once(text, anchor, addition, "Warlock ranged damage"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
