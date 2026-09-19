#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_highgrass.py <HighGrass.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

furrowed_old = """		if (level.map[pos] == Terrain.FURROWED_GRASS){
			if (ch instanceof Hero && ((Hero) ch).heroClass == HeroClass.HUNTRESS){
				//Do nothing
				freezeTrample = true;
			} else {
				Level.set(pos, Terrain.GRASS);
			}
"""

furrowed_new = """		if (level.map[pos] == Terrain.FURROWED_GRASS){
			if ((ch instanceof Hero && ((Hero) ch).heroClass == HeroClass.HUNTRESS)
					|| com.spd.cohero.CoHeroClassTraits.isHuntress(ch)){
				// Huntress-like grass handling: preserve furrowed grass.
				freezeTrample = true;
			} else {
				Level.set(pos, Terrain.GRASS);
			}
"""

high_old = """		} else {
			if (ch instanceof Hero && ((Hero) ch).heroClass == HeroClass.HUNTRESS){
				if(((Hero) ch).hasTalent(Talent.BARKSKIN)){
					Barkskin.conditionallyAppend(ch, (((Hero) ch).lvl* ((Hero) ch).pointsInTalent(Talent.BARKSKIN))/3, 1 );
				}
				Level.set(pos, Terrain.FURROWED_GRASS);
				freezeTrample = true;
			} else {
				Level.set(pos, Terrain.GRASS);
			}
"""

high_new = """		} else {
			if (ch instanceof Hero && ((Hero) ch).heroClass == HeroClass.HUNTRESS){
				if(((Hero) ch).hasTalent(Talent.BARKSKIN)){
					Barkskin.conditionallyAppend(ch, (((Hero) ch).lvl* ((Hero) ch).pointsInTalent(Talent.BARKSKIN))/3, 1 );
				}
				Level.set(pos, Terrain.FURROWED_GRASS);
				freezeTrample = true;
			} else if (com.spd.cohero.CoHeroClassTraits.isHuntress(ch)) {
				// CoHero is not a Hero instance: preserve the Huntress intrinsic grass behavior
				// without entering Hero-only talent code or casting the companion to Hero.
				Level.set(pos, Terrain.FURROWED_GRASS);
				freezeTrample = true;
			} else {
				Level.set(pos, Terrain.GRASS);
			}
"""

if furrowed_new in text or high_new in text:
    raise SystemExit("CoHero Huntress grass hooks are already present")

if text.count(furrowed_old) != 1:
    raise SystemExit(
        f"expected exactly one furrowed-grass Huntress anchor, found {text.count(furrowed_old)}"
    )
if text.count(high_old) != 1:
    raise SystemExit(
        f"expected exactly one high-grass Huntress anchor, found {text.count(high_old)}"
    )

text = text.replace(furrowed_old, furrowed_new, 1)
text = text.replace(high_old, high_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
