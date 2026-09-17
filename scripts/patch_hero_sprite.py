#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_hero_sprite.py <HeroSprite.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old_ctor = """	public HeroSprite() {
		super();
		
		texture( Dungeon.hero.heroClass.spritesheet() );
		updateArmor();
		
		link( Dungeon.hero );

		if (ch.isAlive())
			idle();
		else
			die();
	}
"""
new_ctor = """	public HeroSprite() {
		this( Dungeon.hero );
	}

	protected HeroSprite( Hero hero ) {
		super();

		if (hero == null) {
			throw new IllegalArgumentException("HeroSprite owner must not be null");
		}

		texture( hero.heroClass.spritesheet() );
		updateArmor( hero );

		link( hero );

		if (ch.isAlive())
			idle();
		else
			die();
	}
"""

old_update = """	public void updateArmor() {

		TextureFilm film = new TextureFilm( tiers(), Dungeon.hero.tier(), FRAME_WIDTH, FRAME_HEIGHT );
"""
new_update = """	public void updateArmor() {
		if (!(ch instanceof Hero)) {
			throw new IllegalStateException("HeroSprite is not linked to a Hero");
		}
		updateArmor( (Hero)ch );
	}

	private void updateArmor( Hero hero ) {

		TextureFilm film = new TextureFilm( tiers(), hero.tier(), FRAME_WIDTH, FRAME_HEIGHT );
"""

old_alive = """		if (Dungeon.hero.isAlive())
			idle();
		else
			die();
	}
"""
new_alive = """		if (hero.isAlive())
			idle();
		else
			die();
	}
"""

old_place = """		if (Game.scene() instanceof GameScene) Camera.main.panFollow(this, 5f);"""
new_place = """		if (Game.scene() instanceof GameScene && ch == Dungeon.hero) Camera.main.panFollow(this, 5f);"""

old_pan = """		Camera.main.panFollow(this, 20f);"""
new_pan = """		if (ch == Dungeon.hero) Camera.main.panFollow(this, 20f);"""

checks = [
    (old_ctor, new_ctor, "HeroSprite constructor", 1),
    (old_update, new_update, "HeroSprite armor owner", 1),
    (old_alive, new_alive, "HeroSprite armor alive check", 1),
    (old_place, new_place, "HeroSprite place camera", 1),
    (old_pan, new_pan, "HeroSprite move/jump camera", 2),
]

for old, new, label, expected in checks:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s), found {count}")
    text = text.replace(old, new)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
