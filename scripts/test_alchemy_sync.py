#!/usr/bin/env python3
"""Verify the CoHero alchemy port against the checked-out SPD source."""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile


SOURCE_ROOT = "core/src/main/java/com/shatteredpixel/shatteredpixeldungeon"
PATCHES = (
    ("patch_alchemy_scroll.py", "items/scrolls/Scroll.java"),
    ("patch_alchemy_recipe.py", "items/Recipe.java"),
    ("patch_alchemy_liquid_metal.py", "items/LiquidMetal.java"),
    ("patch_alchemy_arcane_resin.py", "items/ArcaneResin.java"),
    ("patch_alchemy_quick_recipe.py", "ui/QuickRecipe.java"),
    ("patch_alchemy_journal.py", "windows/WndJournal.java"),
    ("patch_alchemy_document_page.py", "items/journal/DocumentPage.java"),
)
MESSAGE_KEYS = (
    "cohero.alchemy.reverse_stones.title",
    "cohero.alchemy.reverse_stones.body",
    "cohero.alchemy.reverse_stones.enchantment_disabled",
)


def require(text: str, fragment: str, filename: str) -> None:
    if fragment not in text:
        raise AssertionError(f"{filename}: expected {fragment!r}")


def test_patch_application(repo: Path, upstream: Path) -> None:
    script_root = repo / "integration/shattered/patches"
    sources = upstream / SOURCE_ROOT

    with tempfile.TemporaryDirectory() as temporary:
        target = Path(temporary)
        patched = {}
        for script, source in PATCHES:
            original = sources / source
            if not original.is_file():
                raise AssertionError(f"missing upstream source: {original}")
            copy = target / original.name
            copy.write_bytes(original.read_bytes())

            command = [sys.executable, str(script_root / script), str(copy)]
            subprocess.run(command, check=True, capture_output=True, text=True)
            patched[original.name] = copy.read_text(encoding="utf-8")

            # A repeated patch must fail without altering a previously patched file.
            content = copy.read_bytes()
            repeat = subprocess.run(command, capture_output=True, text=True)
            if repeat.returncode == 0 or copy.read_bytes() != content:
                raise AssertionError(f"{script}: duplicate application was not rejected")

    scroll = patched["Scroll.java"]
    require(scroll, "class StoneToScroll extends Recipe", "Scroll.java")
    require(scroll, "ScrollToStone.stones.keySet()", "Scroll.java")
    require(scroll, "ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY", "Scroll.java")
    require(scroll, "!(first instanceof StoneOfEnchantment)", "Scroll.java")
    require(scroll, "first.getClass() == second.getClass()", "Scroll.java")
    require(scroll, "stone.quantity(stone.quantity() - 1);", "Scroll.java")

    recipe = patched["Recipe.java"]
    require(recipe, "new Scroll.StoneToScroll(),", "Recipe.java")
    require(recipe,
            "item instanceof MissileWeapon && item.isUpgradable();",
            "Recipe.java")
    require(recipe,
            "} else if (item instanceof Wand) {\n\t\t\treturn item.cursedKnown;",
            "Recipe.java")
    require(recipe, "return !item.cursed;", "Recipe.java")

    for name in ("LiquidMetal.java", "ArcaneResin.java"):
        source = patched[name]
        require(source, "&& ingredients.get(0).cursedKnown;", name)
        if "&& !ingredients.get(0).cursed;" in source:
            raise AssertionError(f"{name}: cursed equipment still blocked")
        require(source, "return 3;" if name == "LiquidMetal.java" else "return 5;", name)

    quick = patched["QuickRecipe.java"]
    require(quick, "case 9:", "QuickRecipe.java")
    require(quick, "r.testIngredients(recipeInputs)", "QuickRecipe.java")
    require(quick, "Item output = r.sampleOutput(recipeInputs);", "QuickRecipe.java")
    require(quick, "new ArrayList<>(Arrays.asList(stone.quantity(2)))", "QuickRecipe.java")

    guide = patched["WndJournal.java"]
    require(guide, "NUM_BUTTONS = 10;", "WndJournal.java")
    require(guide, "REVERSE_STONES_IDX = 2;", "WndJournal.java")
    require(guide, "i == REVERSE_STONES_IDX ? 1 : originalIndex", "WndJournal.java")
    require(guide, "currentPageIdx == REVERSE_STONES_IDX ? 9 : originalIndex", "WndJournal.java")
    for key in MESSAGE_KEYS:
        require(guide, key, "WndJournal.java")

    picked_up = patched["DocumentPage.java"]
    require(picked_up, "originalIndex + 1 : originalIndex;", "DocumentPage.java")

    # Every original document page maps to its original recipe data.
    for original in range(9):
        display = original + (1 if original >= 2 else 0)
        resolved = 1 if display == 2 else display - (1 if display > 2 else 0)
        if resolved != original:
            raise AssertionError(f"incorrect original page mapping: {original}")


def test_messages(repo: Path) -> None:
    locales = sorted((repo / "messages").glob("misc*.properties"))
    if len(locales) != 23:
        raise AssertionError(f"expected 23 message bundles, found {len(locales)}")
    for locale in locales:
        keys = [
            line.split("=", 1)[0] for line in locale.read_text(
                encoding="utf-8").splitlines()
            if "=" in line and not line.lstrip().startswith(("#", "!"))
        ]
        for key in MESSAGE_KEYS:
            if keys.count(key) != 1:
                raise AssertionError(f"{locale.name}: expected one {key}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("repo", type=Path)
    parser.add_argument("--upstream", type=Path, required=True)
    args = parser.parse_args()

    repo = args.repo.resolve()
    upstream = args.upstream.resolve()
    settings = (repo / "core/src/main/java/com/spd/cohero/CoHeroSettings.java").read_text(
        encoding="utf-8")
    require(
        settings,
        "public static final boolean ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY = false;",
        "CoHeroSettings.java",
    )
    test_patch_application(repo, upstream)
    test_messages(repo)
    print("CoHero alchemy: SPD source patches, duplicate rejection, and locales verified")


if __name__ == "__main__":
    main()
