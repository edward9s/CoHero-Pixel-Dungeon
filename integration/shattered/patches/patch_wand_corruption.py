#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_corruption.py <WandOfCorruption.java>")

path = Path(sys.argv[1])
corruption = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

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


path.write_text(corruption, encoding="utf-8")
print(f"patched {path}")
