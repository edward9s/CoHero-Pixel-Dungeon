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

	/**
	 * Sleeping AI normally enters its hostile scan only when chooseEnemy() produced a visible
	 * target. A sleeping mob can retain a stale Hero target, which makes that gate false even when
	 * CoHero is standing in its FOV. This helper repairs only that gate; the stock Sleeping logic
	 * still owns stealth, invisibility, flying, distance, RNG and the actual awaken transition.
	 */
	private boolean coHeroHostileInFOV() {
		if (fieldOfView == null) {
			return false;
		}
		for (Char ch : Actor.chars()) {
			if (ch instanceof com.spd.cohero.CoHeroAlly
					&& ch.isAlive()
					&& ch.invisible <= 0
					&& ch.alignment != alignment
					&& ch.alignment != Alignment.NEUTRAL
					&& ch.pos >= 0
					&& ch.pos < fieldOfView.length
					&& fieldOfView[ch.pos]) {
				return true;
			}
		}
		return false;
	}

"""

if "coHeroCanAttackFrom" in text:
    raise SystemExit("CoHero Mob attack probe is already present")
if text.count(anchor) != 1:
    raise SystemExit(f"expected exactly one Mob.canAttack anchor, found {text.count(anchor)}")

text = text.replace(anchor, patch, 1)


sleep_anchor = """			if (enemyInFOV || (enemy != null && enemy.invisible > 0)) {
"""
sleep_patch = """			if (enemyInFOV
					|| (enemy != null && enemy.invisible > 0)
					|| coHeroHostileInFOV()) {
"""
if "|| coHeroHostileInFOV())" in text:
    raise SystemExit("CoHero sleeping wake gate is already present")
if text.count(sleep_anchor) != 1:
    raise SystemExit(f"expected exactly one Mob.Sleeping wake gate, found {text.count(sleep_anchor)}")
text = text.replace(sleep_anchor, sleep_patch, 1)

sleep_selection_anchor = """			if (enemyInFOV
					|| (enemy != null && enemy.invisible > 0)
					|| coHeroHostileInFOV()) {

				float highestChance = Float.POSITIVE_INFINITY;
				Char closestHostile = null;

				for (Char ch : Actor.chars()){
					if (fieldOfView[ch.pos] && ch.invisible == 0 && ch.alignment != alignment && ch.alignment != Alignment.NEUTRAL){
						float bestChance = detectionChance(ch);
						//silent steps rogue talent, which also applies to rogue's shadow clone
						if ((ch instanceof Hero || ch instanceof ShadowClone.ShadowAlly)
								&& Dungeon.hero.hasTalent(Talent.SILENT_STEPS)){
							if (distance(ch) >= 4 - Dungeon.hero.pointsInTalent(Talent.SILENT_STEPS)) {
								bestChance = Float.POSITIVE_INFINITY;
							}
						}
						//flying characters are naturally stealthy
						if (ch.flying && distance(ch) >= 2){
							bestChance = Float.POSITIVE_INFINITY;
						}
						if (bestChance < highestChance){
							highestChance = bestChance;
							closestHostile = ch;
						}
					}
				}

				if (closestHostile != null && Random.Float() < detectionChance(closestHostile)) {
"""
sleep_selection_patch = """			if (enemyInFOV
					|| (enemy != null && enemy.invisible > 0)
					|| coHeroHostileInFOV()) {

				float highestChance = 0f;
				Char easiestHostileToDetect = null;

				for (Char ch : Actor.chars()){
					if (fieldOfView[ch.pos] && ch.invisible == 0 && ch.alignment != alignment && ch.alignment != Alignment.NEUTRAL){
						float bestChance = detectionChance(ch);
						//silent steps rogue talent, which also applies to rogue's shadow clone
						if ((ch instanceof Hero || ch instanceof ShadowClone.ShadowAlly)
								&& Dungeon.hero.hasTalent(Talent.SILENT_STEPS)){
							if (distance(ch) >= 4 - Dungeon.hero.pointsInTalent(Talent.SILENT_STEPS)) {
								bestChance = 0f;
							}
						}
						//flying characters are naturally stealthy
						if (ch.flying && distance(ch) >= 2){
							bestChance = 0f;
						}
						if (bestChance > highestChance){
							highestChance = bestChance;
							easiestHostileToDetect = ch;
						}
					}
				}

				if (easiestHostileToDetect != null && Random.Float() < highestChance) {
"""
if "Char easiestHostileToDetect = null;" in text:
    raise SystemExit("CoHero sleeping hostile selection fix is already present")
if text.count(sleep_selection_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Mob.Sleeping hostile selection block, found {text.count(sleep_selection_anchor)}"
    )
text = text.replace(sleep_selection_anchor, sleep_selection_patch, 1)

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

hold_anchor = """		for (Mob mob : level.mobs.toArray( new Mob[0] )) {
			//preserve directable allies or empowered intelligent allies no matter where they are
"""
hold_patch = """		for (Mob mob : level.mobs.toArray( new Mob[0] )) {
			// CoHero owns its own cross-floor lifecycle/state and must never enter the stock
			// heldAllies transport, otherwise special-floor exclusion can be bypassed.
			if (mob instanceof com.spd.cohero.CoHeroAlly) {
				continue;
			}
			//preserve directable allies or empowered intelligent allies no matter where they are
"""
if text.count(hold_anchor) != 1:
    raise SystemExit(f"expected exactly one Mob.holdAllies anchor, found {text.count(hold_anchor)}")
text = text.replace(hold_anchor, hold_patch, 1)

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
