#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero_transition.py <Hero.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """			if (Dungeon.level.activateTransition(this, transition)){
				curAction = null;
			} else {
				ready();
			}
"""

new = """			int coHeroTransition = com.spd.cohero.CoHero.requestTransition(this, transition);
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

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Hero transition anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
