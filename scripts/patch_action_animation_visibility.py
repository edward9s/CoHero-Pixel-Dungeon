#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_action_animation_visibility.py <mobs-dir>")

mobs_dir = Path(sys.argv[1])

expected_counts = {
    "Mob.java": 1,
    "DM100.java": 1,
    "DM200.java": 2,
    "DM201.java": 1,
    "DM300.java": 4,
    "Guard.java": 2,
    "Golem.java": 2,
    "Spinner.java": 2,
    "Warlock.java": 1,
    "Elemental.java": 2,
    "CrystalWisp.java": 1,
    "Shaman.java": 1,
    "YogFist.java": 1,
    "quest/vault/VaultBossElemental.java": 1,
}

old = "sprite.visible || enemy.sprite.visible"
new = (
    "com.spd.cohero.CoHero.heroCanSee(pos) "
    "|| com.spd.cohero.CoHero.heroCanSee(enemy.pos)"
)

for relative, expected in expected_counts.items():
    path = mobs_dir / relative
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != expected:
        raise SystemExit(
            f"expected {expected} action-visibility anchor(s) in {path}, found {actual}"
        )
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"patched {path}")
