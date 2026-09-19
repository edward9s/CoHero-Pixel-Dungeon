#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_mob_cohero.py <Mob.java> <GreatCrab.java>")

path = Path(sys.argv[1])
great_crab_path = Path(sys.argv[2])
text = path.read_text(encoding="utf-8")
great_crab = great_crab_path.read_text(encoding="utf-8")

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

	/**
	 * CoHero-only surprise semantics. This deliberately does not feed Mob.surprisedBy(), because
	 * the stock path also records Hero sneak-attack statistics and Hero-specific surprise effects.
	 */
	public boolean coHeroSurprisedBy(Char attacker) {
		return attacker instanceof com.spd.cohero.CoHeroAlly
				&& (attacker.invisible > 0
					|| !enemySeen
					|| (fieldOfView != null
						&& fieldOfView.length == Dungeon.level.length()
						&& !fieldOfView[attacker.pos]))
				&& attacker.canSurpriseAttack();
	}

"""

if "coHeroCanAttackFrom" in text:
    raise SystemExit("CoHero Mob attack probe is already present")
if text.count(anchor) != 1:
    raise SystemExit(f"expected exactly one Mob.canAttack anchor, found {text.count(anchor)}")

text = text.replace(anchor, patch, 1)

defense_anchor = """		if ( !surprisedBy(enemy)
				&& paralysed == 0
"""
defense_patch = """		if ( !surprisedBy(enemy)
				&& !coHeroSurprisedBy(enemy)
				&& paralysed == 0
"""
if text.count(defense_anchor) != 1:
    raise SystemExit(f"expected exactly one Mob defense anchor, found {text.count(defense_anchor)}")
text = text.replace(defense_anchor, defense_patch, 1)

great_crab_anchor = """		if (enemySeen
				&& state != SLEEPING
				&& paralysed == 0
				&& enemy == this.enemy
				&& enemy.invisible == 0){
"""
great_crab_patch = """		if (enemySeen
				&& state != SLEEPING
				&& paralysed == 0
				&& enemy == this.enemy
				&& enemy.invisible == 0
				&& !coHeroSurprisedBy(enemy)){
"""
if "&& !coHeroSurprisedBy(enemy))" in great_crab:
    raise SystemExit("CoHero GreatCrab surprise seam is already present")
if great_crab.count(great_crab_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GreatCrab defense anchor, found {great_crab.count(great_crab_anchor)}"
    )
great_crab = great_crab.replace(great_crab_anchor, great_crab_patch, 1)

path.write_text(text, encoding="utf-8")
great_crab_path.write_text(great_crab, encoding="utf-8")
print(f"patched {path}")
print(f"patched {great_crab_path}")
