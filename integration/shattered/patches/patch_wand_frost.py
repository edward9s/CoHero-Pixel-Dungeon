#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_frost.py <WandOfFrost.java>")

path = Path(sys.argv[1])
frost = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Frost: only the visual path is Hero-static.
frost = replace_once(
    frost,
    """	public void fx(Ballistica bolt, Callback callback) {
		MagicMissile.boltFromChar(curUser.sprite.parent,
				MagicMissile.FROST,
				curUser.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
    """	public void fx(Ballistica bolt, Callback callback) {
		Char user = zapUser();
		MagicMissile.boltFromChar(user.sprite.parent,
				MagicMissile.FROST,
				user.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play(Assets.Sounds.ZAP);
	}
""",
    "Frost fx",
)


path.write_text(frost, encoding="utf-8")
print(f"patched {path}")
