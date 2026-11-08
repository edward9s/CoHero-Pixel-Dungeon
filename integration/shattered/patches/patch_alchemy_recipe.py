#!/usr/bin/env python3
"""Register the reverse recipe and permit known cursed missiles/wands in alchemy."""
from pathlib import Path
import sys

from java_patch import java_source


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def main(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    source = replace_once(
        source,
        "\tprivate static Recipe[] twoIngredientRecipes = new Recipe[]{\n",
        "\tprivate static Recipe[] twoIngredientRecipes = new Recipe[]{\n"
        "\t\tnew Scroll.StoneToScroll(),\n",
        "Recipe.java: two-ingredient registration",
    )
    source = replace_once(
        source,
        "\t\t\treturn item.cursedKnown && !item.cursed &&\n"
        "\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();",
        "\t\t\treturn item.cursedKnown &&\n"
        "\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();",
        "Recipe.java: thrown-weapon ingredients",
    )
    source = replace_once(
        source,
        "\t\t} else if (item instanceof Wand) {\n"
        "\t\t\treturn item.cursedKnown && !item.cursed;",
        "\t\t} else if (item instanceof Wand) {\n"
        "\t\t\treturn item.cursedKnown;",
        "Recipe.java: wand ingredients",
    )

    path.write_text(source, encoding="utf-8")
    print("patched Recipe.java: reverse recipe, cursed missile/wand inputs")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_recipe.py <Recipe.java>")
    main(Path(sys.argv[1]))
