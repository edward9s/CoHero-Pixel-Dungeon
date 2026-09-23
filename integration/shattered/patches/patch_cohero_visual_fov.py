#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cohero_visual_fov.py <FogOfWar.java>")

fog_path = Path(sys.argv[1])
fog = fog_path.read_text(encoding="utf-8")

old = """			updateTexture(Dungeon.level.heroFOV, Dungeon.level.visited, Dungeon.level.mapped);
"""
new = """			updateTexture(com.spd.cohero.CoHero.renderFieldOfView(), Dungeon.level.visited, Dungeon.level.mapped);
"""

if fog.count(old) != 1:
    raise SystemExit(f"expected exactly one FogOfWar render visibility anchor, found {fog.count(old)}")

fog_path.write_text(fog.replace(old, new, 1), encoding="utf-8")
print(f"patched {fog_path}")
