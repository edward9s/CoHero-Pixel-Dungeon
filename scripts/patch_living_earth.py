#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_living_earth.py <WandOfLivingEarth.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    text = text.replace(old, new, 1)

if "private int ownerId = -1;" in text:
    raise SystemExit("CoHero Living Earth hooks are already present")

# Make all casts use Wand.zapUser(), which is Hero for normal play and CoHero for companion casts.
replace_once(
"""	@Override
	public void onZap(Ballistica bolt) {
		Char ch = Actor.findChar(bolt.collisionPos);
		int damage = damageRoll();
		int armorToAdd = damage;

		EarthGuardian guardian = null;
		for (Mob m : Dungeon.level.mobs){
			if (m instanceof EarthGuardian){
				guardian = (EarthGuardian) m;
				break;
			}
		}

		if (Stasis.getStasisAlly() instanceof EarthGuardian){
			guardian = (EarthGuardian)Stasis.getStasisAlly();
		}

		RockArmor buff = curUser.buff(RockArmor.class);
""",
"""	@Override
	public void onZap(Ballistica bolt) {
		Char user = zapUser();
		Char ch = Actor.findChar(bolt.collisionPos);
		int damage = damageRoll();
		int armorToAdd = damage;

		EarthGuardian guardian = guardianFor(user);
		RockArmor buff = user.buff(RockArmor.class);
""",
"onZap owner lookup")

# All owner-sensitive setInfo/distance/effects inside onZap now refer to the actual caster.
text = text.replace("guardian.setInfo(curUser, buffedLvl(), armorToAdd);",
                    "guardian.setInfo(user, progressionHero().lvl, buffedLvl(), armorToAdd);")
text = text.replace("guardian.setInfo(curUser, buffedLvl(), buff.armor);",
                    "guardian.setInfo(user, progressionHero().lvl, buffedLvl(), buff.armor);")
text = text.replace("Buff.affect(curUser, RockArmor.class)",
                    "Buff.affect(user, RockArmor.class)")
text = text.replace("Dungeon.level.trueDistance(c, curUser.pos)",
                    "Dungeon.level.trueDistance(c, user.pos)")
text = text.replace("Dungeon.level.trueDistance(closest, curUser.pos)",
                    "Dungeon.level.trueDistance(closest, user.pos)")
text = text.replace("curUser.sprite.centerEmitter()",
                    "user.sprite.centerEmitter()")

# Do not let one owner's Living Earth wand damage the other owner's guardian.
foreign_guardian_anchor = """		//shooting at the guardian
		if (guardian != null && guardian == ch){
"""
foreign_guardian_patch = """		// A Hero and CoHero may each own a guardian. The other owner's guardian is friendly
		// and must never be treated as a damage target by this wand.
		if (ch instanceof EarthGuardian && ch != guardian) {
			Sample.INSTANCE.play(Assets.Sounds.HIT_MAGIC, 1, 0.9f * Random.Float(0.87f, 1.15f));
			return;
		}

		//shooting at the guardian
		if (guardian != null && guardian == ch){
"""
replace_once(foreign_guardian_anchor, foreign_guardian_patch, "foreign guardian guard")

# Guard against upstream drift: no curUser references should remain in onZap.
on_zap_start = text.index("\tpublic void onZap(Ballistica bolt)")
on_zap_end = text.index("\n\t@Override\n\tpublic String upgradeStat2", on_zap_start)
if "curUser" in text[on_zap_start:on_zap_end]:
    raise SystemExit("unpatched curUser reference remains in Living Earth onZap")

# Owner-aware guardian lookup. Stasis remains a Hero-only system.
insert_before = """	@Override
	public String upgradeStat2(int level) {
"""
helper = """	private EarthGuardian guardianFor(Char owner) {
		for (Mob m : Dungeon.level.mobs) {
			if (m instanceof EarthGuardian && ((EarthGuardian) m).belongsTo(owner)) {
				return (EarthGuardian) m;
			}
		}

		if (owner == Dungeon.hero && Stasis.getStasisAlly() instanceof EarthGuardian) {
			EarthGuardian stasisGuardian = (EarthGuardian) Stasis.getStasisAlly();
			if (stasisGuardian.belongsTo(owner)) {
				return stasisGuardian;
			}
		}

		return null;
	}

"""
replace_once(insert_before, helper + insert_before, "guardian lookup insertion")

# FX must originate from the actual caster.
replace_once(
"""	@Override
	public void fx(Ballistica bolt, Callback callback) {
		MagicMissile.boltFromChar(curUser.sprite.parent,
				MagicMissile.EARTH,
				curUser.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
"""	@Override
	public void fx(Ballistica bolt, Callback callback) {
		Char user = zapUser();
		MagicMissile.boltFromChar(user.sprite.parent,
				MagicMissile.EARTH,
				user.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
"Living Earth fx")

# Staff proc uses the actual attacker as owner. CoHero shares Dungeon.hero progression level.
replace_once(
"""	@Override
	public void onHit(MagesStaff staff, Char attacker, Char defender, int damage) {
		EarthGuardian guardian = null;
		for (Mob m : Dungeon.level.mobs){
			if (m instanceof EarthGuardian){
				guardian = (EarthGuardian) m;
				break;
			}
		}
		
		int armor = Math.round(damage*0.33f*procChanceMultiplier(attacker));

		if (guardian != null){
			guardian.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + buffedLvl() / 2);
			guardian.setInfo(Dungeon.hero, buffedLvl(), armor);
		} else {
			attacker.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + buffedLvl() / 2);
			Buff.affect(attacker, RockArmor.class).addArmor( buffedLvl(), armor);
		}
	}
""",
"""	@Override
	public void onHit(MagesStaff staff, Char attacker, Char defender, int damage) {
		EarthGuardian guardian = guardianFor(attacker);
		
		int armor = Math.round(damage*0.33f*procChanceMultiplier(attacker));

		if (guardian != null){
			guardian.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + buffedLvl() / 2);
			int ownerLevel = attacker instanceof Hero
					? ((Hero) attacker).lvl
					: (Dungeon.hero == null ? 1 : Dungeon.hero.lvl);
			guardian.setInfo(attacker, ownerLevel, buffedLvl(), armor);
		} else {
			attacker.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + buffedLvl() / 2);
			Buff.affect(attacker, RockArmor.class).addArmor( buffedLvl(), armor);
		}
	}
""",
"Living Earth staff proc")

# EarthGuardian records and persists its owner. Legacy saves without ownerId belong to Dungeon.hero.
replace_once(
"""		private int wandLevel = -1;

		public void setInfo(Hero hero, int wandLevel, int healthToAdd){
			if (wandLevel > this.wandLevel) {
				this.wandLevel = wandLevel;
				HT = 16 + 8 * wandLevel;
			}
			if (HP != 0 && sprite != null){
				sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(healthToAdd), FloatingText.HEALING);
			}
			HP = Math.min(HT, HP + healthToAdd);
			//half of hero's evasion
			defenseSkill = (hero.lvl + 4)/2;
		}
""",
"""		private int wandLevel = -1;
		private int ownerId = -1;

		// Preserve the stock public API for Hero abilities and downstream forks.
		public void setInfo(Hero hero, int wandLevel, int healthToAdd){
			setInfo(hero, hero.lvl, wandLevel, healthToAdd);
		}

		public void setInfo(Char owner, int ownerLevel, int wandLevel, int healthToAdd){
			if (owner != null) {
				ownerId = owner.id();
			}
			if (wandLevel > this.wandLevel) {
				this.wandLevel = wandLevel;
				HT = 16 + 8 * wandLevel;
			}
			if (HP != 0 && sprite != null){
				sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(healthToAdd), FloatingText.HEALING);
			}
			HP = Math.min(HT, HP + healthToAdd);
			//half of the owning hero/CoHero's level-based evasion baseline
			defenseSkill = (ownerLevel + 4)/2;
		}

		public boolean belongsTo(Char owner) {
			if (owner == null) {
				return false;
			}
			return ownerId == owner.id() || (ownerId == -1 && owner == Dungeon.hero);
		}

		private Char owner() {
			if (ownerId == -1) {
				return Dungeon.hero;
			}
			Actor actor = Actor.findById(ownerId);
			return actor instanceof Char ? (Char) actor : null;
		}
""",
"EarthGuardian owner state")

# Save ownerId.
replace_once(
"""		private static final String DEFENSE = "defense";
		private static final String WAND_LEVEL = "wand_level";

		@Override
		public void storeInBundle(Bundle bundle) {
			super.storeInBundle(bundle);
			bundle.put(DEFENSE, defenseSkill);
			bundle.put(WAND_LEVEL, wandLevel);
		}

		@Override
		public void restoreFromBundle(Bundle bundle) {
			super.restoreFromBundle(bundle);
			defenseSkill = bundle.getInt(DEFENSE);
			wandLevel = bundle.getInt(WAND_LEVEL);
		}
""",
"""		private static final String DEFENSE = "defense";
		private static final String WAND_LEVEL = "wand_level";
		private static final String OWNER_ID = "owner_id";

		@Override
		public void storeInBundle(Bundle bundle) {
			super.storeInBundle(bundle);
			bundle.put(DEFENSE, defenseSkill);
			bundle.put(WAND_LEVEL, wandLevel);
			bundle.put(OWNER_ID, ownerId);
		}

		@Override
		public void restoreFromBundle(Bundle bundle) {
			super.restoreFromBundle(bundle);
			defenseSkill = bundle.getInt(DEFENSE);
			wandLevel = bundle.getInt(WAND_LEVEL);
			ownerId = bundle.contains(OWNER_ID) ? bundle.getInt(OWNER_ID) : -1;
		}
""",
"EarthGuardian persistence")

# Return remaining guardian HP to the correct owner. PowerOfMany remains Hero-only.
replace_once(
"""			@Override
			public boolean act(boolean enemyInFOV, boolean justAlerted) {
				if (!enemyInFOV){
					Buff.affect(Dungeon.hero, RockArmor.class).addArmor(wandLevel, HP);
					if (buff(PowerOfMany.PowerBuff.class) != null){
						Buff.affect(Dungeon.hero, RockArmor.class).powerOfManyTurns = buff(PowerOfMany.PowerBuff.class).cooldown()+1;
					}
					Dungeon.hero.sprite.centerEmitter().burst(MagicMissile.EarthParticle.ATTRACT, 8 + wandLevel/2);
					destroy();
					sprite.die();
					return true;
				} else {
					return super.act(enemyInFOV, justAlerted);
				}
			}
""",
"""			@Override
			public boolean act(boolean enemyInFOV, boolean justAlerted) {
				if (!enemyInFOV){
					Char owner = owner();
					if (owner != null && owner.isAlive()) {
						Buff.affect(owner, RockArmor.class).addArmor(wandLevel, HP);
						if (owner == Dungeon.hero && buff(PowerOfMany.PowerBuff.class) != null){
							Buff.affect(owner, RockArmor.class).powerOfManyTurns =
									buff(PowerOfMany.PowerBuff.class).cooldown()+1;
						}
						if (owner.sprite != null) {
							owner.sprite.centerEmitter().burst(
									MagicMissile.EarthParticle.ATTRACT, 8 + wandLevel/2);
						}
					}
					destroy();
					sprite.die();
					return true;
				} else {
					return super.act(enemyInFOV, justAlerted);
				}
			}
""",
"EarthGuardian armor return")

# Every setInfo call should now use the owner-aware signature.
if "setInfo(curUser" in text or "setInfo(Dungeon.hero" in text:
    raise SystemExit("legacy Living Earth guardian owner call remains")

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
