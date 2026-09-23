#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_offscreen_vfx.py <effects-dir>")

effects_dir = Path(sys.argv[1])

# Pushing is an Actor purely to synchronize its presentation. When Hero cannot see either
# endpoint, run the gameplay callback immediately and leave only an optional cosmetic Effect.
pushing_path = effects_dir / "Pushing.java"
pushing = pushing_path.read_text(encoding="utf-8")

pushing_old = """	@Override
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

pushing_new = """	@Override
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

if pushing.count(pushing_old) != 1:
    raise SystemExit(
        f"expected exactly one Pushing.act anchor, found {pushing.count(pushing_old)}"
    )
pushing_path.write_text(pushing.replace(pushing_old, pushing_new, 1), encoding="utf-8")
print(f"patched {pushing_path}")

# Swap also uses an Actor only to wait for two cosmetic tweeners. Resolve positions immediately
# when both endpoints are outside Hero FOV, while allowing the already-created tweeners to finish
# visually without swapping gameplay state a second time.
swap_path = effects_dir / "Swap.java"
swap = swap_path.read_text(encoding="utf-8")

swap_field_anchor = """	private float delay;

	public Swap( Char ch1, Char ch2 ) {
"""
swap_field_patch = """	private float delay;
	private boolean gameplayResolved;

	public Swap( Char ch1, Char ch2 ) {
"""
if swap.count(swap_field_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Swap field anchor, found {swap.count(swap_field_anchor)}"
    )
swap = swap.replace(swap_field_anchor, swap_field_patch, 1)

swap_act_old = """	@Override
	protected boolean act() {
		return false;
	}
"""
swap_act_new = """	@Override
	protected boolean act() {
		boolean heroVisible = Dungeon.level.heroFOV[ch1.pos] || Dungeon.level.heroFOV[ch2.pos];
		if (!heroVisible) {
			Actor.remove(this);
			resolveGameplay();
			return true;
		}
		return false;
	}
"""
if swap.count(swap_act_old) != 1:
    raise SystemExit(
        f"expected exactly one Swap.act anchor, found {swap.count(swap_act_old)}"
    )
swap = swap.replace(swap_act_old, swap_act_new, 1)

swap_finish_old = """		if (eff1 == null && eff2 == null) {
			Actor.remove( this );
			next();

			int pos = ch1.pos;
			ch1.pos = ch2.pos;
			ch2.pos = pos;

			Dungeon.level.occupyCell(ch1 );
			Dungeon.level.occupyCell(ch2 );

			if (ch1 == Dungeon.hero || ch2 == Dungeon.hero) {
				Dungeon.observe();
				GameScene.updateFog();
			}
		}
	}
"""
swap_finish_new = """		if (eff1 == null && eff2 == null) {
			Actor.remove( this );
			resolveGameplay();
			next();
		}
	}

	private void resolveGameplay() {
		if (gameplayResolved) {
			return;
		}
		gameplayResolved = true;

		int pos = ch1.pos;
		ch1.pos = ch2.pos;
		ch2.pos = pos;

		Dungeon.level.occupyCell(ch1 );
		Dungeon.level.occupyCell(ch2 );

		if (ch1 == Dungeon.hero || ch2 == Dungeon.hero) {
			Dungeon.observe();
			GameScene.updateFog();
		}
	}
"""
if swap.count(swap_finish_old) != 1:
    raise SystemExit(
        f"expected exactly one Swap.finish gameplay block, found {swap.count(swap_finish_old)}"
    )
swap = swap.replace(swap_finish_old, swap_finish_new, 1)

swap_path.write_text(swap, encoding="utf-8")
print(f"patched {swap_path}")
