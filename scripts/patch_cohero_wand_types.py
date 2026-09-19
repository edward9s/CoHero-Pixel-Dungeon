#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 8:
    raise SystemExit(
        "usage: patch_cohero_wand_types.py "
        "<Frost> <Disintegration> <Lightning> <PrismaticLight> <Regrowth> <Transfusion> <Corruption>"
    )

paths = [Path(p) for p in sys.argv[1:]]
frost, disintegration, lightning, prismatic, regrowth, transfusion, corruption = [
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
	}

""" + regrowth_anchor
regrowth = replace_once(regrowth, regrowth_anchor, regrowth_patch, "Regrowth prepare")

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

for path, text in zip(paths, [frost, disintegration, lightning, prismatic, regrowth, transfusion, corruption]):
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")
