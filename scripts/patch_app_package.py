#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_app_package.py <build.gradle>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon'"
new = "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon.cohero'"

if new in text:
    raise SystemExit("CoHero app package is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one upstream appPackageName anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}: com.shatteredpixel.shatteredpixeldungeon.cohero")
