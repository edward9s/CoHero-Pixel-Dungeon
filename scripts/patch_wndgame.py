#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wndgame.py <WndGame.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t\tif (Dungeon.hero == null || !Dungeon.hero.isAlive()) {"
new = (
    "\t\tif (Dungeon.hero == null || !Dungeon.hero.isAlive()\n"
    "\t\t\t\t|| com.spd.cohero.CoHero.companionDeathEndedRun()) {"
)

if new in text:
    raise SystemExit("CoHero WndGame restart hook is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one WndGame restart anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
