#!/usr/bin/env python3
from pathlib import Path
import sys


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_android_manifest.py <AndroidManifest.xml>")

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    if "android.permission.MANAGE_EXTERNAL_STORAGE" in text:
        raise SystemExit(f"CoHero storage permissions already exist in {path}")

    anchor = "\t<application"
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"expected exactly one <application anchor, found {count}")

    permissions = (
        '\t<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />\n'
        '\t<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />\n'
        '\t<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n\n'
    )

    text = text.replace(anchor, permissions + anchor, 1)
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}: CoHero save-transfer storage permissions")


if __name__ == "__main__":
    main()
