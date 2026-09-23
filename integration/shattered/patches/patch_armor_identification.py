#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_armor_identification.py <Armor.java>")

path = Path(sys.argv[1])
armor = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)



if "coHeroUseForIdentification()" in armor:
    raise SystemExit("CoHero Armor identification seam is already present")

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


path.write_text(armor, encoding="utf-8")
print(f"patched {path}")
