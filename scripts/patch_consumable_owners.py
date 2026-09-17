#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_consumable_owners.py <spd-core-java-root>")

root = Path(sys.argv[1])

def patch_exact(rel, old, new, expected, label):
    path = root / rel
    text = path.read_text(encoding="utf-8")
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present in {path}")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s) in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"patched {path}")

def replace_all_hero_with_cur_user(rel, expected):
    path = root / rel
    text = path.read_text(encoding="utf-8")
    count = text.count("Dungeon.hero")
    if count != expected:
        raise SystemExit(
            f"expected {expected} Dungeon.hero owner reference(s) in {path}, found {count}"
        )
    text = text.replace("Dungeon.hero", "curUser")
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")

patch_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java",
    "Invisibility.dispel();",
    "Invisibility.dispel(curUser);",
    1,
    "Scroll invisibility owner",
)

patch_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/PotionOfHealing.java",
    """		if (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
			pharmacophobiaProc(Dungeon.hero);
""",
    """		if (ch instanceof Hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
			pharmacophobiaProc((Hero)ch);
""",
    1,
    "healing potion challenge owner",
)

patch_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/PotionOfHealing.java",
    """			if (ch == Dungeon.hero){
				GLog.p( Messages.get(PotionOfHealing.class, "heal") );
			}
""",
    """			if (ch instanceof Hero){
				GLog.p( Messages.get(PotionOfHealing.class, "heal") );
			}
""",
    1,
    "healing potion message owner",
)

replace_all_hero_with_cur_user(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/exotic/PotionOfDivineInspiration.java",
    2,
)
replace_all_hero_with_cur_user(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfUpgrade.java",
    5,
)
replace_all_hero_with_cur_user(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfRemoveCurse.java",
    2,
)
replace_all_hero_with_cur_user(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfTransmutation.java",
    17,
)
replace_all_hero_with_cur_user(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/exotic/ScrollOfMetamorphosis.java",
    7,
)
