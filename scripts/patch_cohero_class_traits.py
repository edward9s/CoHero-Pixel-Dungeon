#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cohero_class_traits.py <Char.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

accuracy_old = "\t\tif (attacker.buff(Bless.class) != null) acuRoll *= 1.25f;\n"
accuracy_new = (
    "\t\tif (attacker.buff(Bless.class) != null\n"
    "\t\t\t\t|| com.spd.cohero.CoHeroClassTraits.isClericBlessed(attacker)) acuRoll *= 1.25f;\n"
)
if text.count(accuracy_old) != 1:
    raise SystemExit(
        f"expected exactly one Char Bless accuracy anchor, found {text.count(accuracy_old)}"
    )
text = text.replace(accuracy_old, accuracy_new, 1)

evasion_old = "\t\tif (defender.buff(Bless.class) != null) defRoll *= 1.25f;\n"
evasion_new = (
    "\t\tif (defender.buff(Bless.class) != null\n"
    "\t\t\t\t|| com.spd.cohero.CoHeroClassTraits.isClericBlessed(defender)) defRoll *= 1.25f;\n"
)
if text.count(evasion_old) != 1:
    raise SystemExit(
        f"expected exactly one Char Bless evasion anchor, found {text.count(evasion_old)}"
    )
text = text.replace(evasion_old, evasion_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
