#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: patch_cohero_visual_fov.py <GameScene.java> <FogOfWar.java>")

scene_path = Path(sys.argv[1])
fog_path = Path(sys.argv[2])

scene = scene_path.read_text(encoding="utf-8")
fog = fog_path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

scene = replace_once(
    scene,
    """\t\tsprite.visible = sprite.visibleOutOfFFOV || Dungeon.level.heroFOV[mob.pos];
""",
    """\t\tsprite.visible = sprite.visibleOutOfFFOV || com.spd.cohero.CoHero.isVisibleToPlayer(mob.pos);
""",
    "GameScene addMobSprite visibility",
)

scene = replace_once(
    scene,
    """\t\t\t\t\t\tmob.sprite.visible = mob.sprite.visibleOutOfFFOV || Dungeon.level.heroFOV[mob.pos];
""",
    """\t\t\t\t\t\tmob.sprite.visible = mob.sprite.visibleOutOfFFOV || com.spd.cohero.CoHero.isVisibleToPlayer(mob.pos);
""",
    "GameScene afterObserve visibility",
)

fog = replace_once(
    fog,
    """\t\t\tupdateTexture(Dungeon.level.heroFOV, Dungeon.level.visited, Dungeon.level.mapped);
""",
    """\t\t\tupdateTexture(com.spd.cohero.CoHero.renderFieldOfView(), Dungeon.level.visited, Dungeon.level.mapped);
""",
    "FogOfWar render visibility",
)

scene_path.write_text(scene, encoding="utf-8")
fog_path.write_text(fog, encoding="utf-8")

print(f"patched {scene_path}")
print(f"patched {fog_path}")
