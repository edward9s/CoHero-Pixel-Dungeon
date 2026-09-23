#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero.py <Hero.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

transition_old = """			if (Dungeon.level.activateTransition(this, transition)){
				curAction = null;
			} else {
				ready();
			}
"""

transition_new = """			int coHeroTransition = com.spd.cohero.CoHero.requestTransition(this, transition);
			if (coHeroTransition == com.spd.cohero.CoHero.TRANSITION_STARTED) {
				curAction = null;
			} else {
				// blocked transitions and async choice prompts both end this stair action.
				// WndOptions is modal, so ready() here does not let the Hero move under the prompt.
				ready();
			}
"""

if "CoHero.requestTransition(this, transition)" in text:
    raise SystemExit("CoHero transition request hook is already present")
if text.count(transition_old) != 1:
    raise SystemExit(
        f"expected exactly one Hero transition anchor, found {text.count(transition_old)}"
    )
text = text.replace(transition_old, transition_new, 1)

exp_old = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
"""
exp_new = """		if (source != PotionOfExperience.class) {
			for (Item i : belongings) {
				i.onHeroGainExp(percent, this);
			}
			com.spd.cohero.CoHero.onHeroGainIdentificationExp(percent);
"""

if "onHeroGainIdentificationExp(percent)" in text:
    raise SystemExit("CoHero Hero EXP identification hook is already present")
if text.count(exp_old) != 1:
    raise SystemExit(f"expected exactly one Hero normal EXP anchor, found {text.count(exp_old)}")
text = text.replace(exp_old, exp_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
