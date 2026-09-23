#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_chasm_cohero.py <Chasm.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """	public static void mobFall( Mob mob ) {
		if (mob.isAlive()) {
			Buff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
			mob.die( Chasm.class );
		}
		
		if (mob.sprite != null) ((MobSprite)mob.sprite).fall();
	}
"""

new = """	public static void mobFall( Mob mob ) {
		if (mob.isAlive()) {
			Buff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
			mob.die( Chasm.class );
		}

		// CoHero can survive a chasm only by consuming an Ankh. In that case its own revive
		// flow has already moved it to a valid non-pit cell, so do not play the stale fall
		// animation after die() returns.
		if (mob.sprite != null
				&& !(mob instanceof com.spd.cohero.CoHeroAlly && mob.isAlive())) {
			((MobSprite)mob.sprite).fall();
		}
	}
"""

if "CoHero can survive a chasm only by consuming an Ankh" in text:
    raise SystemExit("CoHero chasm survival seam is already present")
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one Chasm.mobFall anchor, found {text.count(old)}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
