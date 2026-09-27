#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ring_info_owner.py <rings-directory>")

rings_dir = Path(sys.argv[1])
if not rings_dir.is_dir():
    raise SystemExit(f"rings directory not found: {rings_dir}")

RING_FILES = (
    "RingOfAccuracy.java",
    "RingOfArcana.java",
    "RingOfElements.java",
    "RingOfEnergy.java",
    "RingOfEvasion.java",
    "RingOfForce.java",
    "RingOfFuror.java",
    "RingOfHaste.java",
    "RingOfMight.java",
    "RingOfSharpshooting.java",
    "RingOfTenacity.java",
    "RingOfWealth.java",
)


def stats_info_span(text: str, path: Path) -> tuple[int, int]:
    marker = "public String statsInfo()"
    if text.count(marker) != 1:
        raise SystemExit(
            f"expected exactly one statsInfo method in {path.name}, found {text.count(marker)}"
        )

    method_start = text.index(marker)
    body_start = text.index("{", method_start)
    depth = 0
    for index in range(body_start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return method_start, index + 1
    raise SystemExit(f"unterminated statsInfo method in {path.name}")


for filename in RING_FILES:
    path = rings_dir / filename
    if not path.is_file():
        raise SystemExit(f"expected ring source not found: {path}")

    text = path.read_text(encoding="utf-8")
    start, end = stats_info_span(text, path)
    method = text[start:end]
    original = method

    method = method.replace(
        "isEquipped(Dungeon.hero)",
        "isEquippedForStats()",
    )
    method = method.replace(
        "combinedBuffedBonus(Dungeon.hero)",
        "combinedBuffedBonusForStats()",
    )
    method = method.replace(
        "combinedBonus(Dungeon.hero)",
        "combinedBonusForStats()",
    )

    if filename == "RingOfMight.java":
        anchor = "getBonus(Dungeon.hero, Might.class)"
        if anchor not in method:
            raise SystemExit(f"RingOfMight combined bonus anchor missing in {path}")
        method = method.replace(anchor, "combinedBonusForStats()")

    if filename == "RingOfForce.java":
        anchor = "Dungeon.hero != null ? Dungeon.hero.STR() : 10"
        if anchor not in method:
            raise SystemExit(f"RingOfForce strength context anchor missing in {path}")
        method = method.replace(
            anchor,
            "statsOwner() != null ? statsOwner().STR() : 10",
        )

    if method == original:
        raise SystemExit(f"statsInfo owner patch made no changes in {path.name}")
    if "isEquipped(Dungeon.hero)" in method:
        raise SystemExit(f"Hero-only equipped check remains in {path.name} statsInfo")
    if "combinedBuffedBonus(Dungeon.hero)" in method:
        raise SystemExit(f"Hero-only combined buffed bonus remains in {path.name} statsInfo")

    text = text[:start] + method + text[end:]
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")
