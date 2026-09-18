#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_consumable_owners.py <spd-core-java-root>")

root = Path(sys.argv[1])

def path(rel):
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing consumable source file: {p}")
    return p

def replace_exact(rel, old, new, label, expected=1):
    p = path(rel)
    text = p.read_text(encoding="utf-8")
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present in {p}")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s) in {p}, found {count}")
    p.write_text(text.replace(old, new, expected), encoding="utf-8")
    print(f"patched {p}: {label}")

replace_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java",
    "\t\tInvisibility.dispel();",
    "\t\tInvisibility.dispel(curUser);",
    "scroll invisibility owner",
)

replace_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/PotionOfHealing.java",
    """\t\tif (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
\t\t\tpharmacophobiaProc(Dungeon.hero);
""",
    """\t\tif (ch instanceof Hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
\t\t\tpharmacophobiaProc((Hero) ch);
""",
    "healing challenge owner",
)
replace_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/PotionOfHealing.java",
    "\t\t\tif (ch == Dungeon.hero){",
    "\t\t\tif (ch instanceof Hero){",
    "healing message owner",
)

replace_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/exotic/PotionOfDivineInspiration.java",
    "for (int i = 1; i <= Dungeon.hero.talents.size(); i++){",
    "for (int i = 1; i <= curUser.talents.size(); i++){",
    "divine inspiration talent owner",
)
replace_exact(
    "com/shatteredpixel/shatteredpixeldungeon/items/potions/exotic/PotionOfDivineInspiration.java",
    "if (Dungeon.hero.talentPointsAvailable(i) > 0){",
    "if (curUser.talentPointsAvailable(i) > 0){",
    "divine inspiration talent points owner",
)

upgrade = path("com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfUpgrade.java")
text = upgrade.read_text(encoding="utf-8")
count = text.count("Dungeon.hero")
if count != 5:
    raise SystemExit(f"expected 5 ScrollOfUpgrade owner anchors in {upgrade}, found {count}")
upgrade.write_text(text.replace("Dungeon.hero", "curUser"), encoding="utf-8")
print(f"patched {upgrade}: upgrade owner ({count} anchors)")

remove_curse = path("com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfRemoveCurse.java")
text = remove_curse.read_text(encoding="utf-8")
old = """\t@Override
\tprotected boolean usableOnItem(Item item) {
\t\treturn uncursable(item);
\t}

\tpublic static boolean uncursable( Item item ){
\t\tif (item.isEquipped(Dungeon.hero) && Dungeon.hero.buff(Degrade.class) != null) {
\t\t\treturn true;
"""
new = """\t@Override
\tprotected boolean usableOnItem(Item item) {
\t\treturn uncursable(curUser, item);
\t}

\tpublic static boolean uncursable( Item item ){
\t\treturn uncursable(Dungeon.hero, item);
\t}

\tpublic static boolean uncursable( Hero hero, Item item ){
\t\tif (hero != null && item.isEquipped(hero) && hero.buff(Degrade.class) != null) {
\t\t\treturn true;
"""
if new in text:
    raise SystemExit(f"CoHero remove-curse owner hook is already present in {remove_curse}")
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one remove-curse owner anchor in {remove_curse}")
remove_curse.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {remove_curse}: remove-curse owner")

for rel, expected, label in [
    (
        "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfTransmutation.java",
        17,
        "transmutation owner",
    ),
    (
        "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/exotic/ScrollOfMetamorphosis.java",
        7,
        "metamorphosis owner",
    ),
]:
    p = path(rel)
    text = p.read_text(encoding="utf-8")
    count = text.count("Dungeon.hero")
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchors in {p}, found {count}")
    text = text.replace("Dungeon.hero", "curUser")
    p.write_text(text, encoding="utf-8")
    print(f"patched {p}: {label} ({count} anchors)")

transmutation = path(
    "com/shatteredpixel/shatteredpixeldungeon/items/scrolls/ScrollOfTransmutation.java"
)
text = transmutation.read_text(encoding="utf-8")
old = "result.collect()"
new = "result.collect(curUser.belongings.backpack)"
count = text.count(old)
if count != 2:
    raise SystemExit(
        f"expected 2 transmutation result-collection anchors in {transmutation}, found {count}"
    )
transmutation.write_text(text.replace(old, new), encoding="utf-8")
print(f"patched {transmutation}: transmutation result owner")
