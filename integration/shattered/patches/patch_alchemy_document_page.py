#!/usr/bin/env python3
"""Translate original alchemy guide pickup pages to the new display indices."""
from pathlib import Path
import sys

from java_patch import java_source


def main(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    anchor = "\t\t\tWndJournal.AlchemyTab.currentPageIdx = document().pageIdx(page());"
    replacement = (
        "\t\t\tint originalIndex = document().pageIdx(page());\n"
        "\t\t\tWndJournal.AlchemyTab.currentPageIdx = originalIndex >= WndJournal.AlchemyTab.REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? originalIndex + 1 : originalIndex;"
    )
    if source.count(anchor) != 1:
        raise SystemExit("DocumentPage.java: expected one original alchemy page index")
    path.write_text(source.replace(anchor, replacement, 1), encoding="utf-8")
    print("patched DocumentPage.java: preserve original alchemy page lookup")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_document_page.py <DocumentPage.java>")
    main(Path(sys.argv[1]))
