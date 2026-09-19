#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gamescene.py <GameScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

ready_marker = "\t\tcom.spd.cohero.CoHero.onGameSceneReady();"
locator_marker = "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator();"
hazard_marker = "\t\tcom.spd.cohero.CoHeroHazards.warn(pos, delay);"

if ready_marker in text or locator_marker in text or hazard_marker in text:
    raise SystemExit("CoHero GameScene hooks are already present")

mob_anchor = (
    "\t\tfor (Mob mob : Dungeon.level.mobs) {\n"
    "\t\t\taddMobSprite( mob );\n"
    "\t\t}\n"
)
if text.count(mob_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene mob-loading anchor, found {text.count(mob_anchor)}")

layout_anchor = (
    "\t\tlayoutTags();\n"
    "\n"
    "\t\tswitch (InterlevelScene.mode) {\n"
)
if text.count(layout_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene HUD layout anchor, found {text.count(layout_anchor)}")

text = text.replace(mob_anchor, mob_anchor + "\n" + ready_marker + "\n", 1)

locator_block = (
    "\t\tfloat coHeroSafeLeft = insets.left + (SPDSettings.flipTags() ? Tag.SIZE : 0);\n"
    "\t\tfloat coHeroSafeRight = uiCamera.width - insets.right - (SPDSettings.flipTags() ? 0 : Tag.SIZE);\n"
    "\t\tfloat coHeroSafeTop = Math.max(menu.bottom(), boss.bottom());\n"
    "\t\tif (uiSize == 0) coHeroSafeTop = Math.max(coHeroSafeTop, status.bottom());\n"
    "\t\tfloat coHeroSafeBottom = toolbar.top();\n"
    "\t\tif (uiSize > 0) coHeroSafeBottom = Math.min(coHeroSafeBottom, status.top());\n"
    "\t\tif (inventory != null && inventory.visible) coHeroSafeBottom = Math.min(coHeroSafeBottom, inventory.top());\n"
    "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator(\n"
    "\t\t\t\tcoHeroSafeLeft, coHeroSafeTop, coHeroSafeRight, coHeroSafeBottom);\n"
    "\t\tcoHeroLocator.camera = uiCamera;\n"
    "\t\tadd(coHeroLocator);\n\n"
)
text = text.replace(
    layout_anchor,
    locator_block + layout_anchor,
    1,
)

targeted_anchor = (
    "\tpublic static TargetedCell targetedCell(int pos, float delay){\n"
)
if text.count(targeted_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene targeted-cell anchor, found {text.count(targeted_anchor)}"
    )
text = text.replace(
    targeted_anchor,
    targeted_anchor + hazard_marker + "\n",
    1,
)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
