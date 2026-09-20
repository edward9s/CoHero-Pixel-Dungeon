#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_prison_boss_cohero.py <PrisonBossLevel.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

marker = "com.spd.cohero.CoHero.relocateCompanionForLevelRewrite"
if marker in text:
    raise SystemExit(f"CoHero PrisonBossLevel patch already applied to {path}")

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    text = text.replace(old, new, 1)

# Phase 1 rewrites most of the map and destroys every mob outside tenguCell.
replace_once(
    "\t\t\tcase FIGHT_START:\n"
    "\t\t\t\t\n"
    "\t\t\t\tclearEntities( tenguCell ); //clear anything not in tengu's cell\n",
    "\t\t\tcase FIGHT_START:\n"
    "\t\t\t\t\n"
    "\t\t\t\tcom.spd.cohero.CoHero.relocateCompanionForLevelRewrite("
    "tenguCell, Dungeon.hero.pos);\n"
    "\t\t\t\tclearEntities( tenguCell ); //clear anything not in tengu's cell\n",
    "Tengu phase-1 rewrite",
)

# Entering phase 2 clears every mob outside pauseSafeArea before replacing the map with the arena.
replace_once(
    "\t\t\tcase FIGHT_PAUSE:\n"
    "\t\t\t\t\n"
    "\t\t\t\tDungeon.hero.interrupt();\n"
    "\t\t\t\t\n"
    "\t\t\t\tclearEntities( pauseSafeArea );\n",
    "\t\t\tcase FIGHT_PAUSE:\n"
    "\t\t\t\t\n"
    "\t\t\t\tDungeon.hero.interrupt();\n"
    "\t\t\t\t\n"
    "\t\t\t\tcom.spd.cohero.CoHero.relocateCompanionForLevelRewrite("
    "pauseSafeArea, Dungeon.hero.pos);\n"
    "\t\t\t\tclearEntities( pauseSafeArea );\n",
    "Tengu phase-2 rewrite",
)

# Tengu's final transition calls unseal() before it teleports the Hero and rewrites the map.
# LockedFloor.detach therefore fires too early for the final placement. setMapEnd() also mutates
# map[] before buildFlagMaps()/cleanMapState() rebuild passable/solid, so never relocate in that
# stale-flag window. Wait until clearEntities(tenguCell) and cleanMapState() have finished.
replace_once(
    "\t\t\t\ttengu.die(Dungeon.hero);\n"
    "\t\t\t\t\n"
    "\t\t\t\tclearEntities(tenguCell);\n"
    "\t\t\t\tcleanMapState();\n",
    "\t\t\t\ttengu.die(Dungeon.hero);\n"
    "\t\t\t\t\n"
    "\t\t\t\tclearEntities(tenguCell);\n"
    "\t\t\t\tcleanMapState();\n"
    "\t\t\t\tcom.spd.cohero.CoHero.relocateCompanionNextToHero();\n",
    "Tengu final post-clean-map reunion",
)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
