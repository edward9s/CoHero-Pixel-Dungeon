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

death_ankh_old = "\t\tAnkh ankh = null;\n\n\t\t//look for ankhs in player inventory, prioritize ones which are blessed.\n\t\tfor (Ankh i : belongings.getAllItems(Ankh.class)){\n\t\t\tif (ankh == null || i.isBlessed()) {\n\t\t\t\tankh = i;\n\t\t\t}\n\t\t}\n"
death_ankh_new = "\t\tAnkh ankh = null;\n\n\t\t// A final CoHero death ends the shared run. The Hero must follow the stock final-death\n\t\t// path without consuming or offering an Ankh from the Hero's own inventory.\n\t\tif (!com.spd.cohero.CoHero.companionDeathEndedRun()) {\n\t\t\t//look for ankhs in player inventory, prioritize ones which are blessed.\n\t\t\tfor (Ankh i : belongings.getAllItems(Ankh.class)){\n\t\t\t\tif (ankh == null || i.isBlessed()) {\n\t\t\t\t\tankh = i;\n\t\t\t\t}\n\t\t\t}\n\t\t}\n"

if "CoHero.companionDeathEndedRun()" in text:
    raise SystemExit("CoHero final-death Ankh gate is already present")
if text.count(death_ankh_old) != 1:
    raise SystemExit(
        f"expected exactly one Hero Ankh search anchor, found {text.count(death_ankh_old)}"
    )
text = text.replace(death_ankh_old, death_ankh_new, 1)

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
