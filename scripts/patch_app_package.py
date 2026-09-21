#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_app_package.py <build.gradle>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

name_old = "appName = 'Shattered Pixel Dungeon'"
name_new = "appName = 'Co Shattered Pixel Dungeon'"
package_old = "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon'"
package_new = "appPackageName = 'com.shatteredpixel.shatteredpixeldungeon.cohero'"

if name_new in text or package_new in text:
    raise SystemExit("CoHero app identity is already present")

name_count = text.count(name_old)
if name_count != 1:
    raise SystemExit(f"expected exactly one upstream appName anchor, found {name_count}")

package_count = text.count(package_old)
if package_count != 1:
    raise SystemExit(
        f"expected exactly one upstream appPackageName anchor, found {package_count}"
    )

text = text.replace(name_old, name_new, 1)
text = text.replace(package_old, package_new, 1)
path.write_text(text, encoding="utf-8")
print(
    f"patched {path}: Co Shattered Pixel Dungeon / "
    "com.shatteredpixel.shatteredpixeldungeon.cohero"
)
