#!/usr/bin/env python3
"""Add a tenth alchemy guide category without changing saved document page IDs."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def main(path: Path) -> None:
    journal = path.read_text(encoding="utf-8")

    journal = replace_once(
        journal,
        "\t\tprivate static final int NUM_BUTTONS = 9;",
        "\t\tprivate static final int NUM_BUTTONS = 10;\n"
        "\t\tpublic static final int REVERSE_STONES_IDX = 2;",
        "guide button count",
    )
    journal = replace_once(
        journal,
        "\t\t\t\tItemSpriteSheet.STONE_HOLDER,\n\t\t\t\tItemSpriteSheet.FOOD_HOLDER,",
        "\t\t\t\tItemSpriteSheet.STONE_HOLDER,\n"
        "\t\t\t\tItemSpriteSheet.SCROLL_HOLDER,\n"
        "\t\t\t\tItemSpriteSheet.FOOD_HOLDER,",
        "reverse runestone button icon",
    )
    journal = replace_once(
        journal,
        "\t\t\t\tif (Document.ALCHEMY_GUIDE.isPageFound(i)) {",
        "\t\t\t\tint originalIndex = i > REVERSE_STONES_IDX ? i - 1 : i;\n"
        "\t\t\t\tif (Document.ALCHEMY_GUIDE.isPageFound(\n"
        "\t\t\t\t\t\ti == REVERSE_STONES_IDX ? 1 : originalIndex)) {",
        "guide unlock state",
    )
    journal = replace_once(
        journal,
        "\t\t\t\t\tbuttonWidth = width()/4;",
        "\t\t\t\t\tbuttonWidth = width()/5;",
        "portrait guide second row",
    )
    journal = replace_once(
        journal,
        "\t\t\tif (currentPageIdx != -1 && !Document.ALCHEMY_GUIDE.isPageFound(currentPageIdx)){",
        "\t\t\tint originalIndex = currentPageIdx > REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? currentPageIdx - 1 : currentPageIdx;\n"
        "\t\t\tif (currentPageIdx == REVERSE_STONES_IDX) originalIndex = 1;\n"
        "\t\t\tif (currentPageIdx != -1 && !Document.ALCHEMY_GUIDE.isPageFound(originalIndex)){",
        "page discovery mapping",
    )
    journal = replace_once(
        journal,
        "\t\t\ttitle.label(Document.ALCHEMY_GUIDE.pageTitle(currentPageIdx));",
        "\t\t\ttitle.label(currentPageIdx == REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? Messages.get(\"cohero.alchemy.reverse_stones.title\")\n"
        "\t\t\t\t\t: Document.ALCHEMY_GUIDE.pageTitle(originalIndex));",
        "guide title",
    )
    journal = replace_once(
        journal,
        "\t\t\tbody.text(Document.ALCHEMY_GUIDE.pageBody(currentPageIdx));",
        "\t\t\tif (currentPageIdx == REVERSE_STONES_IDX) {\n"
        "\t\t\t\tString description = Messages.get(\"cohero.alchemy.reverse_stones.body\");\n"
        "\t\t\t\tif (!com.spd.cohero.CoHeroSettings.ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY) {\n"
        "\t\t\t\t\tdescription += \"\\n\" + Messages.get(\"cohero.alchemy.reverse_stones.enchantment_disabled\");\n"
        "\t\t\t\t}\n"
        "\t\t\t\tbody.text(description);\n"
        "\t\t\t} else {\n"
        "\t\t\t\tbody.text(Document.ALCHEMY_GUIDE.pageBody(originalIndex));\n"
        "\t\t\t}",
        "guide description",
    )
    journal = replace_once(
        journal,
        "\t\t\tDocument.ALCHEMY_GUIDE.readPage(currentPageIdx);\n\t\t\t\n"
        "\t\t\tArrayList<QuickRecipe> toAdd = QuickRecipe.getRecipes(currentPageIdx);",
        "\t\t\tif (currentPageIdx != REVERSE_STONES_IDX) {\n"
        "\t\t\t\tDocument.ALCHEMY_GUIDE.readPage(originalIndex);\n"
        "\t\t\t}\n\t\t\t\n"
        "\t\t\tArrayList<QuickRecipe> toAdd = QuickRecipe.getRecipes(\n"
        "\t\t\t\t\tcurrentPageIdx == REVERSE_STONES_IDX ? 9 : originalIndex);",
        "guide recipe mapping",
    )

    path.write_text(journal, encoding="utf-8")
    print("patched WndJournal.java: reverse runestone guide page")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_journal.py <WndJournal.java>")
    main(Path(sys.argv[1]))
