package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Image;

/** Persistent HUD Tag for the shared Link meter and its ultimate. */
public final class CoHeroComboIndicator extends Tag {

    private final Image symbol;
    private final BitmapText count;
    private int displayedEnergy = -1;

    public CoHeroComboIndicator() {
        super(0xA67838);
        setColor(0xA67838);
        visible = false;

        symbol = Icons.get(Icons.ENERGY_SML);
        add(symbol);

        count = new BitmapText(PixelScene.pixelFont);
        count.scale.set(0.62f);
        add(count);
        setSize(SIZE, SIZE);
    }

    @Override
    public void update() {
        CoHeroAlly companion = CoHero.findCompanion();
        boolean show = Dungeon.hero != null && Dungeon.hero.isAlive()
                && companion != null && companion.isAlive();
        if (show && !visible) {
            flash();
        }
        visible = show;

        int energy = CoHeroCombo.energy();
        if (energy != displayedEnergy) {
            if (displayedEnergy < CoHeroCombo.CAST_COST && energy >= CoHeroCombo.CAST_COST) {
                flash();
            }
            displayedEnergy = energy;
            count.text(energy + "/" + CoHeroCombo.MAX_ENERGY);
            layout();
        }

        float alpha = CoHeroCombo.canCast() ? 1f : 0.55f;
        symbol.alpha(alpha);
        count.alpha(alpha);
        super.update();
    }

    @Override
    protected void onClick() {
        super.onClick();
        if (Dungeon.hero != null && Dungeon.hero.ready) {
            CoHeroCombo.requestCast();
        }
    }

    @Override
    protected String hoverText() {
        return CoHeroCombo.skillName() + " (" + CoHeroCombo.energy()
                + "/" + CoHeroCombo.MAX_ENERGY + ")";
    }

    @Override
    protected void layout() {
        super.layout();

        float slotX = flipped ? x + width - SIZE : x;
        symbol.x = slotX + (SIZE - symbol.width()) / 2f;
        symbol.y = y + 2f;
        PixelScene.align(symbol);

        // BitmapText is rendered at 62% to fit even the 180/180 value in a normal Tag.
        count.x = slotX + (SIZE - count.width() * count.scale.x) / 2f;
        count.y = y + height - 3f - count.height() * count.scale.y;
        PixelScene.align(count);
    }
}
