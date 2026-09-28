#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_eye_ranged_damage.py <Eye.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\treturn Random.NormalIntRange(20, 30);\n\t}\n"
addition = (\n    "\n\t@Override\n\tpublic int coHeroRangedDamageRoll(Char enemy) {\n"\n    "\t\tint damage = Random.NormalIntRange(30, 50);\n"\n    "\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));\n"\n    "\t}\n"\n    "\n\tpublic int coHeroDeathGazeTarget() {\n"\n    "\t\treturn beamCharged && beamCooldown == 0 ? beamTarget : -1;\n"\n    "\t}\n"\n)\n
if "coHeroRangedDamageRoll" in text or "coHeroDeathGazeTarget" in text:
    raise SystemExit("CoHero Eye hooks are already present")
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one ranged damage anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, anchor + addition, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
