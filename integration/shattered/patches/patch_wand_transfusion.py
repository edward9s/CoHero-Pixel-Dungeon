#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_transfusion.py <WandOfTransfusion.java>")

path = Path(sys.argv[1])
transfusion = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Transfusion: CoHero may support the player Hero; otherwise preserve normal Mob semantics.
if "curUser" not in transfusion:
    raise SystemExit("expected Transfusion curUser references")
transfusion = transfusion.replace("curUser", "zapUser()")
transfusion = replace_once(
    transfusion,
    "if (ch instanceof Mob){",
    "if (ch instanceof Mob || (coHeroCasting() && ch == Dungeon.hero)){",
    "Transfusion CoHero support target",
)
transfusion = replace_once(
    transfusion,
    "if (!zapUser().isAlive()){",
    "if (!zapUser().isAlive() && zapUser() == Dungeon.hero){",
    "Transfusion Hero death handling",
)


path.write_text(transfusion, encoding="utf-8")
print(f"patched {path}")
