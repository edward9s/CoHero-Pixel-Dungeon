#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_delayed_rockfall.py <DelayedRockFall.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

anchor = """	@Override
	public void fx(boolean on) {
		if (on && rockPositions != null){
"""
replacement = """	@Override
	public void fx(boolean on) {
		if (on && rockPositions != null){
			// Re-register persisted telegraphs when restored rockfall buffs rebuild their FX.
			for (int cell : rockPositions) {
				com.spd.cohero.CoHeroHazards.warn(cell, cooldown());
			}
"""

if text.count(anchor) != 1:
    raise SystemExit(f"expected exactly one DelayedRockFall fx anchor, found {text.count(anchor)}")

text = text.replace(anchor, replacement, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
