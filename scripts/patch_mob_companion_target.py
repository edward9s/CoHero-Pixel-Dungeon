#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mob_companion_target.py <Mob.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """				//and look for the hero
				if (fieldOfView[Dungeon.hero.pos] && Dungeon.hero.invisible <= 0) {
					enemies.add(Dungeon.hero);
				}
"""
new = """				//and look for both heroes
				if (fieldOfView[Dungeon.hero.pos] && Dungeon.hero.invisible <= 0) {
					enemies.add(Dungeon.hero);
				}
				com.spd.cohero.CompanionHero coHero =
						com.spd.cohero.CoHero.companionForEnemyTargeting();
				if (coHero != null && fieldOfView[coHero.pos] && coHero.invisible <= 0) {
					enemies.add(coHero);
				}
"""

if new in text:
    raise SystemExit("CoHero Mob target hook is already present")
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Mob enemy-target anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
