#!/usr/bin/env python3
from collections import Counter
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_consumable_owner.py <items-java-dir>")

root = Path(sys.argv[1])
if not root.is_dir():
    raise SystemExit(f"items directory not found: {root}")

# These are the only remaining Dungeon.hero references permitted under Potion/Scroll code.
# They are deliberately limited to global catalog state, main-camera/FOV behavior, a description
# fallback, and the legacy one-argument Remove Curse helper used outside item execution.
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
for subdir in ("potions", "scrolls"):
    base = root / subdir
    if not base.is_dir():
        raise SystemExit(f"missing consumable source directory: {base}")
    for path in base.rglob("*.java"):
        rel = path.relative_to(root).as_posix()
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if "Dungeon.hero" in line:
                actual[(rel, line)] += 1

unexpected = actual - allowed
missing = allowed - actual

if unexpected or missing:
    lines = ["CoHero consumable owner contract mismatch."]
    if unexpected:
        lines.append("Unexpected direct Dungeon.hero references:")
        for (path, line), count in sorted(unexpected.items()):
            lines.append(f"  {path} x{count}: {line}")
    if missing:
        lines.append("Expected allowlisted references changed or disappeared:")
        for (path, line), count in sorted(missing.items()):
            lines.append(f"  {path} x{count}: {line}")
    raise SystemExit("\n".join(lines))

print(f"verified consumable owner contract: {sum(actual.values())} allowlisted Dungeon.hero reference(s)")
