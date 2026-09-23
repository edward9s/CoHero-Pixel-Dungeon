#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_elemental_blast_living_earth.py <ElementalBlast.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """						} else if (finalWandCls == WandOfLivingEarth.class && charsHit > 0){
							for (Mob m : Dungeon.level.mobs){
								if (m instanceof WandOfLivingEarth.EarthGuardian){
									((WandOfLivingEarth.EarthGuardian) m).setInfo(hero, 0, Math.round(effectMulti*charsHit*5));
									m.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + charsHit);
									break;
								}
							}
"""

new = """						} else if (finalWandCls == WandOfLivingEarth.class && charsHit > 0){
							for (Mob m : Dungeon.level.mobs){
								if (m instanceof WandOfLivingEarth.EarthGuardian
										&& ((WandOfLivingEarth.EarthGuardian) m).belongsTo(hero)){
									((WandOfLivingEarth.EarthGuardian) m).setInfo(
											hero, hero.lvl, 0, Math.round(effectMulti*charsHit*5));
									m.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + charsHit);
									break;
								}
							}
"""

if new in text:
    raise SystemExit("CoHero Living Earth ElementalBlast hook is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Living Earth ElementalBlast anchor, found {count}")

text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
