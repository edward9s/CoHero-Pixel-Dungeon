#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 8:
    raise SystemExit(
        "usage: patch_consumable_owner.py "
        "<Scroll.java> <PotionOfHealing.java> <PotionOfDivineInspiration.java> "
        "<ScrollOfUpgrade.java> <ScrollOfRemoveCurse.java> "
        "<ScrollOfTransmutation.java> <ScrollOfMetamorphosis.java>"
    )

scroll, healing, inspiration, upgrade, remove_curse, transmutation, metamorphosis = map(Path, sys.argv[1:])

def replace_exact(path, old, new, label, expected=1):
    text = path.read_text(encoding="utf-8")
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present in {path}")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s) in {path}, found {count}")
    path.write_text(text.replace(old, new, expected), encoding="utf-8")
    print(f"patched {path}: {label}")

replace_exact(
    scroll,
    "\t\tInvisibility.dispel();",
    "\t\tInvisibility.dispel(curUser);",
    "scroll invisibility owner",
)

replace_exact(
    healing,
    """\t\tif (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
\t\t\tpharmacophobiaProc(Dungeon.hero);
""",
    """\t\tif (ch instanceof Hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
\t\t\tpharmacophobiaProc((Hero) ch);
""",
    "healing challenge owner",
)
replace_exact(
    healing,
    "\t\t\tif (ch == Dungeon.hero){",
    "\t\t\tif (ch instanceof Hero){",
    "healing message owner",
)

replace_exact(
    inspiration,
    "for (int i = 1; i <= Dungeon.hero.talents.size(); i++){",
    "for (int i = 1; i <= curUser.talents.size(); i++){",
    "divine inspiration talent owner",
)
replace_exact(
    inspiration,
    "if (Dungeon.hero.talentPointsAvailable(i) > 0){",
    "if (curUser.talentPointsAvailable(i) > 0){",
    "divine inspiration talent points owner",
)

upgrade_text = upgrade.read_text(encoding="utf-8")
if "Dungeon.hero" not in upgrade_text:
    raise SystemExit(f"expected ScrollOfUpgrade owner anchors in {upgrade}")
upgrade_count = upgrade_text.count("Dungeon.hero")
if upgrade_count != 5:
    raise SystemExit(f"expected 5 ScrollOfUpgrade Dungeon.hero anchors, found {upgrade_count}")
upgrade.write_text(upgrade_text.replace("Dungeon.hero", "curUser"), encoding="utf-8")
print(f"patched {upgrade}: upgrade owner ({upgrade_count} anchors)")

remove_text = remove_curse.read_text(encoding="utf-8")
old_remove = """\t@Override
\tprotected boolean usableOnItem(Item item) {
\t\treturn uncursable(item);
\t}

\tpublic static boolean uncursable( Item item ){
\t\tif (item.isEquipped(Dungeon.hero) && Dungeon.hero.buff(Degrade.class) != null) {
\t\t\treturn true;
"""
new_remove = """\t@Override
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
if new_remove in remove_text:
    raise SystemExit(f"CoHero remove-curse owner hook is already present in {remove_curse}")
if remove_text.count(old_remove) != 1:
    raise SystemExit(f"expected exactly one remove-curse owner anchor in {remove_curse}")
remove_curse.write_text(remove_text.replace(old_remove, new_remove, 1), encoding="utf-8")
print(f"patched {remove_curse}: remove-curse owner")

for path, expected, label in [
    (transmutation, 17, "transmutation owner"),
    (metamorphosis, 7, "metamorphosis owner"),
]:
    text = path.read_text(encoding="utf-8")
    count = text.count("Dungeon.hero")
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchors in {path}, found {count}")
    path.write_text(text.replace("Dungeon.hero", "curUser"), encoding="utf-8")
    print(f"patched {path}: {label} ({count} anchors)")
