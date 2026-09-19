#!/usr/bin/env python3
import re
import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("usage: extract_version.py <CoHeroVersion.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

match = re.search(
    r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"\s*;',
    text,
)
if match is None:
    raise SystemExit("CoHero VERSION constant not found")

print(match.group(1))
