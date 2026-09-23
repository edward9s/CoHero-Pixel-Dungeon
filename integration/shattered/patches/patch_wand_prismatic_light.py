#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_prismatic_light.py <WandOfPrismaticLight.java>")

path = Path(sys.argv[1])
prismatic = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Prismatic light: map effects are generic; only caster light/beam source was static.
expected_user_lines = (
    "Buff.prolong( curUser, Light.class, 2f + buffedLvl());",
    "Buff.prolong( curUser, Light.class, 10f+buffedLvl()*5);",
    "curUser.sprite.parent.add(",
    "new Beam.LightRay(curUser.sprite.center(), DungeonTilemap.raisedTileCenterToWorld(beam.collisionPos)));",
)
actual_user_lines = tuple(line.strip() for line in prismatic.splitlines() if "curUser" in line)
if sorted(actual_user_lines) != sorted(expected_user_lines):
    raise SystemExit("unexpected PrismaticLight curUser references")
prismatic = prismatic.replace("curUser", "zapUser()")


path.write_text(prismatic, encoding="utf-8")
print(f"patched {path}")
