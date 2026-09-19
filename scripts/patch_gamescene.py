#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gamescene.py <GameScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

ready_marker = "\t\tcom.spd.cohero.CoHero.onGameSceneReady();"
locator_marker = "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator();"

if ready_marker in text or locator_marker in text:
    raise SystemExit("CoHero GameScene hooks are already present")

mob_anchor = (
    "\t\tfor (Mob mob : Dungeon.level.mobs) {\n"
    "\t\t\taddMobSprite( mob );\n"
    "\t\t}\n"
)
if text.count(mob_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene mob-loading anchor, found {text.count(mob_anchor)}")

layout_anchor = "\t\tlayoutTags();\n"
if text.count(layout_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene HUD layout anchor, found {text.count(layout_anchor)}")

text = text.replace(mob_anchor, mob_anchor + "\n" + ready_marker + "\n", 1)

locator_block = (
    "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator();\n"
    "\t\tcoHeroLocator.camera = uiCamera;\n"
    "\t\tadd(coHeroLocator);\n\n"
)
text = text.replace(layout_anchor, locator_block + layout_anchor, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
