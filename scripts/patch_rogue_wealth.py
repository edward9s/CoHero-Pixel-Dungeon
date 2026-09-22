#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_rogue_wealth.py <RingOfWealth.java> <Mob.java>")

ring_path = Path(sys.argv[1])
mob_path = Path(sys.argv[2])
ring = ring_path.read_text(encoding="utf-8")
mob = mob_path.read_text(encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    return text.replace(old, new, 1)


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
ring = replace_once(
    ring,
    drop_multiplier_old,
    drop_multiplier_new,
    "RingOfWealth dropChanceMultiplier",
)

bonus_old = "		int bonus = getBuffedBonus(target, Wealth.class);\n"
bonus_new = "		int bonus = wealthBonus(target);\n"
ring = replace_once(ring, bonus_old, bonus_new, "RingOfWealth bonus-drop bonus")

equip_old = "				int equipBonus = 0;\n"
equip_new = (
    "				int equipBonus = "
    "com.spd.cohero.CoHeroClassTraits.rogueWealthBonus();\n"
)
ring = replace_once(ring, equip_old, equip_new, "RingOfWealth equipment bonus")

gate_old = (
    "		if (Ring.getBuffedBonus(Dungeon.hero, "
    "RingOfWealth.Wealth.class) > 0) {\n"
)
gate_new = "		if (RingOfWealth.wealthBonus(Dungeon.hero) > 0) {\n"
mob = replace_once(mob, gate_old, gate_new, "Mob RingOfWealth bonus-drop gate")

ring_path.write_text(ring, encoding="utf-8")
mob_path.write_text(mob, encoding="utf-8")
print(f"patched {ring_path}")
print(f"patched {mob_path}")
