#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_delayed_rockfall.py <DelayedRockFall.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

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

addition = replacement[len(anchor):]
text = java_source(insert_after_code_once(
    text, anchor, addition, "DelayedRockFall hazard hook"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
