#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_spectral_necromancer_cohero.py <SpectralNecromancer.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = """	private ArrayList<Integer> wraithIDs = new ArrayList<>();
"""

patch = anchor + """
	@Override
	public boolean coHeroDeathRemoves(Mob dependent) {
		return super.coHeroDeathRemoves(dependent)
				|| (dependent instanceof Wraith && wraithIDs.contains(dependent.id()));
	}
"""

if "coHeroDeathRemoves(Mob dependent)" in text:
    raise SystemExit("CoHero SpectralNecromancer dependency seam is already present")
addition = patch[len(anchor):]
text = java_source(insert_after_code_once(
    text, anchor, addition, "SpectralNecromancer dependency seam"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
