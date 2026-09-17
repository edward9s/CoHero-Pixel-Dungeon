#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero_select.py <HeroSelectScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

create_hook = "\t\tcom.spd.cohero.CoHero.onHeroSelectSceneCreated();\n"
confirm_hook = (
    "\t\t\t\tif (com.spd.cohero.CoHero.onHeroSelectionConfirmed("
    "GamesInProgress.selectedClass)) return;\n"
)

if create_hook in text or confirm_hook in text:
    raise SystemExit("CoHero HeroSelectScene hooks are already present")

create_anchor = "\t\tsuper.create();\n"
confirm_anchor = "\t\t\t\tif (GamesInProgress.selectedClass == null) return;\n"
title_anchor = (
    '\t\ttitle = PixelScene.renderTextBlock(Messages.get(this, "title"), 12);\n'
)
title_replacement = (
    '\t\ttitle = PixelScene.renderTextBlock('
    'com.spd.cohero.CoHero.heroSelectionTitle(Messages.get(this, "title")), 12);\n'
)

for name, anchor in (
    ("create", create_anchor),
    ("confirm", confirm_anchor),
    ("title", title_anchor),
):
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"expected exactly one HeroSelectScene {name} anchor, found {count}")

text = text.replace(create_anchor, create_anchor + "\n" + create_hook, 1)
text = text.replace(confirm_anchor, confirm_anchor + "\n" + confirm_hook, 1)
text = text.replace(title_anchor, title_replacement, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
