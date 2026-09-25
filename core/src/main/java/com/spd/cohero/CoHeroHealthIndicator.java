package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.CharHealthIndicator;

/**
 * CoHero health is always shown while its rendered sprite is visible, including at full HP.
 */
public class CoHeroHealthIndicator extends CharHealthIndicator {

    private final CharSprite renderSprite;

    public CoHeroHealthIndicator(Char target) {
        this(target, null);
    }

    public CoHeroHealthIndicator(Char target, CharSprite renderSprite) {
        super(target);
        this.renderSprite = renderSprite;
    }

    @Override
    public void update() {
        super.update();

        Char target = target();
        CharSprite sprite = renderSprite != null
                ? renderSprite
                : target == null ? null : target.sprite;

        if (target != null
                && target.isAlive()
                && target.isActive()
                && sprite != null
                && sprite.visible) {
            if (renderSprite != null) {
                width = sprite.width() * (4 / 6f);
                x = sprite.x + sprite.width() / 6f;
                y = sprite.y - 2;
                level(target);
            }
            visible = true;
        } else {
            visible = false;
        }
    }
}
