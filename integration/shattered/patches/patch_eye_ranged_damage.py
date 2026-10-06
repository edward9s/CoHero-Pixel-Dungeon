#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_eye_ranged_damage.py <Eye.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = "\t@Override\n\tpublic int damageRoll() {\n\t\treturn Random.NormalIntRange(20, 30);\n\t}\n"
addition = (
    "\n\t@Override\n"
    "\tpublic int coHeroRangedDamageRoll(Char enemy) {\n"
    "\t\tint damage = Random.NormalIntRange(30, 50);\n"
    "\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));\n"
    "\t}\n"
    "\n\t@Override\n"
    "\tpublic boolean coHeroCanAttackFrom(int sourcePos, Char enemy) {\n"
    "\t\tif (enemy == null || !Dungeon.level.insideMap(sourcePos)) {\n"
    "\t\t\treturn false;\n"
    "\t\t}\n"
    "\n"
    "\t\tint livePos = pos;\n"
    "\t\ttry {\n"
    "\t\t\tpos = sourcePos;\n"
    "\t\t\tif (beamCooldown != 0) {\n"
    "\t\t\t\treturn super.canAttack(enemy);\n"
    "\t\t\t}\n"
    "\n"
    "\t\t\tboolean[] probeFov = new boolean[Dungeon.level.length()];\n"
    "\t\t\tDungeon.level.updateFieldOfView(this, probeFov);\n"
    "\t\t\tBallistica aim = new Ballistica(pos, enemy.pos, Ballistica.STOP_SOLID);\n"
    "\t\t\tif (enemy.invisible == 0\n"
    "\t\t\t\t\t&& !isCharmedBy(enemy)\n"
    "\t\t\t\t\t&& probeFov[enemy.pos]\n"
    "\t\t\t\t\t&& (super.canAttack(enemy)\n"
    "\t\t\t\t\t\t|| aim.subPath(1, aim.dist).contains(enemy.pos))) {\n"
    "\t\t\t\treturn true;\n"
    "\t\t\t}\n"
    "\t\t\treturn beamCharged;\n"
    "\t\t} finally {\n"
    "\t\t\tpos = livePos;\n"
    "\t\t}\n"
    "\t}\n"
    "\n\tpublic int coHeroDeathGazeTarget() {\n"
    "\t\treturn beamCharged && beamCooldown == 0 && state == HUNTING ? beamTarget : -1;\n"
    "\t}\n"
    "\n\tpublic boolean coHeroDeathGazeTracks(Char target) {\n"
    "\t\treturn beamCharged\n"
    "\t\t\t\t&& beamCooldown == 0\n"
    "\t\t\t\t&& state == HUNTING\n"
    "\t\t\t\t&& target != null\n"
    "\t\t\t\t&& enemy == target\n"
    "\t\t\t\t&& target.invisible == 0\n"
    "\t\t\t\t&& !isCharmedBy(target);\n"
    "\t}\n"
)

if ("coHeroRangedDamageRoll" in text
        or "coHeroCanAttackFrom" in text
        or "coHeroDeathGazeTarget" in text
        or "coHeroDeathGazeTracks" in text):
    raise SystemExit("CoHero Eye hooks are already present")
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one ranged damage anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, anchor + addition, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
