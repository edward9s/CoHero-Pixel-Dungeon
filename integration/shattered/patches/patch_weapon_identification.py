#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_weapon_identification.py <Weapon.java>")

path = Path(sys.argv[1])
weapon = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)



if "coHeroUseForIdentification()" in weapon:
    raise SystemExit("CoHero Weapon identification seam is already present")

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


path.write_text(weapon, encoding="utf-8")
print(f"patched {path}")
