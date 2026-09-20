#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 5:
    raise SystemExit(
        "usage: patch_equipment_identification.py <Weapon.java> <Armor.java> <Ring.java> <Hero.java>"
    )

weapon_path = Path(sys.argv[1])
armor_path = Path(sys.argv[2])
ring_path = Path(sys.argv[3])
hero_path = Path(sys.argv[4])

weapon = weapon_path.read_text(encoding="utf-8")
armor = armor_path.read_text(encoding="utf-8")
ring = ring_path.read_text(encoding="utf-8")
hero = hero_path.read_text(encoding="utf-8")


def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)


if "coHeroUseForIdentification()" in weapon:
    raise SystemExit("CoHero Weapon identification seam is already present")
if "coHeroUseForIdentification()" in armor:
    raise SystemExit("CoHero Armor identification seam is already present")
if "coHeroGainIdentificationExp" in ring:
    raise SystemExit("CoHero Ring identification seam is already present")
if "onHeroGainIdentificationExp(percent)" in hero:
    raise SystemExit("CoHero Hero EXP identification hook is already present")

weapon_use_old = """		if (!levelKnown && attacker == Dungeon.hero) {
			float uses = Math.min( availableUsesToID, Talent.itemIDSpeedFactor(Dungeon.hero, this) );
			availableUsesToID -= uses;
			usesLeftToID -= uses;
			if (usesLeftToID <= 0) {
				if (ShardOfOblivion.passiveIDDisabled()){
					if (usesLeftToID > -1){
						GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
					}
					setIDReady();
				} else {
					identify();
					GLog.p(Messages.get(Weapon.class, "identify"));
					Badges.validateItemLevelAquired(this);
				}
			}
		}
"""
weapon_use_new = """		if (!levelKnown && attacker == Dungeon.hero) {
			progressIdentificationUse(Talent.itemIDSpeedFactor(Dungeon.hero, this));
		}
"""
weapon = replace_once(weapon, weapon_use_old, weapon_use_new, "Weapon use identification")

weapon_exp_old = """	public void onHeroGainExp( float levelPercent, Hero hero ){
		levelPercent *= Talent.itemIDSpeedFactor(hero, this);
		if (!levelKnown && (isEquipped(hero) || this instanceof MissileWeapon)
				&& availableUsesToID <= usesToID()/2f) {
			//gains enough uses to ID over 0.5 levels
			availableUsesToID = Math.min(usesToID()/2f, availableUsesToID + levelPercent * usesToID());
		}
	}
"""
weapon_exp_new = """	private void progressIdentificationUse(float speedFactor) {
		float uses = Math.min(availableUsesToID, speedFactor);
		availableUsesToID -= uses;
		usesLeftToID -= uses;
		if (usesLeftToID <= 0) {
			if (ShardOfOblivion.passiveIDDisabled()){
				if (usesLeftToID > -1){
					GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
				}
				setIDReady();
			} else {
				identify();
				GLog.p(Messages.get(Weapon.class, "identify"));
				Badges.validateItemLevelAquired(this);
			}
		}
	}

	public void coHeroUseForIdentification() {
		if (!levelKnown) {
			progressIdentificationUse(1f);
		}
	}

	private void gainIdentificationExp(float levelPercent) {
		if (!levelKnown && availableUsesToID <= usesToID()/2f) {
			//gains enough uses to ID over 0.5 levels
			availableUsesToID = Math.min(
					usesToID()/2f,
					availableUsesToID + levelPercent * usesToID());
		}
	}

	public void onHeroGainExp( float levelPercent, Hero hero ){
		if (isEquipped(hero) || this instanceof MissileWeapon) {
			gainIdentificationExp(levelPercent * Talent.itemIDSpeedFactor(hero, this));
		}
	}

	public void coHeroGainIdentificationExp(float levelPercent) {
		gainIdentificationExp(levelPercent);
	}
"""
weapon = replace_once(weapon, weapon_exp_old, weapon_exp_new, "Weapon EXP identification")

armor_use_old = """		if (!levelKnown && defender == Dungeon.hero) {
			float uses = Math.min( availableUsesToID, Talent.itemIDSpeedFactor(Dungeon.hero, this) );
			availableUsesToID -= uses;
			usesLeftToID -= uses;
			if (usesLeftToID <= 0) {
				if (ShardOfOblivion.passiveIDDisabled()){
					if (usesLeftToID > -1){
						GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
					}
					setIDReady();
				} else {
					identify();
					GLog.p(Messages.get(Armor.class, "identify"));
					Badges.validateItemLevelAquired(this);
				}
			}
		}
"""
armor_use_new = """		if (!levelKnown && defender == Dungeon.hero) {
			progressIdentificationUse(Talent.itemIDSpeedFactor(Dungeon.hero, this));
		}
"""
armor = replace_once(armor, armor_use_old, armor_use_new, "Armor use identification")

armor_exp_old = """	@Override
	public void onHeroGainExp(float levelPercent, Hero hero) {
		levelPercent *= Talent.itemIDSpeedFactor(hero, this);
		if (!levelKnown && isEquipped(hero) && availableUsesToID <= USES_TO_ID/2f) {
			//gains enough uses to ID over 0.5 levels
			availableUsesToID = Math.min(USES_TO_ID/2f, availableUsesToID + levelPercent * USES_TO_ID);
		}
	}
"""
armor_exp_new = """	private void progressIdentificationUse(float speedFactor) {
		float uses = Math.min(availableUsesToID, speedFactor);
		availableUsesToID -= uses;
		usesLeftToID -= uses;
		if (usesLeftToID <= 0) {
			if (ShardOfOblivion.passiveIDDisabled()){
				if (usesLeftToID > -1){
					GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
				}
				setIDReady();
			} else {
				identify();
				GLog.p(Messages.get(Armor.class, "identify"));
				Badges.validateItemLevelAquired(this);
			}
		}
	}

	public void coHeroUseForIdentification() {
		if (!levelKnown) {
			progressIdentificationUse(1f);
		}
	}

	private void gainIdentificationExp(float levelPercent) {
		if (!levelKnown && availableUsesToID <= USES_TO_ID/2f) {
			//gains enough uses to ID over 0.5 levels
			availableUsesToID = Math.min(
					USES_TO_ID/2f,
					availableUsesToID + levelPercent * USES_TO_ID);
		}
	}

	@Override
	public void onHeroGainExp(float levelPercent, Hero hero) {
		if (isEquipped(hero)) {
			gainIdentificationExp(levelPercent * Talent.itemIDSpeedFactor(hero, this));
		}
	}

	public void coHeroGainIdentificationExp(float levelPercent) {
		gainIdentificationExp(levelPercent);
	}
"""
armor = replace_once(armor, armor_exp_old, armor_exp_new, "Armor EXP identification")

ring_exp_old = """	public void onHeroGainExp( float levelPercent, Hero hero ){
		if (isIdentified() || !isEquipped(hero)) return;
		levelPercent *= Talent.itemIDSpeedFactor(hero, this);
		//becomes IDed after 1 level
		levelsToID -= levelPercent;
		if (levelsToID <= 0){
			if (ShardOfOblivion.passiveIDDisabled()){
				if (levelsToID > -1){
					GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
				}
				setIDReady();
			} else {
				identify();
				GLog.p(Messages.get(Ring.class, "identify"));
				Badges.validateItemLevelAquired(this);
			}
		}
	}
"""
ring_exp_new = """	private void gainIdentificationExp(float levelPercent) {
		if (isIdentified()) return;
		//becomes IDed after 1 level
		levelsToID -= levelPercent;
		if (levelsToID <= 0){
			if (ShardOfOblivion.passiveIDDisabled()){
				if (levelsToID > -1){
					GLog.p(Messages.get(ShardOfOblivion.class, "identify_ready"), name());
				}
				setIDReady();
			} else {
				identify();
				GLog.p(Messages.get(Ring.class, "identify"));
				Badges.validateItemLevelAquired(this);
			}
		}
	}

	public void onHeroGainExp( float levelPercent, Hero hero ){
		if (!isEquipped(hero)) return;
		gainIdentificationExp(levelPercent * Talent.itemIDSpeedFactor(hero, this));
	}

	public void coHeroGainIdentificationExp(float levelPercent) {
		gainIdentificationExp(levelPercent);
	}
"""
ring = replace_once(ring, ring_exp_old, ring_exp_new, "Ring EXP identification")

hero_exp_old = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
"""
hero_exp_new = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
			com.spd.cohero.CoHero.onHeroGainIdentificationExp(percent);
"""
hero = replace_once(hero, hero_exp_old, hero_exp_new, "Hero normal EXP")

weapon_path.write_text(weapon, encoding="utf-8")
armor_path.write_text(armor, encoding="utf-8")
ring_path.write_text(ring, encoding="utf-8")
hero_path.write_text(hero, encoding="utf-8")

print(f"patched {weapon_path}")
print(f"patched {armor_path}")
print(f"patched {ring_path}")
print(f"patched {hero_path}")
