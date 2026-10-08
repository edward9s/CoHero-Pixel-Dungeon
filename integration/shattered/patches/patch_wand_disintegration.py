#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

from java_patch import replace_code_once as replace_once

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_disintegration.py <WandOfDisintegration.java>")

path = Path(sys.argv[1])
disintegration = java_source(path.read_text(encoding="utf-8"))


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
