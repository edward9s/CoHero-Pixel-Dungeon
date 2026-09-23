#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ring_identification.py <Ring.java>")

path = Path(sys.argv[1])
ring = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)



if "coHeroGainIdentificationExp" in ring:
    raise SystemExit("CoHero Ring identification seam is already present")

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


path.write_text(ring, encoding="utf-8")
print(f"patched {path}")
