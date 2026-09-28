#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_attack_indicator.py <AttackIndicator.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """\tpublic static void target(Char target ) {
\t\tif (target == null) return;
\t\tsynchronized (instance) {
"""

new = """\tpublic static void target(Char target ) {
\t\tif (target == null) return;
\t\tif (!com.spd.cohero.CoHero.heroCanSee(target.pos)) {
\t\t\tupdateState();
\t\t\treturn;
\t\t}
\t\tsynchronized (instance) {
"""

if "CoHero.heroCanSee(target.pos)" in text:
    raise SystemExit("CoHero AttackIndicator visibility gate is already present")
if text.count(old) != 1:
    raise SystemExit(
        f"expected exactly one AttackIndicator.target anchor, found {text.count(old)}"
    )

text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
