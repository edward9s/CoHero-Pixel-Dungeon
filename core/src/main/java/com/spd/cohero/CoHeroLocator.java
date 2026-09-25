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
import com.watabou.utils.RectF;

import java.util.ArrayList;

/**
 * Bidirectional off-screen monitor for Hero and CoHero.
 *
 * The locator is UI-only: it does not change AI, pathfinding, or gameplay FOV.
 */
public class CoHeroLocator extends Button {

    private static final float WIDTH = 32f;
    private static final float HEIGHT = 22f;
    private static final float AVATAR_SCALE = 0.7f;
    private static final float TEXT_SCALE = 0.7f;
    private static final float DIRECTION_SCALE = 0.7f;
    private static final float VERTICAL_EDGE_MARGIN = 2f;
    private static final float TAG_GAP = 1f;
    private static final float RAD_TO_DEG = 180f / 3.1415926f;

    private final float safeLeft;
    private final float safeTop;
    private final float safeRight;
    private final float safeBottom;

    private final ColorBlock background;
    private final Image heroAvatar;
    private final Image companionAvatar;
    private final Image direction;
    private final BitmapText identity;
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
        direction.scale.set(DIRECTION_SCALE);
        add(direction);

        identity = new BitmapText(PixelScene.pixelFont);
        identity.text("CO");
        identity.measure();
        identity.scale.set(TEXT_SCALE);
        add(identity);

        hp = new HealthBar();
        add(hp);

        buffs = new BuffStrip();
        add(buffs);

        warning = new BitmapText(PixelScene.pixelFont);
        warning.text("!");
        warning.measure();
        warning.visible = false;
        add(warning);

        setSize(WIDTH, HEIGHT);
        visible = false;
    }

    private Image classAvatar(HeroClass heroClass) {
        Image avatar = new Image(heroClass.spritesheet(), 0, 90, 12, 15);
        avatar.scale.set(AVATAR_SCALE);
        return avatar;
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
        boolean companionWarning = locatorTarget == companion;
        boolean lowHealth = companionWarning && companion.isLowHealth();
        boolean inCombat = companionWarning && companion.inCombat();
        warning.visible = lowHealth || inCombat;
        if (warning.visible) {
            warning.hardlight(lowHealth ? 0xFF0000 : 0xFFFF00);
        }

        float worldCenterX = world.scroll.x + world.width / 2f;
        float worldCenterY = world.scroll.y + world.height / 2f;
        float dx = target.x - worldCenterX;
        float dy = target.y - worldCenterY;

        direction.angle = (float)Math.atan2(dx, -dy) * RAD_TO_DEG;

        float left = safeLeft;
        float top = safeTop + VERTICAL_EDGE_MARGIN;
        float right = safeRight;
        float bottom = safeBottom - VERTICAL_EDGE_MARGIN;

        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f;
        float halfW = Math.max(1f, (right - left - WIDTH) / 2f);
        float halfH = Math.max(1f, (bottom - top - HEIGHT) / 2f);

        float scaleX = dx == 0 ? Float.POSITIVE_INFINITY : halfW / Math.abs(dx);
        float scaleY = dy == 0 ? Float.POSITIVE_INFINITY : halfH / Math.abs(dy);
        float scale = Math.min(scaleX, scaleY);

        float locatorX = centerX + dx * scale - WIDTH / 2f;
        float locatorY = centerY + dy * scale - HEIGHT / 2f;

        RectF tagBounds = GameScene.coHeroTagBounds();
        if (tagBounds != null) {
            RectF locatorBounds =
                    new RectF(locatorX, locatorY, locatorX + WIDTH, locatorY + HEIGHT);
            if (!locatorBounds.intersect(tagBounds).isEmpty()) {
                float screenCenterX = (left + right) / 2f;
                if (tagBounds.right <= screenCenterX) {
                    locatorX = Math.min(right - WIDTH, tagBounds.right + TAG_GAP);
                } else {
                    locatorX = Math.max(left, tagBounds.left - WIDTH - TAG_GAP);
                }
            }
        }

        setPos(locatorX, locatorY);
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
        identity.text(target == Dungeon.hero ? "ME" : "CO");
        identity.measure();
    }

    @Override
    protected void onClick() {
        if (locatorTarget != null && locatorTarget.isAlive() && locatorTarget.sprite != null) {
            Camera.main.panTo(locatorTarget.sprite.destinationCenter(), 5f);
        }
    }

    @Override
    protected boolean onLongClick() {
        if (locatorTarget == Dungeon.hero
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

        heroAvatar.x = companionAvatar.x = x + 1;
        heroAvatar.y = companionAvatar.y = y + 1;

        direction.x = x + width - direction.width() * DIRECTION_SCALE - 1;
        direction.y = y + 1;

        identity.x = x + 10;
        identity.y = y + 2;

        hp.setRect(x + 1, y + 12, width - 2, 2);
        buffs.setRect(x + 1, y + 15, width - 2, 5);

        warning.x = identity.x + identity.width() * TEXT_SCALE + 1;
        warning.y = y + 1;
    }

    private static class BuffStrip extends Component {

        private static final int MAX_BUFFS = 6;
        private static final float ICON_SCALE = 5f / 7f;
        private static final float ICON_STEP = 5f;

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
                icon.scale.set(ICON_SCALE);
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
