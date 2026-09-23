#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_cohero_actor_nonblocking.py <Actor.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """				if (acting instanceof Char && ((Char) acting).sprite != null) {
					// If it's character's turn to act, but its sprite
					// is moving, wait till the movement is over
"""

new = """				if (acting instanceof Char
						&& ((Char) acting).sprite != null
						&& !(acting instanceof com.spd.cohero.CoHeroAlly)
						&& (!(acting instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob)
								|| !((com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob) acting)
										.coHeroMovementPresentationNonBlocking())) {
					// Hero-related stock movement stays synchronized. CoHero and mobs currently
					// engaged with CoHero must never suspend Actor.process() for presentation.
"""

if text.count(old) != 1:
    raise SystemExit(
        f"expected exactly one Actor movement-wait anchor, found {text.count(old)}"
    )

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
