#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import replace_code_once as replace_once

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ring_identification.py <Ring.java>")

path = Path(sys.argv[1])
ring = path.read_text(encoding="utf-8")




if "coHeroGainIdentificationExp" in ring:
    raise SystemExit("CoHero Ring identification seam is already present")

if "soloBuffedBonus(Char target)" in ring:
    raise SystemExit("CoHero Ring owner-aware buff seam is already present")

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

ring_buffed_old = """	@Override
	public int buffedLvl() {
		int lvl = super.buffedLvl();
		if (Dungeon.hero.buff(EnhancedRings.class) != null){
			lvl++;
		}
		return lvl;
	}
"""
ring_buffed_new = """	private Char activeBuffTarget() {
		if (buff == null || buff.target == null || !buff.target.buffs().contains(buff)) {
			return null;
		}
		return buff.target;
	}

	private Char buffContext() {
		Char target = activeBuffTarget();
		return target != null ? target : Dungeon.hero;
	}

	protected Char statsOwner() {
		return buffContext();
	}

	protected boolean isEquippedForStats() {
		return activeBuffTarget() != null || isEquipped(Dungeon.hero);
	}

	private int buffedLvl(Char target) {
		int lvl = super.buffedLvl();
		if (target == Dungeon.hero && target != null && target.buff(EnhancedRings.class) != null){
			lvl++;
		}
		return lvl;
	}

	@Override
	public int buffedLvl() {
		return buffedLvl(buffContext());
	}
"""
ring = replace_once(ring, ring_buffed_old, ring_buffed_new, "Ring owner-aware buffed level")

solo_buffed_old = """	//just used for ring descriptions
	public int soloBuffedBonus(){
		if (cursed){
			return Math.min( 0, Ring.this.buffedLvl()-2 );
		} else {
			return Ring.this.buffedLvl()+1;
		}
	}
"""
solo_buffed_new = """	private int soloBuffedBonus(Char target){
		if (cursed){
			return Math.min( 0, Ring.this.buffedLvl(target)-2 );
		} else {
			return Ring.this.buffedLvl(target)+1;
		}
	}

	//just used for ring descriptions
	public int soloBuffedBonus(){
		return soloBuffedBonus(buffContext());
	}
"""
ring = replace_once(ring, solo_buffed_old, solo_buffed_new, "Ring owner-aware solo buffed bonus")

combined_stats_old = """	//just used for ring descriptions
	public int combinedBonus(Hero hero){
		int bonus = 0;
		if (hero.belongings.ring() != null && hero.belongings.ring().getClass() == getClass()){
			bonus += hero.belongings.ring().soloBonus();
		}
		if (hero.belongings.misc() != null && hero.belongings.misc().getClass() == getClass()){
			bonus += ((Ring)hero.belongings.misc()).soloBonus();
		}
		return bonus;
	}

	//just used for ring descriptions
	public int combinedBuffedBonus(Hero hero){
		int bonus = 0;
		if (hero.belongings.ring() != null && hero.belongings.ring().getClass() == getClass()){
			bonus += hero.belongings.ring().soloBuffedBonus();
		}
		if (hero.belongings.misc() != null && hero.belongings.misc().getClass() == getClass()){
			bonus += ((Ring)hero.belongings.misc()).soloBuffedBonus();
		}
		return bonus;
	}
"""
combined_stats_new = combined_stats_old + """
	protected int combinedBonusForStats() {
		Char target = activeBuffTarget();
		if (target == null || target == Dungeon.hero) {
			return Dungeon.hero == null ? soloBonus() : combinedBonus(Dungeon.hero);
		}
		int bonus = 0;
		for (RingBuff ringBuff : target.buffs(buffClass)) {
			bonus += ringBuff.level();
		}
		return bonus;
	}

	protected int combinedBuffedBonusForStats() {
		Char target = activeBuffTarget();
		if (target == null || target == Dungeon.hero) {
			return Dungeon.hero == null ? soloBuffedBonus() : combinedBuffedBonus(Dungeon.hero);
		}
		int bonus = 0;
		for (RingBuff ringBuff : target.buffs(buffClass)) {
			bonus += ringBuff.buffedLvl();
		}
		return bonus;
	}
"""
ring = replace_once(ring, combined_stats_old, combined_stats_new, "Ring owner-aware combined stats")

ring_buff_old = """		public int buffedLvl(){
			return Ring.this.soloBuffedBonus();
		}
"""
ring_buff_new = """		public int buffedLvl(){
			return Ring.this.soloBuffedBonus(target);
		}
"""
ring = replace_once(ring, ring_buff_old, ring_buff_new, "RingBuff owner-aware buffed level")


path.write_text(ring, encoding="utf-8")
print(f"patched {path}")
