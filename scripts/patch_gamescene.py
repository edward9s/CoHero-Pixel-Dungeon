#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gamescene.py <GameScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

ready_marker = "\t\tcom.spd.cohero.CoHero.onGameSceneReady();"
ready_anchor = (
    "\t\tfor (Mob mob : Dungeon.level.mobs) {\n"
    "\t\t\taddMobSprite( mob );\n"
    "\t\t}\n"
)

if ready_marker in text:
    raise SystemExit("CoHero GameScene ready hook is already present")
if text.count(ready_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene mob-loading anchor, found {text.count(ready_anchor)}")
text = text.replace(ready_anchor, ready_anchor + "\n" + ready_marker + "\n", 1)

add_anchor = """	public static void add( Mob mob ) {
		add( mob, 0);
	}

"""
add_method = """	public static void addCoHero( com.spd.cohero.CompanionHero companion ) {
		if (companion == null) {
			throw new IllegalArgumentException("CoHero companion must not be null");
		}
		if (scene == null) {
			throw new IllegalStateException("Cannot add CoHero without an active GameScene");
		}

		com.spd.cohero.CompanionHeroSprite sprite =
				new com.spd.cohero.CompanionHeroSprite(companion);
		sprite.visible = sprite.visibleOutOfFFOV || Dungeon.level.heroFOV[companion.pos];
		scene.mobs.add(sprite);
		sortMobSprites();

		Actor.add(companion);
		companion.spendToWhole();
	}

"""

if add_method in text:
    raise SystemExit("CoHero GameScene second-Hero add hook is already present")
if text.count(add_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene add(Mob) anchor, found {text.count(add_anchor)}")
text = text.replace(add_anchor, add_method + add_anchor, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
