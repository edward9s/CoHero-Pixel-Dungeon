#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gamescene.py <GameScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
marker = "\t\tcom.spd.cohero.CoHero.onGameSceneReady();"

if marker in text:
    raise SystemExit("CoHero GameScene hook is already present")

anchor = (
    "\t\tfor (Mob mob : Dungeon.level.mobs) {\n"
    "\t\t\taddMobSprite( mob );\n"
    "\t\t}\n"
)

count = text.count(anchor)
if count != 1:
    raise SystemExit(f"expected exactly one GameScene mob-loading anchor, found {count}")

text = text.replace(anchor, anchor + "\n" + marker + "\n", 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
