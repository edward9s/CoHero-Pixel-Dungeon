#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_version_ui.py <TitleScene.java> <MenuPane.java>")

title_path = Path(sys.argv[1])
menu_path = Path(sys.argv[2])

title = title_path.read_text(encoding="utf-8")
menu = menu_path.read_text(encoding="utf-8")

title_old = 'version = new BitmapText( "v" + Game.version, pixelFont);'
title_new = 'version = new BitmapText( com.spd.cohero.CoHeroVersion.display(Game.version), pixelFont);'

menu_old = 'version = new BitmapText( "v" + Game.version , PixelScene.pixelFont);'
menu_new = 'version = new BitmapText( com.spd.cohero.CoHeroVersion.display(Game.version), PixelScene.pixelFont);'

if title_new in title or menu_new in menu:
    raise SystemExit("CoHero version UI hooks are already present")

if title.count(title_old) != 1:
    raise SystemExit(f"expected exactly one TitleScene version anchor, found {title.count(title_old)}")
if menu.count(menu_old) != 1:
    raise SystemExit(f"expected exactly one MenuPane version anchor, found {menu.count(menu_old)}")

title_path.write_text(title.replace(title_old, title_new, 1), encoding="utf-8")
menu_path.write_text(menu.replace(menu_old, menu_new, 1), encoding="utf-8")

print(f"patched {title_path}")
print(f"patched {menu_path}")
