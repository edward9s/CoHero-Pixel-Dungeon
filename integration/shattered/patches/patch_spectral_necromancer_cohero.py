#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_spectral_necromancer_cohero.py <SpectralNecromancer.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

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
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one SpectralNecromancer wraith anchor, found {text.count(anchor)}"
    )

text = text.replace(anchor, patch, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
