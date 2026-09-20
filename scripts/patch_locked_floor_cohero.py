#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_locked_floor_cohero.py <LockedFloor.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

marker = "com.spd.cohero.CoHero.relocateCompanionNextToHero"
if marker in text:
    raise SystemExit(f"CoHero LockedFloor patch already applied to {path}")

import_anchor = (
    "import com.shatteredpixel.shatteredpixeldungeon.Dungeon;\n"
)
if text.count(import_anchor) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor Dungeon import anchor, found {text.count(import_anchor)}"
    )
text = text.replace(
    import_anchor,
    import_anchor
    + "import com.shatteredpixel.shatteredpixeldungeon.actors.Char;\n",
    1,
)

act_anchor = (
    "\t@Override\n"
    "\tpublic boolean act() {\n"
)
if text.count(act_anchor) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor act anchor, found {text.count(act_anchor)}"
    )

hooks = (
    "\t@Override\n"
    "\tpublic boolean attachTo(Char target) {\n"
    "\t\tboolean attached = super.attachTo(target);\n"
    "\t\tif (attached && target == Dungeon.hero) {\n"
    "\t\t\tcom.spd.cohero.CoHero.relocateCompanionNextToHero();\n"
    "\t\t}\n"
    "\t\treturn attached;\n"
    "\t}\n\n"
    "\t@Override\n"
    "\tpublic void detach() {\n"
    "\t\tif (target == Dungeon.hero) {\n"
    "\t\t\tcom.spd.cohero.CoHero.relocateCompanionNextToHero();\n"
    "\t\t}\n"
    "\t\tsuper.detach();\n"
    "\t}\n\n"
)

text = text.replace(act_anchor, hooks + act_anchor, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
