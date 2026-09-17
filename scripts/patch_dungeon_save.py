#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_dungeon_save.py <Dungeon.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

save_old = "\t\t\tbundle.put( HERO, hero );\n"
save_new = save_old + "\t\t\tcom.spd.cohero.CoHero.storeGame(bundle);\n"

load_old = "\t\tBundle bundle = FileUtils.bundleFromFile( GamesInProgress.gameFile( save ) );\n"
load_new = load_old + "\t\tif (fullLoad) com.spd.cohero.CoHero.restoreGame(bundle);\n"

if save_new in text or load_new in text:
    raise SystemExit("CoHero Dungeon save hooks are already present")

save_count = text.count(save_old)
load_count = text.count(load_old)
if save_count != 1:
    raise SystemExit(f"expected exactly one Dungeon save anchor, found {save_count}")
if load_count != 1:
    raise SystemExit(f"expected exactly one Dungeon load anchor, found {load_count}")

text = text.replace(save_old, save_new, 1)
text = text.replace(load_old, load_new, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
