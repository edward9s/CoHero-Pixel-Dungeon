#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_consumable_owners.py <spd-core-java-root>")

root = Path(sys.argv[1])
items = root / "com/shatteredpixel/shatteredpixeldungeon/items"

# These remaining singleton references are deliberately global rather than item-user ownership:
# - Potion.java / Scroll.java: discovery bookkeeping is run-global.
# - ElixirOfMight.java: description preview uses the main run hero as a fallback display context.
# - ScrollOfTeleportation.java: main-camera/FOV/UI behavior explicitly distinguishes Dungeon.hero.
allowed = {
    "potions/Potion.java": 1,
    "potions/elixirs/ElixirOfMight.java": 1,
    "scrolls/Scroll.java": 1,
    "scrolls/ScrollOfTeleportation.java": 8,
}

seen = {}
for category in ("potions", "scrolls"):
    for path in sorted((items / category).rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        count = text.count("Dungeon.hero")
        if count:
            rel = path.relative_to(items).as_posix()
            seen[rel] = count

if seen != allowed:
    lines = ["Unsupported consumable owner contract:"]
    for rel in sorted(set(seen) | set(allowed)):
        actual = seen.get(rel, 0)
        expected = allowed.get(rel, 0)
        if actual != expected:
            lines.append(f"  {rel}: expected {expected} Dungeon.hero reference(s), found {actual}")
    raise SystemExit("\n".join(lines))

print("consumable owner contract verified")
