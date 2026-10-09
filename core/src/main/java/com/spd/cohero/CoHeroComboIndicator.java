package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.ColorBlock;

/**
 * Three-charge ultimate Tag. Its color and three charge notches show stored casts,
 * while the sword emblem separately dims when the pair cannot currently cast.
 * No new image resources or permanent animation state.
 */
public final class CoHeroComboIndicator extends Tag {

    private static final int EMPTY_COLOR = 0x68717A;
    private static final int[] CHARGE_COLORS = {0x68717A, 0xB78941, 0xD6A84E, 0xEBC469};
    private static final int SWORD_COLOR = 0xFFF1C9;
    private static final int UNCHARGED_SWORD_COLOR = 0xAEB6BF;

    private final ColorBlock[] notches = new ColorBlock[3];
    private final ColorBlock firstSword;
    private final ColorBlock secondSword;
    private final ColorBlock crystal;
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

        // Crossed twin blades and central crystal, assembled from four tiny pixel shapes.
        firstSword = new ColorBlock(2, 10, 0xFFFFFFFF);
        firstSword.origin.set(1f, 5f);
        firstSword.angle = 45;
        add(firstSword);

        secondSword = new ColorBlock(2, 10, 0xFFFFFFFF);
        secondSword.origin.set(1f, 5f);
        secondSword.angle = -45;
        add(secondSword);

        crystal = new ColorBlock(4, 4, 0xFFFFFFFF);
        crystal.origin.set(2, 2);
        crystal.angle = 45;
        add(crystal);

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
        firstSword.alpha(emblemAlpha);
        secondSword.alpha(emblemAlpha);
        crystal.alpha(emblemAlpha);
        count.alpha(energy >= CoHeroCombo.CAST_COST ? 1f : 0.75f);
        int swordColor = charges > 0 ? SWORD_COLOR : UNCHARGED_SWORD_COLOR;
        firstSword.hardlight(swordColor);
        secondSword.hardlight(swordColor);
        crystal.hardlight(charges == 3 ? 0xFFFFFF : 0xE6B875);
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
            notches[i].y = y + 2f;
        }
        firstSword.x = slotX + 11f;
        firstSword.y = y + 6f;
        secondSword.x = slotX + 11f;
        secondSword.y = y + 6f;
        crystal.x = slotX + 10f;
        crystal.y = y + 9f;
        count.x = slotX + (SIZE - count.width()) / 2f;
        count.y = y + height - count.height() - 2f;
        PixelScene.align(count);
    }
}
