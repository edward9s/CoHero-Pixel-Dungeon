#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_menu_pane.py <MenuPane.java>")

path = Path(sys.argv[1])
menu = path.read_text(encoding="utf-8")

menu_old = 'version = new BitmapText( "v" + Game.version , PixelScene.pixelFont);'
menu_new = 'version = new BitmapText( com.spd.cohero.CoHeroVersion.display(Game.version), PixelScene.pixelFont);'


if menu_new in menu:
    raise SystemExit("CoHero MenuPane version UI hook is already present")
if menu.count(menu_old) != 1:
    raise SystemExit(f"expected exactly one MenuPane version anchor, found {menu.count(menu_old)}")
menu = menu.replace(menu_old, menu_new, 1)


path.write_text(menu, encoding="utf-8")
print(f"patched {path}")
