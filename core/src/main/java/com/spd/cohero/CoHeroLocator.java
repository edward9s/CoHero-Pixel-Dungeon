package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.HealthBar;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Image;
import com.watabou.utils.PointF;

/**
 * Off-screen monitor for the autonomous CoHero.
 *
 * The locator is UI-only: it does not change AI, pathfinding, or gameplay FOV.
 */
public class CoHeroLocator extends Button {

    private static final float WIDTH = 48f;
    private static final float HEIGHT = 22f;
    private static final float EDGE_MARGIN = 4f;
    private static final float RAD_TO_DEG = 180f / 3.1415926f;

    private final ColorBlock background;
    private final Image avatar;
    private final Image direction;
    private final HealthBar hp;
    private final BitmapText warning;

    public CoHeroLocator() {
        super();

        background = new ColorBlock(1, 1, 0xCC000000);
        add(background);

        HeroClass heroClass = CoHero.companionClass();
        if (heroClass == null) {
            throw new IllegalStateException("CoHero locator has no selected companion class");
        }
        // Use the class-selection portrait so the HUD icon is stable across armor changes.
        avatar = new Image(heroClass.spritesheet(), 0, 90, 12, 15);
        add(avatar);

        direction = Icons.COMPASS.get();
        direction.origin.set(direction.width() / 2f, direction.height() / 2f);
        add(direction);

        hp = new HealthBar();
        add(hp);

        warning = new BitmapText(PixelScene.pixelFont);
        warning.text("!");
        warning.measure();
        warning.hardlight(0xFFFF00);
        add(warning);

        setSize(WIDTH, HEIGHT);
        visible = false;
    }

    @Override
    public void update() {
        super.update();

        CoHeroAlly companion = CoHero.findCompanion();
        if (companion == null
                || !companion.isAlive()
                || companion.sprite == null
                || camera() == null) {
            visible = false;
            return;
        }

        PointF target = companion.sprite.destinationCenter();
        Camera world = Camera.main;

        boolean onScreen = target.x >= world.scroll.x
                && target.x <= world.scroll.x + world.width
                && target.y >= world.scroll.y
                && target.y <= world.scroll.y + world.height;

        if (onScreen) {
            visible = false;
            return;
        }

        visible = true;
        hp.level(companion);
        warning.visible = companion.lowHealthRally();

        float worldCenterX = world.scroll.x + world.width / 2f;
        float worldCenterY = world.scroll.y + world.height / 2f;
        float dx = target.x - worldCenterX;
        float dy = target.y - worldCenterY;

        direction.angle = (float)Math.atan2(dx, -dy) * RAD_TO_DEG;

        Camera ui = camera();
        float uiCenterX = ui.scroll.x + ui.width / 2f;
        float uiCenterY = ui.scroll.y + ui.height / 2f;
        float halfW = Math.max(1f, ui.width / 2f - WIDTH / 2f - EDGE_MARGIN);
        float halfH = Math.max(1f, ui.height / 2f - HEIGHT / 2f - EDGE_MARGIN);

        float scaleX = dx == 0 ? Float.POSITIVE_INFINITY : halfW / Math.abs(dx);
        float scaleY = dy == 0 ? Float.POSITIVE_INFINITY : halfH / Math.abs(dy);
        float scale = Math.min(scaleX, scaleY);

        setPos(
                uiCenterX + dx * scale - WIDTH / 2f,
                uiCenterY + dy * scale - HEIGHT / 2f);
    }

    @Override
    protected void onClick() {
        CoHeroAlly companion = CoHero.findCompanion();
        if (companion != null && companion.isAlive() && companion.sprite != null) {
            Camera.main.panTo(companion.sprite.destinationCenter(), 5f);
        }
    }

    @Override
    protected void layout() {
        super.layout();

        background.x = x;
        background.y = y;
        background.size(width, height);

        avatar.x = x + 2;
        avatar.y = y + 2;

        hp.setRect(x + 16, y + height - 4, width - 20, 2);

        direction.x = x + width - direction.width() - 2;
        direction.y = y + 2;

        warning.x = x + 17;
        warning.y = y + 3;
    }
}
