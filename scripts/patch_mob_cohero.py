#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mob_cohero.py <Mob.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = """	protected boolean canAttack( Char enemy ) {
		if (Dungeon.level.adjacent( pos, enemy.pos )){
			return true;
		}
		for (ChampionEnemy buff : buffs(ChampionEnemy.class)){
			if (buff.canAttackWithExtraReach( enemy )){
				return true;
			}
		}
		return false;
	}

"""

patch = anchor + """	/**
	 * CoHero tactical probe. Evaluates the mob's real overridden canAttack() semantics from a
	 * hypothetical source cell, then restores the live position immediately.
	 */
	public boolean coHeroCanAttackFrom(int sourcePos, Char enemy) {
		if (enemy == null || !Dungeon.level.insideMap(sourcePos)) {
			return false;
		}
		int livePos = pos;
		try {
			pos = sourcePos;
			return canAttack(enemy);
		} finally {
			pos = livePos;
		}
	}

"""

if "coHeroCanAttackFrom" in text:
    raise SystemExit("CoHero Mob attack probe is already present")
if text.count(anchor) != 1:
    raise SystemExit(f"expected exactly one Mob.canAttack anchor, found {text.count(anchor)}")

path.write_text(text.replace(anchor, patch, 1), encoding="utf-8")
print(f"patched {path}")
