#!/usr/bin/env python3
"""Add an optional drag-preview callback to SPD's OptionSlider."""
from pathlib import Path
import sys

from java_patch import java_source


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    if "protected void onDragPreview(int value)" in source:
        raise SystemExit(f"{path}: slider preview already patched")

    source = replace_once(
        source,
        "\tprivate int selectedVal;\n",
        "\tprivate int selectedVal;\n\tprivate int previewVal;\n",
        "preview state",
    )
    source = replace_once(
        source,
        "\tprotected abstract void onChange();\n",
        """\tprotected abstract void onChange();

\t/** Called only when dragging across a different tick; does not commit a setting. */
\tprotected void onDragPreview(int value) {}

\tprivate void previewDragValue() {
\t\tint value = minVal + Math.round((sliderNode.x - x) / tickDist);
\t\tif (value != previewVal) {
\t\t\tpreviewVal = value;
\t\t\tonDragPreview(value);
\t\t}
\t}
""",
        "preview callback",
    )
    source = replace_once(
        source,
        "\t\tthis.selectedVal = val;\n",
        "\t\tthis.selectedVal = val;\n\t\tthis.previewVal = val;\n",
        "selection sync",
    )
    source = replace_once(
        source,
        "\t\t\t\tsliderNode.brightness(1.5f);\n",
        "\t\t\t\tsliderNode.brightness(1.5f);\n\t\t\t\tpreviewDragValue();\n",
        "pointer down preview",
    )
    source = replace_once(
        source,
        "\t\t\t\t\tselectedVal = minVal + Math.round((sliderNode.x - x) / tickDist);\n",
        "\t\t\t\t\tselectedVal = minVal + Math.round((sliderNode.x - x) / tickDist);\n\t\t\t\t\tpreviewVal = selectedVal;\n",
        "release preview sync",
    )
    drag_anchor = (
        "\t\t\tprotected void onDrag( PointerEvent event ) {\n"
        "\t\t\t\tif (pressed) {\n"
        "\t\t\t\t\tPointF p = camera().screenToCamera((int) event.current.x, (int) event.current.y);\n"
        "\t\t\t\t\tsliderNode.x = GameMath.gate(sliderBG.x - 2, p.x - sliderNode.width()/2, sliderBG.x + sliderBG.width() - 2);\n"
    )
    source = replace_once(
        source,
        drag_anchor,
        drag_anchor + "\t\t\t\t\tpreviewDragValue();\n",
        "drag preview",
    )

    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: OptionSlider drag preview callback")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_option_slider.py <OptionSlider.java>")
    patch(Path(sys.argv[1]))
