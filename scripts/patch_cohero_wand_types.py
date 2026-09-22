#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 11:
    raise SystemExit(
        "usage: patch_cohero_wand_types.py "
        "<Frost> <Disintegration> <Lightning> <PrismaticLight> <Regrowth> <Transfusion> <Corruption> <Corrosion> <Fireblast> <Warding>"
    )

paths = [Path(p) for p in sys.argv[1:]]
frost, disintegration, lightning, prismatic, regrowth, transfusion, corruption, corrosion, fireblast, warding = [
    p.read_text(encoding="utf-8") for p in paths
]

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Frost: only the visual path is Hero-static.
frost = replace_once(
    frost,
    """	public void fx(Ballistica bolt, Callback callback) {
		MagicMissile.boltFromChar(curUser.sprite.parent,
				MagicMissile.FROST,
				curUser.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
    """	public void fx(Ballistica bolt, Callback callback) {
		Char user = zapUser();
		MagicMissile.boltFromChar(user.sprite.parent,
				MagicMissile.FROST,
				user.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
    "Frost fx",
)

# Disintegration: preserve the original beam, but source it from the actual caster.
disintegration = replace_once(
    disintegration,
    """		int cell = beam.path.get(Math.min(beam.dist, distance()));
		curUser.sprite.parent.add(new Beam.DeathRay(curUser.sprite.center(), DungeonTilemap.raisedTileCenterToWorld( cell )));
""",
    """		int cell = beam.path.get(Math.min(beam.dist, distance()));
		Char user = zapUser();
		user.sprite.parent.add(new Beam.DeathRay(user.sprite.center(), DungeonTilemap.raisedTileCenterToWorld( cell )));
""",
    "Disintegration fx caster",
)

# Lightning: its fx prepares combat state, so all caster references must follow the adapter context.
if "curUser" not in lightning:
    raise SystemExit("expected Lightning curUser references")
lightning = lightning.replace("curUser", "zapUser()")
lightning = replace_once(
    lightning,
    "if (n == Dungeon.hero && PathFinder.distance[i] > 1)",
    "if (n == zapUser() && PathFinder.distance[i] > 1)",
    "Lightning caster arc safety",
)
lightning = replace_once(
    lightning,
    "if (!zapUser().isAlive()) {",
    "if (!zapUser().isAlive() && zapUser() == Dungeon.hero) {",
    "Lightning Hero death handling",
)

lightning_fx_old = """	@Override
	public void fx(Ballistica bolt, Callback callback) {

		affected.clear();
		arcs.clear();

		int cell = bolt.collisionPos;

		Char ch = Actor.findChar( cell );
		if (ch != null) {
			if (ch instanceof DwarfKing){
				Statistics.qualifiedForBossChallengeBadge = false;
			}

			affected.add( ch );
			arcs.add( new Lightning.Arc(zapUser().sprite.center(), ch.sprite.center()));
			arc(ch);
		} else {
			arcs.add( new Lightning.Arc(zapUser().sprite.center(), DungeonTilemap.raisedTileCenterToWorld(bolt.collisionPos)));
			CellEmitter.center( cell ).burst( SparkParticle.FACTORY, 3 );
		}

		//don't want to wait for the effect before processing damage.
		zapUser().sprite.parent.addToFront( new Lightning( arcs, null ) );
		Sample.INSTANCE.play( Assets.Sounds.LIGHTNING );
		callback.call();
	}
"""
lightning_fx_new = """	private void prepareCoHeroZapState(Ballistica bolt) {
		affected.clear();
		arcs.clear();

		int cell = bolt.collisionPos;
		Char ch = Actor.findChar(cell);
		if (ch != null) {
			if (ch instanceof DwarfKing) {
				Statistics.qualifiedForBossChallengeBadge = false;
			}
			affected.add(ch);
			arcs.add(new Lightning.Arc(zapUser().sprite.center(), ch.sprite.center()));
			arc(ch);
		} else {
			arcs.add(new Lightning.Arc(
					zapUser().sprite.center(),
					DungeonTilemap.raisedTileCenterToWorld(bolt.collisionPos)));
		}
	}

	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		prepareCoHeroZapState(bolt);
	}

	@Override
	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroZapState(bolt);
		}

		if (Actor.findChar(bolt.collisionPos) == null) {
			CellEmitter.center(bolt.collisionPos).burst(SparkParticle.FACTORY, 3);
		}

		//don't want to wait for the effect before processing damage.
		zapUser().sprite.parent.addToFront(new Lightning(arcs, null));
		Sample.INSTANCE.play(Assets.Sounds.LIGHTNING);
		callback.call();
	}
"""
lightning = replace_once(lightning, lightning_fx_old, lightning_fx_new, "Lightning CoHero prepare/fx")

# Prismatic light: map effects are generic; only caster light/beam source was static.
if "curUser" not in prismatic:
    raise SystemExit("expected PrismaticLight curUser references")
prismatic = prismatic.replace("curUser", "zapUser()")

# Regrowth: preserve cone generation and plant logic, but prepare the target without Hero.tryToZap.
if "curUser" not in regrowth:
    raise SystemExit("expected Regrowth curUser references")
regrowth = regrowth.replace("curUser", "zapUser()")
regrowth_anchor = """	@Override
	public void onZap(Ballistica bolt) {
"""
regrowth_patch = """	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		this.target = target;
		prepareCoHeroCone(bolt);
	}

""" + regrowth_anchor
regrowth = replace_once(regrowth, regrowth_anchor, regrowth_patch, "Regrowth prepare")

regrowth_fx_old = """	public void fx(Ballistica bolt, Callback callback) {

		// 4/6/8 distance
		int maxDist = 2 + 2*chargesPerCast();

		cone = new ConeAOE( bolt,
				maxDist,
				20 + 10*chargesPerCast(),
				Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);

		//cast to cells at the tip, rather than all cells, better performance.
"""
regrowth_fx_new = """	private void prepareCoHeroCone(Ballistica bolt) {
		int maxDist = 2 + 2*chargesPerCast();
		cone = new ConeAOE(
				bolt,
				maxDist,
				20 + 10*chargesPerCast(),
				Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
	}

	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroCone(bolt);
		}

		//cast to cells at the tip, rather than all cells, better performance.
"""
regrowth = replace_once(regrowth, regrowth_fx_old, regrowth_fx_new, "Regrowth CoHero prepare/fx")

# Transfusion: CoHero may support the player Hero; otherwise preserve normal Mob semantics.
if "curUser" not in transfusion:
    raise SystemExit("expected Transfusion curUser references")
transfusion = transfusion.replace("curUser", "zapUser()")
transfusion = replace_once(
    transfusion,
    "if (ch instanceof Mob){",
    "if (ch instanceof Mob || (coHeroCasting() && ch == Dungeon.hero)){",
    "Transfusion CoHero support target",
)
transfusion = replace_once(
    transfusion,
    "if (!zapUser().isAlive()){",
    "if (!zapUser().isAlive() && zapUser() == Dungeon.hero){",
    "Transfusion Hero death handling",
)

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

# Fireblast: cone logic is generic; its visual source must use the actual caster.
if "curUser" not in fireblast:
    raise SystemExit("expected Fireblast curUser references")
fireblast = fireblast.replace("curUser", "zapUser()")

fireblast_fx_old = """	@Override
	public void fx(Ballistica bolt, Callback callback) {
		//need to perform flame spread logic here so we can determine what cells to put flames in.

		// 5/7/9 distance
		int maxDist = 3 + 2*chargesPerCast();

		cone = new ConeAOE( bolt,
				maxDist,
				30 + 20*chargesPerCast(),
				Ballistica.STOP_TARGET | Ballistica.STOP_SOLID | Ballistica.IGNORE_SOFT_SOLID);

		//cast to cells at the tip, rather than all cells, better performance.
"""
fireblast_fx_new = """	private void prepareCoHeroCone(Ballistica bolt) {
		int maxDist = 3 + 2*chargesPerCast();
		cone = new ConeAOE(
				bolt,
				maxDist,
				30 + 20*chargesPerCast(),
				Ballistica.STOP_TARGET | Ballistica.STOP_SOLID | Ballistica.IGNORE_SOFT_SOLID);
	}

	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		prepareCoHeroCone(bolt);
	}

	@Override
	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroCone(bolt);
		}

		//cast to cells at the tip, rather than all cells, better performance.
"""
fireblast = replace_once(fireblast, fireblast_fx_old, fireblast_fx_new, "Fireblast CoHero prepare/fx")

# Corrosion: gas ownership is generic; only the projectile source is Hero-static.
corrosion = replace_once(
    corrosion,
    """		MagicMissile.boltFromChar(
				curUser.sprite.parent,
				MagicMissile.CORROSION,
				curUser.sprite,
""",
    """		Char user = zapUser();
		MagicMissile.boltFromChar(
				user.sprite.parent,
				MagicMissile.CORROSION,
				user.sprite,
""",
    "Corrosion fx caster",
)

# Corruption: loot/EXP still belong to the one progression Hero, while visuals use the real caster.
corruption = replace_once(
    corruption,
    "AllyBuff.affectAndLoot(enemy, curUser, Corruption.class);",
    "AllyBuff.affectAndLoot(enemy, progressionHero(), Corruption.class);",
    "Corruption progression owner",
)
corruption = replace_once(
    corruption,
    """		MagicMissile.boltFromChar( curUser.sprite.parent,
				MagicMissile.SHADOW,
				curUser.sprite,
""",
    """		Char user = zapUser();
		MagicMissile.boltFromChar( user.sprite.parent,
				MagicMissile.SHADOW,
				user.sprite,
""",
    "Corruption fx caster",
)

corruption_anchor = """	@Override
	public void onZap(Ballistica bolt) {
"""
corruption_helper = """	public boolean coHeroPowerBeatsResistance(Mob enemy) {
		if (enemy == null || enemy.buff(Corruption.class) != null || enemy.buff(Doom.class) != null) {
			return false;
		}

		float corruptingPower = 3 + buffedLvl()/3f;
		float enemyResist;
		if (enemy instanceof Mimic || enemy instanceof Statue){
			enemyResist = 1 + Dungeon.depth;
		} else if (enemy instanceof Piranha || enemy instanceof Bee) {
			enemyResist = 1 + Dungeon.depth/2f;
		} else if (enemy instanceof Wraith) {
			enemyResist = (1f + Dungeon.scalingDepth()/4f) / 5f;
		} else if (enemy instanceof Swarm){
			enemyResist = 1 + AscensionChallenge.AscensionCorruptResist(enemy);
			if (enemyResist == 1) enemyResist = 1 + 3;
		} else {
			enemyResist = 1 + AscensionChallenge.AscensionCorruptResist(enemy);
		}

		enemyResist *= 1 + 4*Math.pow(enemy.HP/(float)enemy.HT, 2);
		for (Buff buff : enemy.buffs()){
			if (MAJOR_DEBUFFS.containsKey(buff.getClass()))         enemyResist *= (1f-MAJOR_DEBUFF_WEAKEN);
			else if (MINOR_DEBUFFS.containsKey(buff.getClass()))    enemyResist *= (1f-MINOR_DEBUFF_WEAKEN);
			else if (buff.type == Buff.buffType.NEGATIVE)           enemyResist *= (1f-MINOR_DEBUFF_WEAKEN);
		}
		return corruptingPower > enemyResist;
	}

""" + corruption_anchor
corruption = replace_once(corruption, corruption_anchor, corruption_helper, "Corruption power helper")

for path, text in zip(paths, [frost, disintegration, lightning, prismatic, regrowth, transfusion, corruption, corrosion, fireblast, warding]):
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")
