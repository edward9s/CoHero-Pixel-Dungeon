#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_corrosion.py <WandOfCorrosion.java>")

path = Path(sys.argv[1])
corrosion = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Corrosion: gas ownership is generic; only the projectile source is Hero-static.
corrosion = replace_once(
    corrosion,
    """		MagicMissile.boltFromChar(
				curUser.sprite.parent,
				MagicMissile.CORROSION,
				curUser.sprite,
""",
    """		Char user = zapUser();
		MagicMissile.boltFromChar(
				user.sprite.parent,
				MagicMissile.CORROSION,
				user.sprite,
""",
    "Corrosion fx caster",
)


path.write_text(corrosion, encoding="utf-8")
print(f"patched {path}")
