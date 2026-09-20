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

left_anchor = (
    "\tprivate float left = Dungeon.isChallenged(Challenges.STRONGER_BOSSES) ? 20 : 50;\n"
)
if text.count(left_anchor) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor left-field anchor, found {text.count(left_anchor)}"
    )
text = text.replace(
    left_anchor,
    left_anchor + "\tprivate boolean coHeroStartRelocated;\n",
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
    "\t\tif (attached && target == Dungeon.hero && !coHeroStartRelocated) {\n"
    "\t\t\tcom.spd.cohero.CoHero.relocateCompanionNextToHero();\n"
    "\t\t\tcoHeroStartRelocated = true;\n"
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

store_anchor = (
    "\tprivate final String LEFT = \"left\";\n"
)
if text.count(store_anchor) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor bundle-key anchor, found {text.count(store_anchor)}"
    )
text = text.replace(
    store_anchor,
    store_anchor
    + "\tprivate static final String COHERO_START_RELOCATED = \"cohero_start_relocated\";\n",
    1,
)

store_body = (
    "\t\tbundle.put( LEFT, left );\n"
)
if text.count(store_body) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor store anchor, found {text.count(store_body)}"
    )
text = text.replace(
    store_body,
    store_body
    + "\t\tbundle.put( COHERO_START_RELOCATED, coHeroStartRelocated );\n",
    1,
)

restore_body = (
    "\t\tleft = bundle.getFloat( LEFT );\n"
)
if text.count(restore_body) != 1:
    raise SystemExit(
        f"expected exactly one LockedFloor restore anchor, found {text.count(restore_body)}"
    )
text = text.replace(
    restore_body,
    restore_body
    + "\t\tcoHeroStartRelocated = bundle.getBoolean( COHERO_START_RELOCATED );\n",
    1,
)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
