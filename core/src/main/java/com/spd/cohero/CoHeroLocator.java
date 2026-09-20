package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIcon;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.HealthBar;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Image;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PointF;

import java.util.ArrayList;

/**
 * Bidirectional off-screen monitor for Hero and CoHero.
 *
 * The locator is UI-only: it does not change AI, pathfinding, or gameplay FOV.
 */
public class CoHeroLocator extends Button {

    private static final float WIDTH = 50f;
    private static final float HEIGHT = 31f;
    private static final float EDGE_MARGIN = 2f;
    private static final float RAD_TO_DEG = 180f / 3.1415926f;

    private final float safeLeft;
    private final float safeTop;
    private final float safeRight;
    private final float safeBottom;

    private final ColorBlock background;
    private final Image heroAvatar;
    private final Image companionAvatar;
    private final Image direction;
    private final HealthBar hp;
    private final BuffStrip buffs;
    private final BitmapText warning;

    private Char locatorTarget;

    public CoHeroLocator(float safeLeft, float safeTop, float safeRight, float safeBottom) {
        super();

        this.safeLeft = safeLeft;
        this.safeTop = safeTop;
        this.safeRight = safeRight;
        this.safeBottom = safeBottom;

        background = new ColorBlock(1, 1, 0xCC000000);
        add(background);

        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero locator created without Dungeon.hero");
        }
        heroAvatar = classAvatar(Dungeon.hero.heroClass);
        add(heroAvatar);

        HeroClass companionClass = CoHero.companionClass();
        if (companionClass == null) {
            throw new IllegalStateException("CoHero locator has no selected companion class");
        }
        companionAvatar = classAvatar(companionClass);
        add(companionAvatar);

        direction = Icons.COMPASS.get();
        direction.origin.set(direction.width() / 2f, direction.height() / 2f);
        add(direction);

        hp = new HealthBar();
        add(hp);

        buffs = new BuffStrip();
        add(buffs);

        warning = new BitmapText(PixelScene.pixelFont);
        warning.text("!");
        warning.measure();
        warning.hardlight(0xFFFF00);
        add(warning);

        setSize(WIDTH, HEIGHT);
        visible = false;
    }

    private Image classAvatar(HeroClass heroClass) {
        return new Image(heroClass.spritesheet(), 0, 90, 12, 15);
    }

    @Override
    public void update() {
        super.update();

        CoHeroAlly companion = CoHero.findCompanion();
        if (Dungeon.hero == null
                || !Dungeon.hero.isAlive()
                || Dungeon.hero.sprite == null
                || companion == null
                || !companion.isAlive()
                || companion.sprite == null
                || camera() == null) {
            visible = false;
            locatorTarget = null;
            buffs.target(null);
            return;
        }

        Camera world = Camera.main;
        PointF heroCenter = Dungeon.hero.sprite.destinationCenter();
        PointF companionCenter = companion.sprite.destinationCenter();

        boolean heroOnScreen = onScreen(heroCenter, world);
        boolean companionOnScreen = onScreen(companionCenter, world);

        if (heroOnScreen && companionOnScreen) {
            visible = false;
            locatorTarget = null;
            buffs.target(null);
            return;
        }

        if (heroOnScreen) {
            setLocatorTarget(companion, companion);
        } else if (companionOnScreen) {
            setLocatorTarget(Dungeon.hero, companion);
        } else {
            float worldCenterX = world.scroll.x + world.width / 2f;
            float worldCenterY = world.scroll.y + world.height / 2f;
            float heroDistance = squaredDistance(heroCenter, worldCenterX, worldCenterY);
            float companionDistance = squaredDistance(companionCenter, worldCenterX, worldCenterY);
            setLocatorTarget(heroDistance <= companionDistance ? Dungeon.hero : companion, companion);
        }

        PointF target = locatorTarget.sprite.destinationCenter();

        visible = true;
        hp.level(locatorTarget);
        buffs.target(locatorTarget);
        warning.visible = locatorTarget == companion && companion.lowHealthRally();

        float worldCenterX = world.scroll.x + world.width / 2f;
        float worldCenterY = world.scroll.y + world.height / 2f;
        float dx = target.x - worldCenterX;
        float dy = target.y - worldCenterY;

        direction.angle = (float)Math.atan2(dx, -dy) * RAD_TO_DEG;

        float left = safeLeft + EDGE_MARGIN;
        float top = safeTop + EDGE_MARGIN;
        float right = safeRight - EDGE_MARGIN;
        float bottom = safeBottom - EDGE_MARGIN;

        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f;
        float halfW = Math.max(1f, (right - left - WIDTH) / 2f);
        float halfH = Math.max(1f, (bottom - top - HEIGHT) / 2f);

        float scaleX = dx == 0 ? Float.POSITIVE_INFINITY : halfW / Math.abs(dx);
        float scaleY = dy == 0 ? Float.POSITIVE_INFINITY : halfH / Math.abs(dy);
        float scale = Math.min(scaleX, scaleY);

        setPos(
                centerX + dx * scale - WIDTH / 2f,
                centerY + dy * scale - HEIGHT / 2f);
    }

    private boolean onScreen(PointF point, Camera world) {
        return point.x >= world.scroll.x
                && point.x <= world.scroll.x + world.width
                && point.y >= world.scroll.y
                && point.y <= world.scroll.y + world.height;
    }

    private float squaredDistance(PointF point, float x, float y) {
        float dx = point.x - x;
        float dy = point.y - y;
        return dx * dx + dy * dy;
    }

    private void setLocatorTarget(Char target, CoHeroAlly companion) {
        locatorTarget = target;
        heroAvatar.visible = target == Dungeon.hero;
        companionAvatar.visible = target == companion;
    }

    @Override
    protected void onClick() {
        if (locatorTarget != null && locatorTarget.isAlive() && locatorTarget.sprite != null) {
            Camera.main.panTo(locatorTarget.sprite.destinationCenter(), 5f);
        }
    }

    @Override
    protected boolean onLongClick() {
        CoHeroAlly companion = CoHero.findCompanion();
        if (locatorTarget == companion && companion != null && companion.isAlive()) {
            GameScene.show(new WndCompanionInventory(companion));
        } else if (locatorTarget == Dungeon.hero
                && Dungeon.hero != null
                && Dungeon.hero.isAlive()) {
            GameScene.show(new WndBag(Dungeon.hero.belongings.backpack));
        }

        // Always consume the long-press gesture so it cannot fall through into a normal
        // locator click after the hold threshold.
        return true;
    }

    @Override
    protected void layout() {
        super.layout();

        background.x = x;
        background.y = y;
        background.size(width, height);

        heroAvatar.x = companionAvatar.x = x + 2;
        heroAvatar.y = companionAvatar.y = y + 2;

        direction.x = x + width - direction.width() - 2;
        direction.y = y + 2;

        hp.setRect(x + 16, y + 16, width - 20, 2);
        buffs.setRect(x + 2, y + 21, width - 4, 8);

        warning.x = x + 17;
        warning.y = y + 3;
    }

    private static class BuffStrip extends Component {

        private static final int MAX_BUFFS = 6;
        private static final float ICON_STEP = 8f;

        private final ArrayList<Buff> shownBuffs = new ArrayList<>();
        private final ArrayList<BuffIcon> icons = new ArrayList<>();
        private Char target;

        void target(Char target) {
            if (this.target != target) {
                this.target = target;
                rebuild();
            }
        }

        @Override
        public void update() {
            super.update();
            refresh();
        }

        private void refresh() {
            if (target == null || !target.isAlive()) {
                if (!icons.isEmpty()) {
                    rebuild();
                }
                return;
            }

            ArrayList<Buff> current = new ArrayList<>();
            for (Buff buff : target.buffs()) {
                if (buff.icon() != BuffIndicator.NONE) {
                    current.add(buff);
                    if (current.size() >= MAX_BUFFS) {
                        break;
                    }
                }
            }

            boolean changed = current.size() != shownBuffs.size();
            if (!changed) {
                for (int i = 0; i < current.size(); i++) {
                    if (current.get(i) != shownBuffs.get(i)) {
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                rebuild(current);
            } else {
                for (int i = 0; i < icons.size(); i++) {
                    icons.get(i).refresh(shownBuffs.get(i));
                }
            }
        }

        private void rebuild() {
            rebuild(new ArrayList<>());
        }

        private void rebuild(ArrayList<Buff> current) {
            for (BuffIcon icon : icons) {
                icon.killAndErase();
            }
            icons.clear();
            shownBuffs.clear();

            for (Buff buff : current) {
                BuffIcon icon = new BuffIcon(buff, false);
                add(icon);
                icons.add(icon);
                shownBuffs.add(buff);
            }
            layout();
        }

        @Override
        protected void layout() {
            for (int i = 0; i < icons.size(); i++) {
                BuffIcon icon = icons.get(i);
                icon.x = x + i * ICON_STEP;
                icon.y = y;
            }
        }
    }
}
