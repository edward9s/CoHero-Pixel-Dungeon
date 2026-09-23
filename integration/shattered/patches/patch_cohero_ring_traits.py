#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit(
        "usage: patch_cohero_ring_traits.py <RingOfArcana.java>"
    )

arcana_path = Path(sys.argv[1])
arcana = arcana_path.read_text(encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    return text.replace(old, new, 1)


# Huntress intrinsic Ring of Arcana +0. Weapon enchantments and armor glyphs both
# already use this single upstream multiplier, so keep the trait centralized here.
arcana_old = """\tpublic static float enchantPowerMultiplier(Char target ){
\t\treturn (float)Math.pow(1.175f, getBuffedBonus(target, Arcana.class));
\t}
"""
arcana_new = """\tpublic static float enchantPowerMultiplier(Char target ){
\t\tint bonus = getBuffedBonus(target, Arcana.class)
\t\t\t\t+ com.spd.cohero.CoHeroClassTraits.huntressArcanaBonus(target);
\t\treturn (float)Math.pow(1.175f, bonus);
\t}
"""
arcana = replace_once(
    arcana,
    arcana_old,
    arcana_new,
    "RingOfArcana enchantPowerMultiplier",
)

arcana_path.write_text(arcana, encoding="utf-8")
print(f"patched {arcana_path}")
