#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_presentation_batch.py <Hero.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """\t\t\tif (resting) {
\t\t\t\tspendConstant( TIME_TO_REST );
\t\t\t\tnext();
\t\t\t} else {
\t\t\t\tready();
\t\t\t}
"""

new = """\t\t\tif (resting) {
\t\t\t\tspendConstant( TIME_TO_REST );
\t\t\t\tnext();
\t\t\t} else if (com.spd.cohero.CoHeroPresentation.awaitForeground(this)) {
\t\t\t\t// CoHero gameplay is already resolved. Delay only the return of player input until
\t\t\t\t// the slowest visible CoHero presentation in this batch has completed.
\t\t\t} else {
\t\t\t\tready();
\t\t\t}
"""

if text.count(old) != 1:
    raise SystemExit(
        f"expected exactly one Hero ready/presentation anchor, found {text.count(old)}"
    )

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
