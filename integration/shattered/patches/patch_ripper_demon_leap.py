#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import insert_after_code_once, java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_ripper_demon_leap.py <RipperDemon.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))

anchor = "\tprivate int leapPos = -1;\n\tprivate float leapCooldown = 0;\n"
addition = (
    "\n\tpublic int coHeroLeapTarget() {\n"
    "\t\treturn rooted || state != HUNTING ? -1 : leapPos;\n"
    "\t}\n"
)

if "coHeroLeapTarget" in text:
    raise SystemExit("CoHero Ripper Demon leap hook is already present")
text = java_source(insert_after_code_once(text, anchor, addition, "Ripper Demon leap"))
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
