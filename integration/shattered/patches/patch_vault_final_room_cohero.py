#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_vault_final_room_cohero.py <VaultFinalRoom.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

marker = "com.spd.cohero.CoHero.relocateCompanionNextToHero"
if marker in text:
    raise SystemExit(f"CoHero VaultFinalRoom patch already applied to {path}")

anchor = (
    "\t\t\t\tGameScene.add(boss, 1);\n"
    "\t\t\t\t//we add a 1 turn delay, but compute FOV to prevent an opening surprise attack\n"
)
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one VaultFinalRoom boss-start anchor, found {text.count(anchor)}"
    )

text = text.replace(
    anchor,
    "\t\t\t\tGameScene.add(boss, 1);\n"
    "\t\t\t\tcom.spd.cohero.CoHero.relocateCompanionNextToHero();\n"
    "\t\t\t\t//we add a 1 turn delay, but compute FOV to prevent an opening surprise attack\n",
    1,
)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
