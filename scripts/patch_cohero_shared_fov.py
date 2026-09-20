#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cohero_shared_fov.py <Level.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

marker = "com.spd.cohero.CoHero.mergeCompanionFieldOfView(fieldOfView);"
if marker in text:
    raise SystemExit(f"CoHero shared FOV patch already applied to {path}")

anchor = (
    "\t\t//Currently only the hero can get mind vision or awareness\n"
    "\t\tif (c.isAlive() && c == Dungeon.hero) {\n"
)

if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one Level hero mind-vision anchor, found {text.count(anchor)}"
    )

replacement = (
    "\t\tif (c == Dungeon.hero) {\n"
    "\t\t\tcom.spd.cohero.CoHero.mergeCompanionFieldOfView(fieldOfView);\n"
    "\t\t}\n\n"
    + anchor
)

text = text.replace(anchor, replacement, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
