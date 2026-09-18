package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.watabou.noosa.TextureFilm;
import com.watabou.utils.PointF;

/**
 * Hero-shaped sprite for the autonomous companion.
 *
 * It uses the selected HeroClass spritesheet, but deliberately does not use
 * HeroSprite itself because HeroSprite is hard-wired to Dungeon.hero.
 */
public class CoHeroAllySprite extends CharSprite {

    private static final int FRAME_WIDTH = 12;
    private static final int FRAME_HEIGHT = 15;
    private static final int RUN_FRAMERATE = 20;

    public CoHeroAllySprite() {
        super();

        HeroClass heroClass = CoHero.companionClass();
        if (heroClass == null) {
            throw new IllegalStateException("CoHeroAllySprite has no selected HeroClass");
        }

        texture(heroClass.spritesheet());
        updateArmor(0);
    }

    @Override
    public void link(Char ch) {
        if (!(ch instanceof CoHeroAlly)) {
            throw new IllegalArgumentException("CoHeroAllySprite can only link to CoHeroAlly");
        }
        super.link(ch);
        updateArmor();
    }

    public void updateArmor() {
        if (ch == null) {
            return;
        }
        if (!(ch instanceof CoHeroAlly)) {
            throw new IllegalStateException("CoHeroAllySprite is linked to a non-companion Char");
        }
        updateArmor(((CoHeroAlly) ch).armorTier());
    }

    private void updateArmor(int tier) {
        if (tier < 0 || tier > 6) {
            throw new IllegalArgumentException("Unsupported companion armor tier: " + tier);
        }

        TextureFilm film = new TextureFilm(HeroSprite.tiers(), tier, FRAME_WIDTH, FRAME_HEIGHT);

        idle = new Animation(1, true);
        idle.frames(film, 0, 0, 0, 1, 0, 0, 1, 1);

        run = new Animation(RUN_FRAMERATE, true);
        run.frames(film, 2, 3, 4, 5, 6, 7);

        die = new Animation(20, false);
        die.frames(film, 8, 9, 10, 11, 12, 11);

        attack = new Animation(15, false);
        attack.frames(film, 13, 14, 15, 0);

        zap = attack.clone();

        operate = new Animation(8, false);
        operate.frames(film, 16, 17, 16, 17);

        idle();
    }

    @Override
    public void bloodBurstA(PointF from, int damage) {
        // Match the stock hero presentation: no blood burst for human heroes.
    }
}
