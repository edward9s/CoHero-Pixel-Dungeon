#!/usr/bin/env python3
from pathlib import Path
import sys

MARKER = "// COHERO_SETTINGS_TAB"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wndsettings.py <WndSettings.java>")

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    if MARKER in text:
        raise SystemExit(f"CoHero settings tab already exists in {path}")

    field_anchor = "\tprivate LangsTab    langs;\n"
    field_replacement = (
        field_anchor
        + "\tprivate com.spd.cohero.CoHeroSettingsTab cohero; "
        + MARKER
        + "\n"
    )
    text = replace_once(text, field_anchor, field_replacement, "settings field")

    tab_anchor = "\t\tadd( langsTab );\n\n\t\tresize(width, (int)Math.ceil(height));\n"
    tab_replacement = """\t\tadd( langsTab );

\t\tcohero = new com.spd.cohero.CoHeroSettingsTab();
\t\tcohero.setSize(width, 0);
\t\theight = Math.max(height, cohero.height());
\t\tadd(cohero);

\t\tadd(new IconTab(Icons.get(Icons.TALENT)) {
\t\t\t@Override
\t\t\tprotected void select(boolean value) {
\t\t\t\tsuper.select(value);
\t\t\t\tcohero.visible = cohero.active = value;
\t\t\t\tif (value) last_index = 6;
\t\t\t}
\t\t});

\t\tresize(width, (int)Math.ceil(height));
"""
    text = replace_once(text, tab_anchor, tab_replacement, "settings tab")

    hidden_input_anchor = "\t\tif (tabs.size() == 5 && last_index >= 3){\n"
    hidden_input_replacement = "\t\tif (tabs.size() == 6 && last_index >= 3){\n"
    text = replace_once(
        text,
        hidden_input_anchor,
        hidden_input_replacement,
        "hidden input tab selection",
    )

    path.write_text(text, encoding="utf-8")
    print(f"patched {path}: CoHero settings tab")


if __name__ == "__main__":
    main()
