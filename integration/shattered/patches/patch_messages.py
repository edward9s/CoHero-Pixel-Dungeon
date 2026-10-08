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


def validate_locale(
        source: Path,
        base_keys: list[str],
        base_placeholders: dict[str, tuple[str, ...]],
) -> str:
    source_text = source.read_text(encoding="utf-8").strip()
    localized_messages = parse_messages(source_text, source)
    localized = dict(localized_messages)

    missing_keys = [key for key in base_keys if key not in localized]
    extra_keys = [key for key in localized if key not in base_placeholders]
    if missing_keys or extra_keys:
        details = []
        if missing_keys:
            details.append("missing keys: " + ", ".join(missing_keys))
        if extra_keys:
            details.append("extra keys: " + ", ".join(extra_keys))
        raise SystemExit(f"{source}: " + "; ".join(details))

    for key in base_keys:
        actual = placeholders(localized[key])
        expected = base_placeholders[key]
        if actual != expected:
            raise SystemExit(
                f"{source}: placeholder mismatch for {key}: "
                f"expected {expected}, found {actual}"
            )

    # Preserve the source file's own ordering/comments when present. Key order is not semantic.
    return source_text


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_messages.py <upstream-misc-dir> <cohero-message-dir>")

    target_dir = Path(sys.argv[1])
    source_dir = Path(sys.argv[2])

    target_names = sorted(path.name for path in target_dir.glob("misc*.properties"))
    source_names = sorted(path.name for path in source_dir.glob("misc*.properties"))

    if not target_names:
        raise SystemExit(f"no upstream misc message bundles found in {target_dir}")

    base_path = source_dir / BASE_FILE
    if not base_path.is_file():
        raise SystemExit(f"missing required CoHero base locale: {base_path}")

    base_messages = parse_messages(base_path.read_text(encoding="utf-8"), base_path)
    base_keys = [key for key, _ in base_messages]
    base_placeholders = {key: placeholders(value) for key, value in base_messages}
    base_text = validate_locale(base_path, base_keys, base_placeholders)

    validated_sources = {BASE_FILE: base_text}
    for name in source_names:
        if name == BASE_FILE:
            continue
        validated_sources[name] = validate_locale(
            source_dir / name, base_keys, base_placeholders)

    fallback_locales = []

    for name in target_names:
        target = target_dir / name
        source_text = validated_sources.get(name)
        if source_text is None:
            # Upstream may add a new locale before CoHero has a translation. Falling back to
            # English keeps unattended builds working and is preferable to omitting keys.
            source_text = base_text
            fallback_locales.append(name)

        target_text = target.read_text(encoding="utf-8")
        existing = [key for key in base_keys if key_exists(target_text, key)]
        if existing:
            if len(existing) == len(base_keys):
                print(f"{target}: CoHero message keys already present")
                continue
            raise SystemExit(
                f"partial CoHero message set already exists in {target}: {', '.join(existing)}"
            )

        patched = target_text.rstrip() + "\n\n# CoHero Pixel Dungeon\n" + source_text + "\n"
        target.write_text(patched, encoding="utf-8")
        print(f"patched {target} with {len(base_keys)} CoHero messages")

    unused_sources = sorted(set(source_names) - set(target_names) - {BASE_FILE})
    if fallback_locales:
        print(
            "CoHero locale fallback to misc.properties: "
            + ", ".join(fallback_locales)
        )
    if unused_sources:
        print(
            "CoHero locale files not used by this upstream release: "
            + ", ".join(unused_sources)
        )


if __name__ == "__main__":
    main()
