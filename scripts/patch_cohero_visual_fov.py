#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 4:
    raise SystemExit("usage: patch_cohero_visual_fov.py <Char.java> <GameScene.java> <FogOfWar.java>")

char_path = Path(sys.argv[1])
scene_path = Path(sys.argv[2])
fog_path = Path(sys.argv[3])

char = char_path.read_text(encoding="utf-8")
scene = scene_path.read_text(encoding="utf-8")
fog = fog_path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

char = replace_once(
    char,
    """		if (sprite.isVisible() && sprite.parent != null && (Dungeon.level.heroFOV[from] || Dungeon.level.heroFOV[to])) {
""",
    """		if (sprite.isVisible() && sprite.parent != null
				&& (com.spd.cohero.CoHero.isVisibleToPlayer(from)
				|| com.spd.cohero.CoHero.isVisibleToPlayer(to))) {
""",
    "Char moveSprite visibility",
)

scene = replace_once(
    scene,
    """		sprite.visible = sprite.visibleOutOfFFOV || Dungeon.level.heroFOV[mob.pos];
""",
    """		sprite.visible = sprite.visibleOutOfFFOV || com.spd.cohero.CoHero.isVisibleToPlayer(mob.pos);
""",
    "GameScene addMobSprite visibility",
)

scene = replace_once(
    scene,
    """						mob.sprite.visible = mob.sprite.visibleOutOfFFOV || Dungeon.level.heroFOV[mob.pos];
""",
    """						mob.sprite.visible = mob.sprite.visibleOutOfFFOV || com.spd.cohero.CoHero.isVisibleToPlayer(mob.pos);
""",
    "GameScene afterObserve visibility",
)

fog = replace_once(
    fog,
    """			updateTexture(Dungeon.level.heroFOV, Dungeon.level.visited, Dungeon.level.mapped);
""",
    """			updateTexture(com.spd.cohero.CoHero.renderFieldOfView(), Dungeon.level.visited, Dungeon.level.mapped);
""",
    "FogOfWar render visibility",
)

char_path.write_text(char, encoding="utf-8")
scene_path.write_text(scene, encoding="utf-8")
fog_path.write_text(fog, encoding="utf-8")

print(f"patched {char_path}")
print(f"patched {scene_path}")
print(f"patched {fog_path}")
