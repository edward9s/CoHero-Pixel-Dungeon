package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.shatteredpixel.shatteredpixeldungeon.ui.HeroIcon;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Image;

/** Three-charge Tag using the original Duelist Challenge (duel) ability icon. */
public final class CoHeroComboIndicator extends Tag {

    // Slightly taller than a normal Tag so the original 16px sprite, three
    // charge notches and numeric meter can each occupy their own row.
    public static final int HEIGHT = 30;

    // Cool slate backgrounds preserve the stock Challenge icon's reds and
    // keep its warm pixels from blending into an increasingly golden Tag.
    private static final int EMPTY_COLOR = 0x404955;
    private static final int[] CHARGE_COLORS = {0x404955, 0x4B5B6D, 0x526C81, 0x587B92};

    private final ColorBlock[] notches = new ColorBlock[3];
    private final HeroIcon icon;
    private final BitmapText count;
    private final Image crosshair;
    private int displayedEnergy = -1;
    private int displayedCharges = -1;

    public CoHeroComboIndicator() {
        super(EMPTY_COLOR);
        visible = false;

        for (int i = 0; i < notches.length; i++) {
            notches[i] = new ColorBlock(5, 2, 0xFFFFFFFF);
            add(notches[i]);
        }

        // Directly display SPD's original red Challenge sprite at native 16x16.
        // No recolored replacement, scaling, border or shadow layer.
        icon = new HeroIcon(new Challenge());
        add(icon);

        count = new BitmapText(PixelScene.pixelFont);
        add(count);
        crosshair = Icons.TARGET.get();
        setSize(SIZE, HEIGHT);
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
            removeCrosshair();
           super.update();
            return;
        }
        refreshCrosshair();

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
                notches[i].hardlight(i < charges ? 0xDCE9FF : 0x353F4B);
            }
        }

        // Stored energy and actual availability are deliberately distinct.
        float emblemAlpha = CoHeroCombo.canCast() ? 1f : 0.45f;
        icon.alpha(emblemAlpha);
        count.alpha(energy >= CoHeroCombo.CAST_COST ? 1f : 0.75f);
         super.update();
    }

    private void refreshCrosshair() {
        Mob aim = CoHeroCombo.aimTarget();
        if (aim == null || aim.sprite == null || !aim.sprite.visible
                || aim.sprite.parent == null) {
            removeCrosshair();
            return;
        }
        if (crosshair.parent != aim.sprite.parent) {
            removeCrosshair();
            aim.sprite.parent.addToFront(crosshair);
        }
        crosshair.point(aim.sprite.center(crosshair));
    }

    private void removeCrosshair() {
        if (crosshair.parent != null) {
            crosshair.remove();
        }
    }

    @Override
    public void destroy() {
        removeCrosshair();
        super.destroy();
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
        // Bars occupy y+3..5, then 16px art at y+6..22, and meter below.
        icon.x = slotX + (SIZE - icon.width()) / 2f;
        icon.y = y + 6f;
        PixelScene.align(icon);

        count.x = slotX + (SIZE - count.width()) / 2f;
        count.y = y + height - count.height() - 1f;
        PixelScene.align(count);
    }
}
