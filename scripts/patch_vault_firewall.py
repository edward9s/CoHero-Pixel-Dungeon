#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_vault_firewall.py <VaultBossElemental.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = """	public static class FireWall extends Buff {

		private int[] cells = new int[0];
		private int direction;

		private int left = 11; //always the same amount
"""

replacement = """	public static class FireWall extends Buff {

		private int[] cells = new int[0];
		private int direction;

		private int left = 11; //always the same amount

		/**
		 * CoHero pathing probe. The next wall act burns the current strip and the strip one
		 * step ahead. One additional forward strip is reserved as a movement buffer so the
		 * companion does not deliberately step immediately in front of the advancing wall.
		 */
		public boolean coHeroDangerAt(int cell) {
			for (int base : cells) {
				for (int step = 0; step <= 2; step++) {
					int danger = base + step * direction;
					if (danger == cell
							&& Dungeon.level.insideMap(danger)
							&& !Dungeon.level.solid[danger]) {
						return true;
					}
				}
			}
			return false;
		}
"""

if "coHeroDangerAt" in text:
    raise SystemExit("CoHero FireWall seam is already present")
if text.count(anchor) != 1:
    raise SystemExit(f"expected exactly one FireWall anchor, found {text.count(anchor)}")

path.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
print(f"patched {path}")
