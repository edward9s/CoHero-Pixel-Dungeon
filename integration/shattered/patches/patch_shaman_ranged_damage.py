#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_shaman_ranged_damage.py <Shaman.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\treturn Random.NormalIntRange( 5, 10 );\n\t}\n"
addition = "\n\t@Override\n\tpublic int coHeroRangedDamageRoll(Char enemy) {\n\t\tint damage = Random.NormalIntRange(6, 15);\n\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));\n\t}\n"

if "coHeroRangedDamageRoll" in text:
    raise SystemExit("CoHero ranged damage probe is already present")
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one ranged damage anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, anchor + addition, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
