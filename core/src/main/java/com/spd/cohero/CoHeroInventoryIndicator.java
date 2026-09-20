package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.noosa.Image;

/** Permanent CoHero inventory entry in the standard GameScene tag stack. */
public class CoHeroInventoryIndicator extends Tag {

    private static final int TOOLBAR_NEUTRAL = 0x7B8073;
    private static final float COMPANION_BADGE_SCALE = 0.7f;

    private final Image backpack;
    private final Image companionBadge;

    public CoHeroInventoryIndicator() {
        super(TOOLBAR_NEUTRAL);

        visible = false;

        // Match SPD's normal inventory button glyph exactly: Toolbar uses
        // Assets.Interfaces.TOOLBAR frame (160, 0, 16, 16).
        backpack = new Image(Assets.Interfaces.TOOLBAR);
        backpack.frame(160, 0, 16, 16);
        add(backpack);

        HeroClass companionClass = CoHero.companionClass();
        if (companionClass == null) {
            throw new IllegalStateException("CoHero inventory indicator has no selected companion class");
        }
        companionBadge = new Image(companionClass.spritesheet(), 0, 90, 12, 15);
        companionBadge.scale.set(COMPANION_BADGE_SCALE);
        add(companionBadge);

        setSize(SIZE, SIZE);
    }

    @Override
    public void update() {
        CoHeroAlly companion = CoHero.findCompanion();
        boolean shouldShow = Dungeon.hero != null
                && Dungeon.hero.isAlive()
                && companion != null
                && companion.isAlive();

        if (shouldShow && !visible) {
            flash();
        }
        visible = shouldShow;

        super.update();
    }

    @Override
    protected void onClick() {
        super.onClick();

        CoHeroAlly companion = CoHero.findCompanion();
        if (Dungeon.hero != null
                && Dungeon.hero.ready
                && companion != null
                && companion.isAlive()) {
            GameScene.show(new WndCompanionInventory(companion));
        }
    }

    @Override
    protected String hoverText() {
        return CoHeroMessages.get("inventory.title");
    }

    @Override
    protected void layout() {
        super.layout();

        float iconX;
        if (!flipped) {
            iconX = x + (SIZE - backpack.width()) / 2f + 1;
        } else {
            iconX = x + width - (SIZE + backpack.width()) / 2f - 1;
        }
        backpack.x = iconX;
        backpack.y = y + (height - backpack.height()) / 2f;
        PixelScene.align(backpack);

        companionBadge.x = backpack.x + backpack.width() - companionBadge.width();
        companionBadge.y = backpack.y + backpack.height() - companionBadge.height();
        PixelScene.align(companionBadge);
    }
}
