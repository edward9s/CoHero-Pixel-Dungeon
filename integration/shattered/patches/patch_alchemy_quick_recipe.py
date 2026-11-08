#!/usr/bin/env python3
"""Add native stacked-quantity reverse-runestone recipes to the alchemy guide."""
from pathlib import Path
import sys

from java_patch import java_source


def main(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    anchor = "\t\t\tcase 8:\n"
    replacement = (
        "\t\t\tcase 9:\n"
        "\t\t\t\tr = new Scroll.StoneToScroll();\n"
        "\t\t\t\tfor (Class<?> cls : Generator.Category.STONE.classes) {\n"
        "\t\t\t\t\tRunestone stone = (Runestone) Reflection.newInstance(cls);\n"
        "\t\t\t\t\tArrayList<Item> recipeInputs = new ArrayList<>(Arrays.asList(\n"
        "\t\t\t\t\t\t\tstone, (Runestone) Reflection.newInstance(cls)));\n"
        "\t\t\t\t\tif (r.testIngredients(recipeInputs)) {\n"
        "\t\t\t\t\t\tItem output = r.sampleOutput(recipeInputs);\n"
        "\t\t\t\t\t\tresult.add(new QuickRecipe(r,\n"
        "\t\t\t\t\t\t\t\tnew ArrayList<>(Arrays.asList(stone.quantity(2))), output));\n"
        "\t\t\t\t\t}\n"
        "\t\t\t\t}\n"
        "\t\t\t\treturn result;\n"
        "\t\t\tcase 8:\n"
    )
    if source.count(anchor) != 1:
        raise SystemExit("QuickRecipe.java: expected one original case 8")
    path.write_text(source.replace(anchor, replacement, 1), encoding="utf-8")
    print("patched QuickRecipe.java: one runestone icon with quantity 2")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_quick_recipe.py <QuickRecipe.java>")
    main(Path(sys.argv[1]))
