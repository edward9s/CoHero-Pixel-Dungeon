#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_warding.py <WandOfWarding.java>")

path = Path(sys.argv[1])
warding = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Warding: CoHero wards have their own energy pool so Hero and CoHero do not consume each
# other's placement budget. The stock ward actor remains autonomous and allied.
ward_budget_old = """	private boolean wardAvailable = true;
	
	@Override
	public boolean tryToZap(Hero owner, int target) {
		
		int currentWardEnergy = 0;
		for (Char ch : Actor.chars()){
			if (ch instanceof Ward){
				currentWardEnergy += ((Ward) ch).tier;
			}
		}

		if (Stasis.getStasisAlly() instanceof Ward){
			currentWardEnergy += ((Ward) Stasis.getStasisAlly()).tier;
		}
		
		int maxWardEnergy = 0;
		for (Buff buff : curUser.buffs()){
			if (buff instanceof Wand.Charger){
				if (((Charger) buff).wand() instanceof WandOfWarding){
					maxWardEnergy += 2 + ((Charger) buff).wand().level();
				}
			}
		}
		
		wardAvailable = (currentWardEnergy < maxWardEnergy);
		
		Char ch = Actor.findChar(target);
		if (ch instanceof Ward){
			if (!wardAvailable && ((Ward) ch).tier <= 3){
				GLog.w( Messages.get(this, "no_more_wards"));
				return false;
			}
		} else {
			if ((currentWardEnergy + 1) > maxWardEnergy){
				GLog.w( Messages.get(this, "no_more_wards"));
				return false;
			}
		}
		
		return super.tryToZap(owner, target);
	}
"""
ward_budget_new = """	private boolean wardAvailable = true;

	private int currentWardEnergy(boolean coHeroOwned) {
		int energy = 0;
		for (Char ch : Actor.chars()) {
			if (ch instanceof Ward && ((Ward) ch).coHeroOwned() == coHeroOwned) {
				energy += ((Ward) ch).tier;
			}
		}

		if (Stasis.getStasisAlly() instanceof Ward
				&& ((Ward) Stasis.getStasisAlly()).coHeroOwned() == coHeroOwned) {
			energy += ((Ward) Stasis.getStasisAlly()).tier;
		}
		return energy;
	}

	private int maxWardEnergy(Char owner) {
		int max = 0;
		for (Buff buff : owner.buffs()) {
			if (buff instanceof Wand.Charger
					&& ((Charger) buff).wand() instanceof WandOfWarding) {
				max += 2 + ((Charger) buff).wand().level();
			}
		}
		return max;
	}

	private boolean wardBudgetAllows(Char owner, int target, boolean coHeroOwned, boolean logFailure) {
		int current = currentWardEnergy(coHeroOwned);
		int max = maxWardEnergy(owner);
		wardAvailable = current < max;

		Char ch = Actor.findChar(target);
		if (ch instanceof Ward) {
			Ward ward = (Ward) ch;
			if (ward.coHeroOwned() != coHeroOwned) {
				if (logFailure) GLog.w(Messages.get(this, "bad_location"));
				return false;
			}
			if (!wardAvailable && ward.tier <= 3) {
				if (logFailure) GLog.w(Messages.get(this, "no_more_wards"));
				return false;
			}
		} else if ((current + 1) > max) {
			if (logFailure) GLog.w(Messages.get(this, "no_more_wards"));
			return false;
		}
		return true;
	}

	@Override
	public boolean tryToZap(Hero owner, int target) {
		return wardBudgetAllows(owner, target, false, true) && super.tryToZap(owner, target);
	}

	public int coHeroCurrentWardEnergy(Char owner) {
		return currentWardEnergy(true);
	}

	public int coHeroMaxWardEnergy(Char owner) {
		return maxWardEnergy(owner);
	}

	public boolean coHeroWouldIncreaseWardEnergy(Char owner, int target) {
		if (!coHeroCanZap(owner) || !wardBudgetAllows(owner, target, true, false)) {
			return false;
		}
		Char ch = Actor.findChar(target);
		if (ch instanceof Ward) {
			Ward ward = (Ward) ch;
			return ward.coHeroOwned() && wardAvailable && ward.tier < 6;
		}
		return true;
	}

	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		if (!wardBudgetAllows(owner, target, true, false)) {
			throw new IllegalStateException("CoHero attempted an invalid ward cast");
		}
	}
"""
warding = replace_once(warding, ward_budget_old, ward_budget_new, "Warding budget")

ward_target_old = """		if (ch != null){
			if (ch instanceof Ward){
				if (wardAvailable) {
"""
ward_target_new = """		if (ch != null){
			if (ch instanceof Ward){
				if (((Ward) ch).coHeroOwned() != coHeroCasting()) {
					GLog.w(Messages.get(this, "bad_location"));
					Dungeon.level.pressCell(target);
					return;
				}
				if (wardAvailable) {
"""
warding = replace_once(warding, ward_target_old, ward_target_new, "Warding ownership target")

ward_create_old = """		} else {
			Ward ward = new Ward();
			ward.pos = target;
			ward.wandLevel = buffedLvl();
"""
ward_create_new = """		} else {
			Ward ward = new Ward();
			ward.pos = target;
			ward.wandLevel = buffedLvl();
			ward.coHeroOwned = coHeroCasting();
"""
warding = replace_once(warding, ward_create_old, ward_create_new, "Warding ownership create")

ward_fx_old = """	@Override
	public void fx(Ballistica bolt, Callback callback) {
		MagicMissile m = MagicMissile.boltFromChar(curUser.sprite.parent,
				MagicMissile.WARD,
				curUser.sprite,
				bolt.collisionPos,
				callback);
"""
ward_fx_new = """	@Override
	public void fx(Ballistica bolt, Callback callback) {
		Char user = zapUser();
		MagicMissile m = MagicMissile.boltFromChar(user.sprite.parent,
				MagicMissile.WARD,
				user.sprite,
				bolt.collisionPos,
				callback);
"""
warding = replace_once(warding, ward_fx_old, ward_fx_new, "Warding fx caster")

ward_field_old = """		public int totalZaps = 0;

		{
"""
ward_field_new = """		public int totalZaps = 0;
		private boolean coHeroOwned = false;

		public boolean coHeroOwned() {
			return coHeroOwned;
		}

		public boolean coHeroDismiss(Char owner) {
			if (!(owner instanceof com.spd.cohero.CoHeroAlly)
					|| !coHeroOwned
					|| !isAlive()
					|| !Dungeon.level.adjacent(owner.pos, pos)) {
				return false;
			}
			die(null);
			return true;
		}

		{
"""
warding = replace_once(warding, ward_field_old, ward_field_new, "Warding ownership field")

ward_key_old = """		private static final String TOTAL_ZAPS = "total_zaps";

		@Override
"""
ward_key_new = """		private static final String TOTAL_ZAPS = "total_zaps";
		private static final String COHERO_OWNED = "cohero_owned";

		@Override
"""
warding = replace_once(warding, ward_key_old, ward_key_new, "Warding ownership key")

ward_store_old = """			bundle.put(TOTAL_ZAPS, totalZaps);
		}
"""
ward_store_new = """			bundle.put(TOTAL_ZAPS, totalZaps);
			bundle.put(COHERO_OWNED, coHeroOwned);
		}
"""
warding = replace_once(warding, ward_store_old, ward_store_new, "Warding ownership store")

ward_restore_old = """			totalZaps = bundle.getInt(TOTAL_ZAPS);
		}
"""
ward_restore_new = """			totalZaps = bundle.getInt(TOTAL_ZAPS);
			coHeroOwned = bundle.getBoolean(COHERO_OWNED);
		}
"""
warding = replace_once(warding, ward_restore_old, ward_restore_new, "Warding ownership restore")


path.write_text(warding, encoding="utf-8")
print(f"patched {path}")
