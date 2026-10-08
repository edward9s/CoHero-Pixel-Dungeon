#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_app_package.py <build.gradle>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

targets = (
    ("appName", "Shattered Pixel Dungeon", "CoShattered Pixel Dungeon"),
    (
        "appPackageName",
        "com.shatteredpixel.shatteredpixeldungeon",
        "com.shatteredpixel.shatteredpixeldungeon.cohero",
    ),
)

for key, upstream_value, cohero_value in targets:
    current_pattern = re.compile(
        rf"(?m)^(?P<indent>\s*){re.escape(key)}\s*=\s*"
        rf"(?P<quote>['\"]){re.escape(cohero_value)}(?P=quote)"
        rf"(?P<comment>\s*//.*)?\s*$"
    )
    if current_pattern.search(text):
        continue

    upstream_pattern = re.compile(
        rf"(?m)^(?P<indent>\s*){re.escape(key)}\s*=\s*"
        rf"(?P<quote>['\"]){re.escape(upstream_value)}(?P=quote)"
        rf"(?P<comment>\s*//.*)?\s*$"
    )
    matches = list(upstream_pattern.finditer(text))
    if len(matches) != 1:
        raise SystemExit(
            f"expected exactly one semantic {key} assignment for {upstream_value!r}, "
            f"found {len(matches)}"
        )

    match = matches[0]
    replacement = (
        f"{match.group('indent')}{key} = "
        f"{match.group('quote')}{cohero_value}{match.group('quote')}"
        f"{match.group('comment') or ''}"
    )
    text = text[:match.start()] + replacement + text[match.end():]

path.write_text(text, encoding="utf-8")
print(
    f"patched {path}: CoShattered Pixel Dungeon / "
    "com.shatteredpixel.shatteredpixeldungeon.cohero"
)
