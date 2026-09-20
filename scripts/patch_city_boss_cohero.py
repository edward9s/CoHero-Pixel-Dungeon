#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_city_boss_cohero.py <CityBossLevel.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

marker = "com.spd.cohero.CoHero.relocateCompanionForBossPhase"
if marker in text:
    raise SystemExit(f"CoHero CityBossLevel patch already applied to {path}")

anchor = (
    "\t@Override\n"
    "\tpublic void seal() {\n"
    "\t\tsuper.seal();\n"
)

if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one CityBossLevel seal anchor, found {text.count(anchor)}"
    )

replacement = (
    "\t@Override\n"
    "\tpublic void seal() {\n"
    "\t\t// CoHero is intentionally excluded from stock Mob.holdAllies(). Move it fully\n"
    "\t\t// inside the throne room before the bottom entrance door is locked. Exclude\n"
    "\t\t// arena.bottom-1 itself because that cell becomes the locked door.\n"
    "\t\tcom.spd.cohero.CoHero.relocateCompanionForBossPhase(\n"
    "\t\t\t\tnew Rect(arena.left, arena.top, arena.right, arena.bottom - 1),\n"
    "\t\t\t\tDungeon.hero.pos);\n"
    "\t\tsuper.seal();\n"
)

text = text.replace(anchor, replacement, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
