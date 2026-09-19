#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_start_scene.py <StartScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

field_old = """\t\tprivate Image hero;
"""
field_new = """\t\tprivate Image companion;
\t\tprivate Image hero;
"""

children_old = """\t\t\tbg = Chrome.get(Chrome.Type.TOAST_TR);
\t\t\tadd( bg );
\t\t\t
\t\t\tname = PixelScene.renderTextBlock(9);
"""
children_new = """\t\t\tbg = Chrome.get(Chrome.Type.TOAST_TR);
\t\t\tadd( bg );

\t\t\tcompanion = new Image();
\t\t\tcompanion.visible = false;
\t\t\tadd(companion);
\t\t\t
\t\t\tname = PixelScene.renderTextBlock(9);
"""

newgame_old = """\t\t\tif (newGame){
\t\t\t\tname.text( Messages.get(StartScene.class, "new"));
\t\t\t\t
\t\t\t\tif (hero != null){
"""
newgame_new = """\t\t\tif (newGame){
\t\t\t\tname.text( Messages.get(StartScene.class, "new"));
\t\t\t\tcompanion.visible = false;
\t\t\t\t
\t\t\t\tif (hero != null){
"""

existing_anchor = """\t\t\t} else {
\t\t\t\t
\t\t\t\tif (info.subClass != HeroSubClass.NONE){
"""
existing_new = """\t\t\t} else {

\t\t\t\tif (info.companionClass != null) {
\t\t\t\t\tcompanion.copy(new Image(
\t\t\t\t\t\t\tinfo.companionClass.spritesheet(),
\t\t\t\t\t\t\t0,
\t\t\t\t\t\t\t15 * info.companionArmorTier,
\t\t\t\t\t\t\t12,
\t\t\t\t\t\t\t15));
\t\t\t\t\tcompanion.visible = true;
\t\t\t\t} else {
\t\t\t\t\tcompanion.visible = false;
\t\t\t\t}
\t\t\t\t
\t\t\t\tif (info.subClass != HeroSubClass.NONE){
"""

layout_old = """\t\t\tif (hero != null){
\t\t\t\thero.x = x+8;
\t\t\t\thero.y = y + (height - hero.height())/2f;
\t\t\t\talign(hero);
\t\t\t\t
\t\t\t\tname.setPos(
\t\t\t\t\t\thero.x + hero.width() + 6,
\t\t\t\t\t\ty + (height - name.height() - lastPlayed.height() - 2)/2f
\t\t\t\t);
"""
layout_new = """\t\t\tif (hero != null){
\t\t\t\thero.x = x+8;
\t\t\t\thero.y = y + (height - hero.height())/2f;
\t\t\t\talign(hero);

\t\t\t\tfloat portraitRight = hero.x + hero.width();
\t\t\t\tif (companion.visible) {
\t\t\t\t\tcompanion.x = hero.x + hero.width()/2f;
\t\t\t\t\tcompanion.y = hero.y;
\t\t\t\t\talign(companion);
\t\t\t\t\tportraitRight = Math.max(portraitRight, companion.x + companion.width());
\t\t\t\t}
\t\t\t\t
\t\t\t\tname.setPos(
\t\t\t\t\t\tportraitRight + 4,
\t\t\t\t\t\ty + (height - name.height() - lastPlayed.height() - 2)/2f
\t\t\t\t);
"""

anchors = [
    ("field", field_old, field_new),
    ("children", children_old, children_new),
    ("newgame", newgame_old, newgame_new),
    ("existing", existing_anchor, existing_new),
    ("layout", layout_old, layout_new),
]

for label, old, new in anchors:
    if new in text:
        raise SystemExit(f"CoHero StartScene {label} hook is already present")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one StartScene {label} anchor, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
