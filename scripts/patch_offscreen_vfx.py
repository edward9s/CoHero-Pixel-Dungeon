#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_offscreen_vfx.py <Pushing.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """	@Override
	protected boolean act() {
		Actor.remove( Pushing.this );

		if (sprite != null && sprite.parent != null) {
			if (Dungeon.level.heroFOV[from] || Dungeon.level.heroFOV[to]){
				sprite.visible = true;
			}
			if (effect == null) {
				new Effect();
			}
		} else {
			return true;
		}

		//so that all pushing effects at the same time go simultaneously
		for ( Actor actor : Actor.all() ){
			if (actor instanceof Pushing && actor.cooldown() == 0)
				return true;
		}
		return false;

	}
"""

new = """	@Override
	protected boolean act() {
		Actor.remove( Pushing.this );

		boolean heroVisible = Dungeon.level.heroFOV[from] || Dungeon.level.heroFOV[to];
		if (!heroVisible) {
			// Remote push/pull gameplay must not suspend Actor.process(). Run the gameplay
			// callback now; if CoHero FOV exposes the movement, keep only a cosmetic Effect.
			Callback gameplayCallback = callback;
			callback = null;

			if (sprite != null && sprite.parent != null) {
				if (com.spd.cohero.CoHeroPresentation.shouldShow(from, to)) {
					sprite.visible = true;
					if (effect == null) {
						new Effect();
					}
				} else {
					sprite.point(sprite.worldToCamera(to));
				}
			}

			if (gameplayCallback != null) {
				gameplayCallback.call();
			}
			return true;
		}

		if (sprite != null && sprite.parent != null) {
			sprite.visible = true;
			if (effect == null) {
				new Effect();
			}
		} else {
			return true;
		}

		//so that all pushing effects at the same time go simultaneously
		for ( Actor actor : Actor.all() ){
			if (actor instanceof Pushing && actor.cooldown() == 0)
				return true;
		}
		return false;

	}
"""

if text.count(old) != 1:
    raise SystemExit(
        f"expected exactly one Pushing.act anchor, found {text.count(old)}"
    )

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
