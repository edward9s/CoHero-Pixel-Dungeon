#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_special_weapons.py <MagesStaff.java> <SpiritBow.java>")

staff_path = Path(sys.argv[1])
bow_path = Path(sys.argv[2])

staff = staff_path.read_text(encoding="utf-8")
bow = bow_path.read_text(encoding="utf-8")

staff_anchor = """	public void applyWandChargeBuff(Char owner){
		if (wand != null){
			wand.charge(owner, STAFF_SCALE_FACTOR);
		}
	}
"""
staff_patch = staff_anchor + """
	public Wand coHeroWand() {
		if (wand != null) {
			// Match the stock AC_ZAP curse semantics without routing through execute(Hero,...).
			wand.cursed = cursed || hasCurseEnchant();
		}
		return wand;
	}
"""
if staff.count(staff_anchor) != 1:
    raise SystemExit(f"expected exactly one Mage's Staff charge anchor, found {staff.count(staff_anchor)}")
staff = staff.replace(staff_anchor, staff_patch, 1)

bow_damage_old = """	@Override
	public int damageRoll(Char owner) {
		int damage = augment.damageFactor(super.damageRoll(owner));
		
		if (owner instanceof Hero) {
			int exStr = ((Hero)owner).STR() - STRReq();
			if (exStr > 0) {
				damage += Hero.heroDamageIntRange( 0, exStr );
			}
		}
"""
bow_damage_new = """	public int coHeroMin(Char owner) {
		int sharpshooting = RingOfSharpshooting.levelDamageBonus(owner)
				+ com.spd.cohero.CoHeroClassTraits.missileLevelBonus(owner);
		int dmg = 1 + Dungeon.hero.lvl/5
				+ sharpshooting
				+ (curseInfusionBonus ? 1 + Dungeon.hero.lvl/30 : 0);
		return Math.max(0, dmg);
	}

	public int coHeroMax(Char owner) {
		int sharpshooting = RingOfSharpshooting.levelDamageBonus(owner)
				+ com.spd.cohero.CoHeroClassTraits.missileLevelBonus(owner);
		int dmg = 6 + (int)(Dungeon.hero.lvl/2.5f)
				+ 2*sharpshooting
				+ (curseInfusionBonus ? 2 + Dungeon.hero.lvl/15 : 0);
		return Math.max(0, dmg);
	}

	@Override
	public int damageRoll(Char owner) {
		int damage;
		if (owner instanceof com.spd.cohero.CoHeroAlly) {
			damage = augment.damageFactor(Random.NormalIntRange(coHeroMin(owner), coHeroMax(owner)));
			int exStr = ((com.spd.cohero.CoHeroAlly) owner).STR() - STRReq();
			if (exStr > 0) {
				damage += Random.NormalIntRange(0, exStr);
			}
		} else {
			damage = augment.damageFactor(super.damageRoll(owner));
			if (owner instanceof Hero) {
				int exStr = ((Hero)owner).STR() - STRReq();
				if (exStr > 0) {
					damage += Hero.heroDamageIntRange(0, exStr);
				}
			}
		}
"""
if bow.count(bow_damage_old) != 1:
    raise SystemExit(f"expected exactly one Spirit Bow damage anchor, found {bow.count(bow_damage_old)}")
bow = bow.replace(bow_damage_old, bow_damage_new, 1)

proc_old = """		if (attacker.buff(NaturesPower.naturesPowerTracker.class) != null && !sniperSpecial){
"""
proc_new = """		if (attacker instanceof Hero
				&& attacker.buff(NaturesPower.naturesPowerTracker.class) != null
				&& !sniperSpecial){
"""
if bow.count(proc_old) != 1:
    raise SystemExit(f"expected exactly one Spirit Bow NaturesPower proc anchor, found {bow.count(proc_old)}")
bow = bow.replace(proc_old, proc_new, 1)

speed_old = """		if (owner.buff(NaturesPower.naturesPowerTracker.class) != null){
"""
speed_new = """		if (owner instanceof Hero && owner.buff(NaturesPower.naturesPowerTracker.class) != null){
"""
if bow.count(speed_old) != 1:
    raise SystemExit(f"expected exactly one Spirit Bow NaturesPower speed anchor, found {bow.count(speed_old)}")
bow = bow.replace(speed_old, speed_new, 1)

staff_path.write_text(staff, encoding="utf-8")
bow_path.write_text(bow, encoding="utf-8")
print(f"patched {staff_path}")
print(f"patched {bow_path}")
