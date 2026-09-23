#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_disintegration.py <WandOfDisintegration.java>")

path = Path(sys.argv[1])
disintegration = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Disintegration: preserve the original beam, but source it from the actual caster.
disintegration = replace_once(
    disintegration,
    """		int cell = beam.path.get(Math.min(beam.dist, distance()));
		curUser.sprite.parent.add(new Beam.DeathRay(curUser.sprite.center(), DungeonTilemap.raisedTileCenterToWorld( cell )));
""",
    """		int cell = beam.path.get(Math.min(beam.dist, distance()));
		Char user = zapUser();
		user.sprite.parent.add(new Beam.DeathRay(user.sprite.center(), DungeonTilemap.raisedTileCenterToWorld( cell )));
""",
    "Disintegration fx caster",
)


path.write_text(disintegration, encoding="utf-8")
print(f"patched {path}")
