#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_level_companion_chasm.py <Level.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """			if (pit[ch.pos]){
				if (ch == Dungeon.hero) {
					Chasm.heroFall(ch.pos);
				} else if (ch instanceof Mob) {
					Chasm.mobFall( (Mob)ch );
				}
				return;
			}
"""
new = """			if (pit[ch.pos]){
				if (ch == Dungeon.hero) {
					Chasm.heroFall(ch.pos);
				} else if (com.spd.cohero.CoHero.handleCompanionChasm(ch)) {
					// Companion death ends the run; it never changes floors independently.
				} else if (ch instanceof Mob) {
					Chasm.mobFall( (Mob)ch );
				}
				return;
			}
"""

if new in text:
    raise SystemExit("CoHero companion chasm hook is already present")
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Level chasm anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
