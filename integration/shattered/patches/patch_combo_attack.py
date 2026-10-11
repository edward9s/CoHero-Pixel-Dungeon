#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_combo_attack.py <Char.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

# The upstream damage site is common to the player and companion weapon attacks.
# Hook it once, after damage is applied, so misses and zero-damage hits never score.
old = "\t\t\tenemy.damage( effectiveDamage, this );\n"
new = (
    "\t\t\tint coHeroComboHpBefore = enemy.HP;\n"
    + old
    + "\t\t\tcom.spd.cohero.CoHeroCombo.onAttack(this, enemy, coHeroComboHpBefore);\n"
)
if "CoHeroCombo.onAttack(" in text:
    raise SystemExit("CoHero attack hook is already present")
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one Char attack damage site, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
