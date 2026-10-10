package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.shatteredpixel.shatteredpixeldungeon.ui.HeroIcon;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.ColorBlock;

/** Three-charge Tag using the original Duelist Challenge (duel) ability icon. */
public final class CoHeroComboIndicator extends Tag {

    private static final int EMPTY_COLOR = 0x68717A;
    private static final int[] CHARGE_COLORS = {0x68717A, 0xB78941, 0xD6A84E, 0xEBC469};
    private static final float ICON_SCALE = 0.70f;
    private static final float FRAME_SIZE = 14f;
    private static final int FRAME_COLOR = 0xFFE6D6A9;

    private final ColorBlock[] notches = new ColorBlock[3];
    private final HeroIcon icon;
    private final ColorBlock frameTop;
    private final ColorBlock frameBottom;
    private final ColorBlock frameLeft;
    private final ColorBlock frameRight;
    private final BitmapText count;
    private int displayedEnergy = -1;
    private int displayedCharges = -1;

    public CoHeroComboIndicator() {
        super(EMPTY_COLOR);
        visible = false;

        for (int i = 0; i < notches.length; i++) {
            notches[i] = new ColorBlock(5, 2, 0xFFFFFFFF);
            add(notches[i]);
        }

        // Use SPD's actual Duelist crown ability icon, not a reproduced sprite.
        icon = new HeroIcon(new Challenge());
        icon.scale.set(ICON_SCALE, ICON_SCALE);
        add(icon);

        // Four 1px hairlines around the 70%-size icon.
        frameTop = new ColorBlock(FRAME_SIZE, 1, FRAME_COLOR);
        frameBottom = new ColorBlock(FRAME_SIZE, 1, FRAME_COLOR);
        frameLeft = new ColorBlock(1, FRAME_SIZE, FRAME_COLOR);
        frameRight = new ColorBlock(1, FRAME_SIZE, FRAME_COLOR);
        add(frameTop);
        add(frameBottom);
        add(frameLeft);
        add(frameRight);

        count = new BitmapText(PixelScene.pixelFont);
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
        if (!show) {
           super.update();
            return;
        }

        int energy = CoHeroCombo.energy();
        int charges = energy / CoHeroCombo.CAST_COST;
        if (energy != displayedEnergy) {
            displayedEnergy = energy;
            count.text(energy + "/" + CoHeroCombo.MAX_ENERGY);
            count.measure();
            float fontScale = Math.min(0.68f, (SIZE - 2f) / Math.max(1f, count.width()));
            count.scale.set(fontScale, fontScale);
            layout();
        }
        if (charges != displayedCharges) {
            if (charges > displayedCharges && displayedCharges >= 0) {
                flash();
            }
            displayedCharges = charges;
            setColor(CHARGE_COLORS[charges]);
            for (int i = 0; i < notches.length; i++) {
                notches[i].hardlight(i < charges ? 0xFFE19A : 0x41434A);
            }
        }

        // Stored energy and actual availability are deliberately distinct.
        float emblemAlpha = CoHeroCombo.canCast() ? 1f : 0.45f;
        icon.alpha(emblemAlpha);
        count.alpha(energy >= CoHeroCombo.CAST_COST ? 1f : 0.75f);
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
    protected boolean onLongClick() {
        if (Dungeon.hero != null) {
            GameScene.show(new WndMessage(
                    CoHeroCombo.skillName() + "\n\n" + CoHeroCombo.skillDescription()));
        }
        return true;
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
        for (int i = 0; i < notches.length; i++) {
            notches[i].x = slotX + 3f + i * 7f;
            notches[i].y = y + 3f;
        }
        // The charge bars end at y+5; the icon frame starts immediately below.
        float frameX = slotX + (SIZE - FRAME_SIZE) / 2f;
        float frameY = y + 5f;
        icon.x = slotX + (SIZE - icon.width()) / 2f;
        icon.y = frameY + 1f;
        PixelScene.align(icon);

        frameTop.x = frameBottom.x = frameX;
        frameTop.y = frameY;
        frameBottom.y = frameY + FRAME_SIZE - 1;
        frameLeft.x = frameX;
        frameLeft.y = frameY;
        frameRight.x = frameX + FRAME_SIZE - 1;
        frameRight.y = frameY;
        count.x = slotX + (SIZE - count.width()) / 2f;
        count.y = y + SIZE - count.height() - 1f;
        PixelScene.align(count);
    }
}
