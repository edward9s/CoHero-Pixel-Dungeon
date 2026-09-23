#!/usr/bin/env python3
from pathlib import Path
import re
import sys

PREFIX = "cohero."
BASE_FILE = "misc.properties"
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[A-Za-z]")


def parse_messages(text: str, source: Path) -> list[tuple[str, str]]:
    messages = []
    seen = set()
    for line_no, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            raise SystemExit(f"{source}:{line_no}: expected key=value")
        key, value = line.split("=", 1)
        key = key.strip()
        if not key.startswith(PREFIX):
            raise SystemExit(f"{source}:{line_no}: key must start with {PREFIX!r}: {key}")
        if key in seen:
            raise SystemExit(f"{source}:{line_no}: duplicate key: {key}")
        seen.add(key)
        messages.append((key, value))
    if not messages:
        raise SystemExit(f"{source}: no CoHero message keys found")
    return messages


def placeholders(value: str) -> tuple[str, ...]:
    return tuple(PLACEHOLDER_RE.findall(value))


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

    target_names = sorted(path.name for path in target_dir.glob("misc*.properties"))
    source_names = sorted(path.name for path in source_dir.glob("misc*.properties"))

    if not target_names:
        raise SystemExit(f"no upstream misc message bundles found in {target_dir}")

    missing = sorted(set(target_names) - set(source_names))
    extra = sorted(set(source_names) - set(target_names))
    if missing or extra:
        details = []
        if missing:
            details.append("missing CoHero locales: " + ", ".join(missing))
        if extra:
            details.append("unexpected CoHero locales: " + ", ".join(extra))
        raise SystemExit("; ".join(details))

    base_path = source_dir / BASE_FILE
    base_messages = parse_messages(base_path.read_text(encoding="utf-8"), base_path)
    base_keys = [key for key, _ in base_messages]
    base_placeholders = {key: placeholders(value) for key, value in base_messages}

    for name in target_names:
        target = target_dir / name
        source = source_dir / name

        source_text = source.read_text(encoding="utf-8").strip()
        localized_messages = parse_messages(source_text, source)
        localized_keys = [key for key, _ in localized_messages]

        if localized_keys != base_keys:
            missing_keys = [key for key in base_keys if key not in localized_keys]
            extra_keys = [key for key in localized_keys if key not in base_keys]
            order_mismatch = not missing_keys and not extra_keys
            details = []
            if missing_keys:
                details.append("missing keys: " + ", ".join(missing_keys))
            if extra_keys:
                details.append("extra keys: " + ", ".join(extra_keys))
            if order_mismatch:
                details.append("key order differs from misc.properties")
            raise SystemExit(f"{source}: " + "; ".join(details))

        for key, value in localized_messages:
            actual = placeholders(value)
            expected = base_placeholders[key]
            if actual != expected:
                raise SystemExit(
                    f"{source}: placeholder mismatch for {key}: "
                    f"expected {expected}, found {actual}"
                )

        target_text = target.read_text(encoding="utf-8")
        existing = [key for key in base_keys if key_exists(target_text, key)]
        if existing:
            raise SystemExit(
                f"CoHero message keys already exist in {target}: {', '.join(existing)}"
            )

        patched = target_text.rstrip() + "\n\n# CoHero Pixel Dungeon\n" + source_text + "\n"
        target.write_text(patched, encoding="utf-8")
        print(f"patched {target} with {len(base_keys)} CoHero messages")


if __name__ == "__main__":
    main()
