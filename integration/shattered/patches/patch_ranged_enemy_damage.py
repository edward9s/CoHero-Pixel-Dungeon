#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ranged_enemy_damage.py <mob-java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

if "coHeroRangedDamageRoll" in text:
    raise SystemExit(f"CoHero ranged damage probe is already present in {path.name}")

specs = {
    "Shaman.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\treturn Random.NormalIntRange( 5, 10 );
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\tint damage = Random.NormalIntRange(6, 15);
\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));
\t}
""",
    ),
    "DM100.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\treturn Random.NormalIntRange( 2, 8 );
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\tint damage = Random.NormalIntRange(3, 10);
\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));
\t}
""",
    ),
    "Warlock.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\treturn Random.NormalIntRange( 12, 18 );
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\tint damage = Random.NormalIntRange(12, 18);
\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));
\t}
""",
    ),
    "Eye.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\treturn Random.NormalIntRange(20, 30);
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\tint damage = Random.NormalIntRange(30, 50);
\t\treturn Math.round(damage * AscensionChallenge.statModifier(this));
\t}
""",
    ),
    "GnollGuard.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\tif (enemy != null && !Dungeon.level.adjacent(pos, enemy.pos)){
\t\t\treturn Random.NormalIntRange( 16, 22 );
\t\t} else {
\t\t\treturn Random.NormalIntRange( 6, 12 );
\t\t}
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\treturn Random.NormalIntRange(16, 22);
\t}
""",
    ),
    "Elemental.java": (
        """\t@Override
\tpublic int damageRoll() {
\t\tif (!summonedALly) {
\t\t\treturn Random.NormalIntRange(20, 25);
\t\t} else {
\t\t\tint regionScale = Math.max(2, (1 + Dungeon.scalingDepth()/5));
\t\t\treturn Random.NormalIntRange(5*regionScale, 5 + 5*regionScale);
\t\t}
\t}
""",
        """\n\t@Override
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\t// Elemental ranged attacks are effect-driven and differ by subtype.
\t\treturn -1;
\t}
""",
    ),
}

if path.name not in specs:
    raise SystemExit(f"unsupported ranged enemy damage target: {path.name}")

anchor, addition = specs[path.name]
if text.count(anchor) != 1:
    raise SystemExit(
        f"expected exactly one ranged damage anchor in {path.name}, found {text.count(anchor)}"
    )

text = text.replace(anchor, anchor + addition, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
