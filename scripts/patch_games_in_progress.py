#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_games_in_progress.py <GamesInProgress.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

set_old = """\t\tinfo.heroClass = Dungeon.hero.heroClass;
\t\tinfo.subClass = Dungeon.hero.subClass;
\t\tinfo.armorTier = Dungeon.hero.tier();
"""
set_new = set_old + """\t\tcom.spd.cohero.CoHero.populateGameInfo(info);
"""

info_old = """\t\tpublic HeroClass heroClass;
\t\tpublic HeroSubClass subClass;
\t\tpublic int armorTier;
"""
info_new = info_old + """\t\tpublic HeroClass companionClass;
\t\tpublic int companionArmorTier;
"""

if set_new in text or info_new in text:
    raise SystemExit("CoHero save-slot preview hooks are already present")

if text.count(set_old) != 1:
    raise SystemExit(f"expected exactly one GamesInProgress.set anchor, found {text.count(set_old)}")
if text.count(info_old) != 1:
    raise SystemExit(f"expected exactly one GamesInProgress.Info anchor, found {text.count(info_old)}")

text = text.replace(set_old, set_new, 1)
text = text.replace(info_old, info_new, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
