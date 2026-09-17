#!/usr/bin/env python3
from pathlib import Path
import sys

FILES = (
    "misc.properties",
    "misc_zh.properties",
    "misc_zh-hant.properties",
)
PREFIX = "cohero."


def parse_keys(text: str, source: Path) -> list[str]:
    keys = []
    for line_no, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            raise SystemExit(f"{source}:{line_no}: expected key=value")
        key = line.split("=", 1)[0].strip()
        if not key.startswith(PREFIX):
            raise SystemExit(f"{source}:{line_no}: key must start with {PREFIX!r}: {key}")
        if key in keys:
            raise SystemExit(f"{source}:{line_no}: duplicate key: {key}")
        keys.append(key)
    if not keys:
        raise SystemExit(f"{source}: no CoHero message keys found")
    return keys


def key_exists(target_text: str, key: str) -> bool:
    for raw in target_text.splitlines():
        line = raw.lstrip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if line.startswith(key + "=") or line.startswith(key + ":"):
            return True
    return False


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_messages.py <upstream-misc-dir> <cohero-message-dir>")

    target_dir = Path(sys.argv[1])
    source_dir = Path(sys.argv[2])

    for name in FILES:
        target = target_dir / name
        source = source_dir / name
        if not target.is_file():
            raise SystemExit(f"missing upstream message bundle: {target}")
        if not source.is_file():
            raise SystemExit(f"missing CoHero message bundle: {source}")

        target_text = target.read_text(encoding="utf-8")
        source_text = source.read_text(encoding="utf-8").strip()
        keys = parse_keys(source_text, source)

        existing = [key for key in keys if key_exists(target_text, key)]
        if existing:
            raise SystemExit(f"CoHero message keys already exist in {target}: {', '.join(existing)}")

        patched = target_text.rstrip() + "\n\n# CoHero Pixel Dungeon\n" + source_text + "\n"
        target.write_text(patched, encoding="utf-8")
        print(f"patched {target} with {len(keys)} CoHero messages")


if __name__ == "__main__":
    main()
