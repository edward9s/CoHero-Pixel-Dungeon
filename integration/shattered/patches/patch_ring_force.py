#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ring_force.py <RingOfForce.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = """\tpublic static int armedDamageBonus( Char ch ){
\t\treturn getBuffedBonus( ch, Force.class);
\t}
"""
addition = """
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

if "coHeroUnarmedMinDamage" in text or "coHeroUnarmedMaxDamage" in text:
    raise SystemExit("CoHero Ring of Force damage seam is already present")
text = java_source(insert_after_code_once(text, anchor, addition, "Ring of Force"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
