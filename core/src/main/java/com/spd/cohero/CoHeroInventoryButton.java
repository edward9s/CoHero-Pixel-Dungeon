package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Image;

/**
 * Always-discoverable CoHero inventory entry placed next to the player's inventory control.
 */
public class CoHeroInventoryButton extends Button {

    private final ColorBlock background;
    private final ItemSprite backpack;
    private final Image companionBadge;

    public CoHeroInventoryButton() {
        background = new ColorBlock(1, 1, 0xCC000000);
        add(background);

        backpack = new ItemSprite(ItemSpriteSheet.BACKPACK);
        backpack.scale.set(0.625f);
        add(backpack);

        HeroClass companionClass = CoHero.companionClass();
        if (companionClass == null) {
            throw new IllegalStateException("CoHero inventory button has no selected companion class");
        }
        companionBadge = new Image(companionClass.spritesheet(), 0, 90, 12, 15);
        companionBadge.scale.set(0.35f);
        add(companionBadge);
    }

    @Override
    public void update() {
        super.update();

        CoHeroAlly companion = CoHero.findCompanion();
        visible = active = companion != null && companion.isAlive();
    }

    @Override
    protected void onClick() {
        CoHeroAlly companion = CoHero.findCompanion();
        if (companion != null && companion.isAlive()) {
            GameScene.show(new WndCompanionInventory(companion));
        }
    }

    @Override
    protected void layout() {
        super.layout();

        background.x = x;
        background.y = y;
        background.size(width, height);

        backpack.x = x + (width - backpack.width()) / 2f;
        backpack.y = y + (height - backpack.height()) / 2f;

        companionBadge.x = x + width - companionBadge.width() - 1;
        companionBadge.y = y + height - companionBadge.height() - 1;
    }
}
