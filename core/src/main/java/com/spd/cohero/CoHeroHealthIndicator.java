package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.ui.CharHealthIndicator;

/**
 * CoHero health is always shown while its sprite is visible, including at full HP.
 */
public class CoHeroHealthIndicator extends CharHealthIndicator {

    public CoHeroHealthIndicator(Char target) {
        super(target);
    }

    @Override
    public void update() {
        super.update();

        Char target = target();
        visible = target != null
                && target.isAlive()
                && target.isActive()
                && target.sprite != null
                && target.sprite.visible;
    }
}
