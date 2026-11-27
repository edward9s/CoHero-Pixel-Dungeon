#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ring_force.py <RingOfForce.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = """\tpublic static int armedDamageBonus( Char ch, KindOfWeapon wep ){
\t\tint level = getBuffedBonus( ch, Force.class );
\t\tint min = level;
\t\tint max = level;
\t\t//scales from 1-1 to 1-3 bonus dmg per lvl, based on weapon tier
\t\tif (wep instanceof MeleeWeapon){
\t\t\tmax = (int)(level * (((MeleeWeapon) wep).tier+1)/2f);
\t\t} else if (wep instanceof MissileWeapon){
\t\t\tmax = (int)(level * (((MissileWeapon) wep).tier+1)/2f);
\t\t}

\t\tif (max >= min) {
\t\t\treturn Random.NormalIntRange(min, max);
\t\t} else {
\t\t\t//cursed, negative numbers, so max is lower
\t\t\treturn Random.NormalIntRange(max, min);
\t\t}
\t}
"""
addition = """
\tpublic static float coHeroArmedAverageDamage(Char ch, MeleeWeapon weapon) {
\t\tint level = getBuffedBonus(ch, Force.class);
\t\tint max = (int)(level * (weapon.tier+1)/2f);
\t\treturn (level + max) / 2f;
\t}

\tpublic static int coHeroUnarmedMinDamage(Char ch, int strength) {
\t\tif (ch == null || ch.buff(Force.class) == null) {
\t\t\tthrow new IllegalArgumentException("CoHero unarmed force damage requires RingOfForce.Force");
\t\t}
\t\treturn min(getBuffedBonus(ch, Force.class), tier(strength));
\t}

\tpublic static int coHeroUnarmedMaxDamage(Char ch, int strength) {
\t\tif (ch == null || ch.buff(Force.class) == null) {
\t\t\tthrow new IllegalArgumentException("CoHero unarmed force damage requires RingOfForce.Force");
\t\t}
\t\treturn max(getBuffedBonus(ch, Force.class), tier(strength));
\t}
"""

if any(name in text for name in ("coHeroArmedAverageDamage", "coHeroUnarmedMinDamage", "coHeroUnarmedMaxDamage")):
    raise SystemExit("CoHero Ring of Force damage seam is already present")
text = java_source(insert_after_code_once(text, anchor, addition, "Ring of Force"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
