#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cursed_wand.py <CursedWand.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global text
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one CursedWand {label} anchor, found {text.count(old)}")
    text = text.replace(old, new, 1)

fx_anchor = """\t\t\twand.fx(bolt, callback);
\t\t}

\t\t@Override
\t\tpublic boolean effect(Item origin, Char user, Ballistica bolt, boolean positiveOnly) {
\t\t\tif (wand == null){
"""
fx_patch = """\t\t\tif (user instanceof com.spd.cohero.CoHeroAlly) {
\t\t\t\t// Generated wands otherwise use Item.curUser (the Hero) for their FX.
\t\t\t\tMagicMissile.boltFromChar(user.sprite.parent,
\t\t\t\t\t\tMagicMissile.RAINBOW, user.sprite, bolt.collisionPos, callback);
\t\t\t\tSample.INSTANCE.play(Assets.Sounds.ZAP);
\t\t\t} else {
\t\t\t\twand.fx(bolt, callback);
\t\t\t}
\t\t}

\t\t@Override
\t\tpublic boolean effect(Item origin, Char user, Ballistica bolt, boolean positiveOnly) {
\t\t\tif (wand == null){
"""
replace_once(fx_anchor, fx_patch, "random wand FX")

zap_anchor = """\t\t\twand.levelKnown = false;
\t\t\twand.onZap(bolt);
\t\t\twand = null;
"""
zap_patch = """\t\t\twand.levelKnown = false;
\t\t\tif (user instanceof com.spd.cohero.CoHeroAlly) {
\t\t\t\twand.coHeroRandomZap(user, bolt);
\t\t\t} else {
\t\t\t\twand.onZap(bolt);
\t\t\t}
\t\t\twand = null;
"""
replace_once(zap_anchor, zap_patch, "random wand effect")

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
