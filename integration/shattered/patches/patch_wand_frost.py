#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

from java_patch import replace_code_once as replace_once

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_frost.py <WandOfFrost.java>")

path = Path(sys.argv[1])
frost = java_source(path.read_text(encoding="utf-8"))


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
