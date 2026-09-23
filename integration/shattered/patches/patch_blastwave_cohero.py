#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_blastwave_cohero.py <WandOfBlastWave.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """\t@Override
\tpublic void fx(Ballistica bolt, Callback callback) {
\t\tMagicMissile.boltFromChar( curUser.sprite.parent,
\t\t\t\tMagicMissile.FORCE,
\t\t\t\tcurUser.sprite,
\t\t\t\tbolt.collisionPos,
\t\t\t\tcallback);
\t\tSample.INSTANCE.play(Assets.Sounds.ZAP);
\t}
"""

new = """\t@Override
\tpublic void fx(Ballistica bolt, Callback callback) {
\t\tChar user = zapUser();
\t\tMagicMissile.boltFromChar( user.sprite.parent,
\t\t\t\tMagicMissile.FORCE,
\t\t\t\tuser.sprite,
\t\t\t\tbolt.collisionPos,
\t\t\t\tcallback);
\t\tSample.INSTANCE.play(Assets.Sounds.ZAP);
\t}
"""

if "Char user = zapUser();" in text:
    raise SystemExit("CoHero BlastWave visual seam is already present")
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one BlastWave fx anchor, found {text.count(old)}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
