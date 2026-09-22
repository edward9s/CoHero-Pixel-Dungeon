#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 4:
    raise SystemExit(
        "usage: patch_cohero_ring_traits.py "
        "<RingOfWealth.java> <RingOfArcana.java> <Mob.java>"
    )

wealth_path = Path(sys.argv[1])
arcana_path = Path(sys.argv[2])
mob_path = Path(sys.argv[3])

wealth = wealth_path.read_text(encoding="utf-8")
arcana = arcana_path.read_text(encoding="utf-8")
mob = mob_path.read_text(encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    return text.replace(old, new, 1)


# Rogue intrinsic Ring of Wealth +0.
drop_multiplier_old = """	public static float dropChanceMultiplier( Char target ){
		return (float)Math.pow(1.20, getBuffedBonus(target, Wealth.class));
	}
"""
drop_multiplier_new = """	public static int wealthBonus(Char target) {
		return getBuffedBonus(target, Wealth.class)
				+ com.spd.cohero.CoHeroClassTraits.rogueWealthBonus();
	}

	public static float dropChanceMultiplier( Char target ){
		return (float)Math.pow(1.20, wealthBonus(target));
	}
"""
wealth = replace_once(
    wealth,
    drop_multiplier_old,
    drop_multiplier_new,
    "RingOfWealth dropChanceMultiplier",
)

bonus_old = "		int bonus = getBuffedBonus(target, Wealth.class);\n"
bonus_new = "		int bonus = wealthBonus(target);\n"
wealth = replace_once(wealth, bonus_old, bonus_new, "RingOfWealth bonus-drop bonus")

equip_old = "				int equipBonus = 0;\n"
equip_new = (
    "				int equipBonus = "
    "com.spd.cohero.CoHeroClassTraits.rogueWealthBonus();\n"
)
wealth = replace_once(wealth, equip_old, equip_new, "RingOfWealth equipment bonus")

gate_old = (
    "		if (Ring.getBuffedBonus(Dungeon.hero, "
    "RingOfWealth.Wealth.class) > 0) {\n"
)
gate_new = "		if (RingOfWealth.wealthBonus(Dungeon.hero) > 0) {\n"
mob = replace_once(mob, gate_old, gate_new, "Mob RingOfWealth bonus-drop gate")


# Huntress intrinsic Ring of Arcana +0. Weapon enchantments and armor glyphs both
# already use this single upstream multiplier, so keep the trait centralized here.
arcana_old = """	public static float enchantPowerMultiplier(Char target ){
		return (float)Math.pow(1.175f, getBuffedBonus(target, Arcana.class));
	}
"""
arcana_new = """	public static float enchantPowerMultiplier(Char target ){
		int bonus = getBuffedBonus(target, Arcana.class)
				+ com.spd.cohero.CoHeroClassTraits.huntressArcanaBonus(target);
		return (float)Math.pow(1.175f, bonus);
	}
"""
arcana = replace_once(
    arcana,
    arcana_old,
    arcana_new,
    "RingOfArcana enchantPowerMultiplier",
)

wealth_path.write_text(wealth, encoding="utf-8")
arcana_path.write_text(arcana, encoding="utf-8")
mob_path.write_text(mob, encoding="utf-8")
print(f"patched {wealth_path}")
print(f"patched {arcana_path}")
print(f"patched {mob_path}")
