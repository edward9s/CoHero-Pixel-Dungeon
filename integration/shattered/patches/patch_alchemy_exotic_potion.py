#!/usr/bin/env python3
"""Add four-energy exotic-to-regular potion alchemy."""
from pathlib import Path
import sys

from java_patch import java_source


EXOTIC_TO_POTION = """
\tpublic static class ExoticToPotion extends Recipe {
\t\t@Override
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\treturn ingredients.size() == 1
\t\t\t\t\t&& ingredients.get(0) instanceof ExoticPotion
\t\t\t\t\t&& ingredients.get(0).quantity() > 0
\t\t\t\t\t&& exoToReg.containsKey(ingredients.get(0).getClass());
\t\t}

\t\t@Override
\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 4;
\t\t}

\t\t@Override
\t\tpublic Item brew(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tExoticPotion exotic = (ExoticPotion) ingredients.get(0);
\t\t\tPotion potion = Reflection.newInstance(exoToReg.get(exotic.getClass()));
\t\t\texotic.quantity(exotic.quantity() - 1);
\t\t\tpotion.identify();
\t\t\treturn potion;
\t\t}

\t\t@Override
\t\tpublic Item sampleOutput(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tExoticPotion exotic = (ExoticPotion) ingredients.get(0);
\t\t\treturn exotic.isKnown()
\t\t\t\t\t? Reflection.newInstance(exoToReg.get(exotic.getClass()))
\t\t\t\t\t: new Potion.PlaceHolder();
\t\t}
\t}
"""


def main(path: Path) -> None:
    source = java_source(path.read_text(encoding="utf-8"))
    if source.count("public static class PotionToExotic extends Recipe") != 1:
        raise SystemExit("ExoticPotion.java: missing original PotionToExotic")
    if "public static class ExoticToPotion" in source:
        raise SystemExit("ExoticPotion.java: reverse recipe already exists")
    if not source.endswith("\t}\n}\n"):
        raise SystemExit("ExoticPotion.java: unexpected class ending")

    path.write_text(source[:-2] + "\n" + EXOTIC_TO_POTION + "}\n", encoding="utf-8")
    print("patched ExoticPotion.java: reverse exotic potion alchemy")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_exotic_potion.py <ExoticPotion.java>")
    main(Path(sys.argv[1]))
