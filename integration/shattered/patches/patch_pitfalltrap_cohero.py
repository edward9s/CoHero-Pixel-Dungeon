#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_pitfalltrap_cohero.py <PitfallTrap.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

state_old = """\t\t\tboolean herofell = false;
"""
state_new = """\t\t\tint heroFallPos = -1;
\t\t\tboolean heroFell = false;
\t\t\tboolean companionFell = false;
"""

fall_old = """\t\t\t\t\t\tif (ch == Dungeon.hero) {
\t\t\t\t\t\t\therofell = true;
\t\t\t\t\t\t} else {
\t\t\t\t\t\t\tChasm.mobFall((Mob) ch);
\t\t\t\t\t\t}
"""
fall_new = """\t\t\t\t\t\tif (ch == Dungeon.hero) {
\t\t\t\t\t\t\theroFell = true;
\t\t\t\t\t\t\t// If both heroes fall in the same pitfall event, the real Hero's cell wins
\t\t\t\t\t\t\t// for stock WeakFloorRoom / fallIntoPit destination semantics.
\t\t\t\t\t\t\theroFallPos = ch.pos;
\t\t\t\t\t\t} else if (ch instanceof com.spd.cohero.CoHeroAlly) {
\t\t\t\t\t\t\tcompanionFell = true;
\t\t\t\t\t\t\tif (heroFallPos == -1) {
\t\t\t\t\t\t\t\theroFallPos = ch.pos;
\t\t\t\t\t\t\t}
\t\t\t\t\t\t} else {
\t\t\t\t\t\t\tChasm.mobFall((Mob) ch);
\t\t\t\t\t\t}
"""

finish_old = """\t\t\t//process hero falling last
\t\t\tif (herofell){
\t\t\t\tChasm.heroFall(Dungeon.hero.pos);
\t\t\t}

\t\t\tdetach();
\t\t\treturn !herofell;
"""
finish_new = """\t\t\t// Process the shared floor transition last so a multi-cell pitfall changes floors
\t\t\t// exactly once, while retaining which party members actually fell for landing damage.
\t\t\tif (heroFallPos != -1){
\t\t\t\tif (companionFell) {
\t\t\t\t\tcom.spd.cohero.CoHero.markCompanionChasmFall(heroFell);
\t\t\t\t}
\t\t\t\tChasm.heroFall(heroFallPos);
\t\t\t}

\t\t\tdetach();
\t\t\treturn heroFallPos == -1;
"""

if "boolean companionFell = false;" in text:
    raise SystemExit("CoHero PitfallTrap landing-owner seam is already present")
for name, old in (("state", state_old), ("fall", fall_old), ("finish", finish_old)):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one PitfallTrap {name} anchor, found {text.count(old)}")

text = text.replace(state_old, state_new, 1)
text = text.replace(fall_old, fall_new, 1)
text = text.replace(finish_old, finish_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
