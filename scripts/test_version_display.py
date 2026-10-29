#!/usr/bin/env python3
"""Test standalone CoHero version formatting and UI integration patches."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def check_patches(root: Path, upstream: Path) -> None:
    targets = [
        (
            "scenes/TitleScene.java",
            "patch_title_scene.py",
            ("CoHeroUpdates.checkForUpdate();",
             "CoHeroUpdates.latestVersion()",
             "CoHeroVersion.display(",
             "version.text(display);",
             "version.x = right - version.width();"),
        ),
        (
            "ui/MenuPane.java",
            "patch_menu_pane.py",
            ("CoHeroUpdates.checkForUpdate();",
             "CoHeroUpdates.latestVersion()",
             "CoHeroVersion.display(",
             "version.text(display);",
             "versionOverflowBG.visible = true;",
             "layout();"),
        ),
    ]
    upstream_package = upstream / "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon"
    with tempfile.TemporaryDirectory(prefix="cohero-version-ui-") as directory:
        for rel, patch, markers in targets:
            source = upstream_package / rel
            target = Path(directory) / source.name
            shutil.copyfile(source, target)
            subprocess.run(
                [sys.executable, str(root / "integration/shattered/patches" / patch), str(target)],
                check=True,
                capture_output=True,
                text=True,
            )
            result = target.read_text(encoding="utf-8")
            for marker in markers:
                if marker not in result:
                    raise AssertionError(f"{patch} missing {marker}")
            if result.count("CoHeroUpdates.checkForUpdate();") != 1:
                raise AssertionError(f"{patch} should start exactly one update check")
            if "CoHeroVersion.display(Game.version" in result or (
                    "Game.version, com.spd.cohero.CoHeroUpdates.latestVersion()" in result):
                raise AssertionError(f"{patch} must not display the SPD version")
    print("CoHero version UI integration checks passed")


def check_format(root: Path) -> None:
    source = root / "core/src/main/java/com/spd/cohero/CoHeroVersion.java"
    test = root / "scripts/tests/CoHeroVersionDisplayChecks.java"
    with tempfile.TemporaryDirectory(prefix="cohero-version-format-") as directory:
        subprocess.run(["javac", "-d", directory, str(source), str(test)], check=True)
        subprocess.run(
            ["java", "-cp", directory, "com.spd.cohero.CoHeroVersionDisplayChecks"],
            check=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--upstream", type=Path)
    args = parser.parse_args()
    if args.upstream is not None:
        check_patches(args.root.resolve(), args.upstream.resolve())
    else:
        check_format(args.root.resolve())


if __name__ == "__main__":
    main()
