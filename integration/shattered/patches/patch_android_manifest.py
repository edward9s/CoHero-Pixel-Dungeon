#!/usr/bin/env python3
from pathlib import Path
import re
import sys


PERMISSIONS = (
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_android_manifest.py <AndroidManifest.xml>")

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    existing = set(re.findall(
        r'<uses-permission\b[^>]*\bandroid:name\s*=\s*["\']([^"\']+)["\'][^>]*/?>',
        text,
        flags=re.IGNORECASE,
    ))
    missing = [permission for permission in PERMISSIONS if permission not in existing]
    if not missing:
        print(f"{path}: CoHero save-transfer storage permissions already present")
        return

    applications = list(re.finditer(r"(?m)^(?P<indent>[ \t]*)<application\b", text))
    if len(applications) != 1:
        raise SystemExit(
            f"expected exactly one <application element, found {len(applications)}"
        )

    app = applications[0]
    indent = app.group("indent")
    permission_lines = "".join(
        f'{indent}<uses-permission android:name="{permission}" />\n'
        for permission in missing
    )
    text = text[:app.start()] + permission_lines + "\n" + text[app.start():]

    path.write_text(text, encoding="utf-8")
    print(
        f"patched {path}: added {len(missing)} CoHero save-transfer storage permission(s)"
    )


if __name__ == "__main__":
    main()
