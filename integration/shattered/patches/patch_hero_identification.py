#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero_identification.py <Hero.java>")

path = Path(sys.argv[1])
hero = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)



if "onHeroGainIdentificationExp(percent)" in hero:
    raise SystemExit("CoHero Hero EXP identification hook is already present")

hero_exp_old = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
"""
hero_exp_new = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
			com.spd.cohero.CoHero.onHeroGainIdentificationExp(percent);
"""
hero = replace_once(hero, hero_exp_old, hero_exp_new, "Hero normal EXP")


path.write_text(hero, encoding="utf-8")
print(f"patched {path}")
