#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_necromancer_cohero.py <Necromancer.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = """	private NecroSkeleton mySkeleton;
	private int storedSkeletonID = -1;
"""

patch = anchor + """
	@Override
	public boolean coHeroDeathRemoves(Mob dependent) {
		return dependent != null
				&& (dependent == mySkeleton
					|| (storedSkeletonID != -1 && dependent.id() == storedSkeletonID));
	}
"""

if "coHeroDeathRemoves(Mob dependent)" in text:
    raise SystemExit("CoHero Necromancer dependency seam is already present")
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one Necromancer minion anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, patch, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
