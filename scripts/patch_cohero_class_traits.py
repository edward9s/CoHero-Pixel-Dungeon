#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cohero_class_traits.py <Hero.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

accuracy_old = "\t\taccuracy *= RingOfAccuracy.accuracyMultiplier( this );\n"
accuracy_new = accuracy_old + "\t\taccuracy *= com.spd.cohero.CoHeroClassTraits.clericAuraMultiplier(this);\n"
if text.count(accuracy_old) != 1:
    raise SystemExit(f"expected exactly one Hero accuracy anchor, found {text.count(accuracy_old)}")
text = text.replace(accuracy_old, accuracy_new, 1)

evasion_old = "\t\tevasion *= RingOfEvasion.evasionMultiplier( this );\n"
evasion_new = evasion_old + "\t\tevasion *= com.spd.cohero.CoHeroClassTraits.clericAuraMultiplier(this);\n"
if text.count(evasion_old) != 1:
    raise SystemExit(f"expected exactly one Hero evasion anchor, found {text.count(evasion_old)}")
text = text.replace(evasion_old, evasion_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
