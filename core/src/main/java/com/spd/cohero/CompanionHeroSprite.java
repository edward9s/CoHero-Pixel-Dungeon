package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;

/**
 * Stock HeroSprite behavior bound to the autonomous Hero instead of Dungeon.hero.
 *
 * The small upstream HeroSprite seam added by CoHero makes HeroSprite owner-aware and keeps camera
 * follow restricted to Dungeon.hero, so this class only has to bind the correct Hero instance.
 */
public final class CompanionHeroSprite extends HeroSprite {

    public CompanionHeroSprite(CompanionHero companion) {
        super(companion);
    }
}
