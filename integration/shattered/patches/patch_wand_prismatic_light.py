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
if "curUser" not in prismatic:
    raise SystemExit("expected PrismaticLight curUser references")
prismatic = prismatic.replace("curUser", "zapUser()")


path.write_text(prismatic, encoding="utf-8")
print(f"patched {path}")
