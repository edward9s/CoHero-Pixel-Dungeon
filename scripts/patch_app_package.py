#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_app_package.py <build.gradle>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

variants = (
    (
        "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon'",
        "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon.cohero'",
    ),
    (
        "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon.mod'",
        "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon.mod.cohero'",
    ),
)

for _, patched in variants:
    if patched in text:
        raise SystemExit(f"CoHero app package is already present: {patched}")

matches = []
for original, patched in variants:
    count = text.count(original)
    if count > 1:
        raise SystemExit(f"expected at most one appPackageName anchor for {original!r}, found {count}")
    if count == 1:
        matches.append((original, patched))

if len(matches) != 1:
    raise SystemExit(f"expected exactly one supported appPackageName anchor, found {len(matches)}")

original, patched = matches[0]
path.write_text(text.replace(original, patched, 1), encoding="utf-8")
print(f"patched {path}: {patched}")
