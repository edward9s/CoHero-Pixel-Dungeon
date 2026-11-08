#!/usr/bin/env python3
"""Add reverse runestone alchemy using SPD's original scroll-to-stone mapping."""
from pathlib import Path
import sys


STONE_TO_SCROLL = """
\tpublic static class StoneToScroll extends Recipe {

\t\tprivate static final HashMap<Class<? extends Runestone>, Class<? extends Scroll>> scrolls = new HashMap<>();
\t\tstatic {
\t\t\tfor (Class<? extends Scroll> scroll : ScrollToStone.stones.keySet()) {
\t\t\t\tClass<? extends Runestone> stone = ScrollToStone.stones.get(scroll);
\t\t\t\tif (scrolls.put(stone, scroll) != null) {
\t\t\t\t\tthrow new IllegalStateException("Ambiguous runestone: " + stone.getSimpleName());
\t\t\t\t}
\t\t\t}
\t\t}

\t\t@Override
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\tif (ingredients.size() != 2) return false;

\t\t\tItem first = ingredients.get(0);
\t\t\tItem second = ingredients.get(1);
\t\t\treturn first instanceof Runestone
\t\t\t\t\t&& first.getClass() == second.getClass()
\t\t\t\t\t&& first.quantity() > 0 && second.quantity() > 0
\t\t\t\t\t&& scrolls.containsKey(first.getClass())
\t\t\t\t\t&& (com.spd.cohero.CoHeroSettings.ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY
\t\t\t\t\t\t\t|| !(first instanceof StoneOfEnchantment));
\t\t}

\t\t@Override
\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 0;
\t\t}

\t\t@Override
\t\tpublic Item brew(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tScroll scroll = Reflection.newInstance(scrolls.get(ingredients.get(0).getClass()));
\t\t\tscroll.identify();
\t\t\tfor (Item stone : ingredients) {
\t\t\t\tstone.quantity(stone.quantity() - 1);
\t\t\t}
\t\t\treturn scroll;
\t\t}

\t\t@Override
\t\tpublic Item sampleOutput(ArrayList<Item> ingredients) {
\t\t\tif (!testIngredients(ingredients)) return null;

\t\t\tScroll scroll = Reflection.newInstance(scrolls.get(ingredients.get(0).getClass()));
\t\t\treturn scroll.isKnown() ? scroll : new Scroll.PlaceHolder();
\t\t}
\t}
"""


def main(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    if source.count("public static class ScrollToStone extends Recipe") != 1:
        raise SystemExit("Scroll.java: original scroll-to-stone mapping not found exactly once")
    if "public static class StoneToScroll" in source:
        raise SystemExit("Scroll.java: reverse recipe already present")
    if not source.endswith("\t}\n}\n"):
        raise SystemExit("Scroll.java: unexpected class ending")

    path.write_text(source[:-2] + "\n" + STONE_TO_SCROLL + "}\n", encoding="utf-8")
    print("patched Scroll.java: reverse runestone alchemy")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_alchemy_scroll.py <Scroll.java>")
    main(Path(sys.argv[1]))
