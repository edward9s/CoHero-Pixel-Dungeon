#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_menu_pane.py <MenuPane.java>")

path = Path(sys.argv[1])
menu = java_source(path.read_text(encoding="utf-8"))

menu_old = 'version = new BitmapText( "v" + Game.version , PixelScene.pixelFont);'
menu_new = ('com.spd.cohero.CoHeroUpdates.checkForUpdate();\n\t\t'
    'version = new BitmapText( '
    'com.spd.cohero.CoHeroVersion.display(Game.version, '
    'com.spd.cohero.CoHeroUpdates.latestVersion()), PixelScene.pixelFont);')


if menu_new in menu:
    raise SystemExit("CoHero MenuPane version UI hook is already present")
if menu.count(menu_old) != 1:
    raise SystemExit(f"expected exactly one MenuPane version anchor, found {menu.count(menu_old)}")
menu = menu.replace(menu_old, menu_new, 1)


# Re-layout when the network check finds a newer CoHero release.
layout_anchor = "\t@Override\n\tprotected void layout() {\n\t\tsuper.layout();\n\n\t\tbg.x = x;"
if menu.count(layout_anchor) != 1:
    raise SystemExit("expected exactly one MenuPane layout anchor")
update_hook = """
\t@Override
\tpublic void update() {
\t\tsuper.update();
\t\tString display = com.spd.cohero.CoHeroVersion.display(
\t\t\t\tGame.version, com.spd.cohero.CoHeroUpdates.latestVersion());
\t\tif (!display.equals(version.text())) {
\t\t\tversion.text(display);
\t\t\tlayout();
\t\t}
\t}

"""
menu = menu.replace(layout_anchor, update_hook + layout_anchor, 1)

overflow_anchor = "\t\tif (overFlow >= 1){\n"
if menu.count(overflow_anchor) != 1:
    raise SystemExit("expected exactly one MenuPane version-overflow anchor")
menu = menu.replace(
    overflow_anchor, overflow_anchor + "\t\t\tversionOverflowBG.visible = true;\n", 1)

path.write_text(menu, encoding="utf-8")
print(f"patched {path}")
