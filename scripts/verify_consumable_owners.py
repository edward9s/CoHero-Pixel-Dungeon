#!/usr/bin/env python3
from collections import Counter
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_consumable_owners.py <spd-core-java-root>")

root = Path(sys.argv[1])
items = root / "com/shatteredpixel/shatteredpixeldungeon/items"
if not items.is_dir():
    raise SystemExit(f"items directory not found: {items}")

# Remaining singleton references are deliberately NOT item ownership:
# - Potion/Scroll base classes: run-global discovery bookkeeping.
# - ElixirOfMight: description preview fallback.
# - ScrollOfTeleportation: primary camera/FOV/UI distinction.
# - ScrollOfRemoveCurse one-argument helper: legacy external call sites such as WaterOfHealth.
allowed = Counter({
    ("potions/Potion.java", "if (Dungeon.hero.isAlive()) {"): 1,
    (
        "potions/elixirs/ElixirOfMight.java",
        'return Messages.get(this, "desc", HTBoost.boost(Dungeon.hero != null ? Dungeon.hero.HT : 20));',
    ): 1,
    ("scrolls/Scroll.java", "if (Dungeon.hero.isAlive()) {"): 1,
    ("scrolls/ScrollOfTeleportation.java", "if (ch == Dungeon.hero){"): 1,
    ("scrolls/ScrollOfTeleportation.java", "if (ch == Dungeon.hero) {"): 3,
    ("scrolls/ScrollOfTeleportation.java", "Dungeon.hero.interrupt();"): 2,
    (
        "scrolls/ScrollOfTeleportation.java",
        "if (Dungeon.level.heroFOV[ch.pos] && ch != Dungeon.hero ) {",
    ): 1,
    (
        "scrolls/ScrollOfTeleportation.java",
        "if (Dungeon.level.heroFOV[pos] || ch == Dungeon.hero ) {",
    ): 1,
    (
        "scrolls/ScrollOfRemoveCurse.java",
        "return uncursable(Dungeon.hero, item);",
    ): 1,
})

actual = Counter()
for category in ("potions", "scrolls"):
    base = items / category
    if not base.is_dir():
        raise SystemExit(f"missing consumable source directory: {base}")
    for p in base.rglob("*.java"):
        rel = p.relative_to(items).as_posix()
        for raw in p.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if "Dungeon.hero" in line:
                actual[(rel, line)] += 1

unexpected = actual - allowed
missing = allowed - actual
if unexpected or missing:
    lines = ["Unsupported CoHero consumable owner contract:"]
    if unexpected:
        lines.append("Unexpected direct Dungeon.hero references:")
        for (rel, line), count in sorted(unexpected.items()):
            lines.append(f"  {rel} x{count}: {line}")
    if missing:
        lines.append("Expected allowlisted reference changed or disappeared:")
        for (rel, line), count in sorted(missing.items()):
            lines.append(f"  {rel} x{count}: {line}")
    raise SystemExit("\n".join(lines))

print(
    "consumable owner contract verified: "
    f"{sum(actual.values())} explicitly allowlisted singleton reference(s)"
)
