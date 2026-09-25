#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_chasm_cohero.py <Chasm.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """
\tpublic static void mobFall( Mob mob ) {
\t\tif (mob.isAlive()) {
\t\t\tBuff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
\t\t\tmob.die( Chasm.class );
\t\t}
\t\t
\t\tif (mob.sprite != null) ((MobSprite)mob.sprite).fall();
\t}
"""

new = """
\tpublic static void mobFall( Mob mob ) {
\t\tif (mob instanceof com.spd.cohero.CoHeroAlly) {
\t\t\t// A CoHero chasm fall is a party fall. Reuse the stock Hero transition from
\t\t\t// the companion's actual fall cell; heroLand() keeps all landing consequences
\t\t\t// on Dungeon.hero.
\t\t\theroFall(mob.pos);
\t\t\treturn;
\t\t}

\t\tif (mob.isAlive()) {
\t\t\tBuff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
\t\t\tmob.die( Chasm.class );
\t\t}
\t\t
\t\tif (mob.sprite != null) ((MobSprite)mob.sprite).fall();
\t}
"""

if "A CoHero chasm fall is a party fall" in text:
    raise SystemExit("CoHero chasm redirect seam is already present")
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one Chasm.mobFall anchor, found {text.count(old)}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
