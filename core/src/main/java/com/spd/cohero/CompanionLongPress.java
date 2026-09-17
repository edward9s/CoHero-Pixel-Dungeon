package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.PointerArea;
import com.watabou.utils.Callback;
import com.watabou.utils.PointF;
import com.watabou.utils.Signal;

import java.lang.reflect.Field;

/** Map long-press input for opening the companion inventory. */
final class CompanionLongPress {

    private static LongPressLayer inputLayer;
    private static boolean installPending;

    private static Field cellSelectorField;
    private static Field defaultCellListenerField;
    private static Field selectorEventField;

    private CompanionLongPress() {
    }

    static void ensureInstalled() {
        if (!(ShatteredPixelDungeon.scene() instanceof GameScene)
                || Dungeon.hero == null
                || CoHero.findCompanion() == null) {
            return;
        }

        if (inputLayer != null
                && inputLayer.exists
                && inputLayer.parent == ShatteredPixelDungeon.scene()) {
            return;
        }

        if (installPending) {
            return;
        }
        installPending = true;

        ShatteredPixelDungeon.runOnRenderThread(new Callback() {
            @Override
            public void call() {
                installPending = false;
                if (!(ShatteredPixelDungeon.scene() instanceof GameScene)
                        || CoHero.findCompanion() == null) {
                    return;
                }

                CellSelector selector = currentCellSelector();
                if (selector == null) {
                    throw new IllegalStateException("CoHero could not access GameScene cell selector");
                }

                Group scene = (Group) ShatteredPixelDungeon.scene();
                if (inputLayer == null || !inputLayer.exists || inputLayer.parent != scene) {
                    inputLayer = new LongPressLayer(selector);
                    scene.addToFront(inputLayer);
                }
            }
        });
    }

    private static CellSelector currentCellSelector() {
        try {
            if (cellSelectorField == null) {
                cellSelectorField = GameScene.class.getDeclaredField("cellSelector");
                cellSelectorField.setAccessible(true);
            }
            return (CellSelector) cellSelectorField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("CoHero GameScene.cellSelector ABI mismatch", ex);
        }
    }

    private static Object defaultCellListener() {
        try {
            if (defaultCellListenerField == null) {
                defaultCellListenerField = GameScene.class.getDeclaredField("defaultCellListener");
                defaultCellListenerField.setAccessible(true);
            }
            return defaultCellListenerField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("CoHero GameScene.defaultCellListener ABI mismatch", ex);
        }
    }

    private static PointerEvent selectorEvent(CellSelector selector) {
        try {
            if (selectorEventField == null) {
                selectorEventField = PointerArea.class.getDeclaredField("curEvent");
                selectorEventField.setAccessible(true);
            }
            return (PointerEvent) selectorEventField.get(selector);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("CoHero PointerArea.curEvent ABI mismatch", ex);
        }
    }

    private static boolean normalMapMode(CellSelector selector) {
        return selector != null
                && selector.enabled
                && defaultCellListener() != null
                && selector.listener == defaultCellListener();
    }

    private static boolean isCancelEvent(PointerEvent event) {
        return event != null && "CANCEL".equals(String.valueOf(event.type));
    }

    private static final class LongPressLayer extends Gizmo implements Signal.Listener<PointerEvent> {

        private final CellSelector selector;
        private final float dragThreshold;

        private PointerEvent press;
        private float heldTime;
        private boolean cancelled;

        LongPressLayer(CellSelector selector) {
            this.selector = selector;
            dragThreshold = PixelScene.defaultZoom * DungeonTilemap.SIZE / 2f;
            PointerEvent.addPointerListener(this);
        }

        @Override
        public boolean onSignal(PointerEvent event) {
            if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) {
                return false;
            }

            if (event == null) {
                if (press != null && movedTooFar()) {
                    cancelled = true;
                }
                return false;
            }

            if (event.type == PointerEvent.Type.DOWN) {
                if (press != null) {
                    cancelled = true;
                    return false;
                }

                if (event.button == PointerEvent.RIGHT
                        || event.button == PointerEvent.MIDDLE
                        || event.button == PointerEvent.BACK
                        || event.button == PointerEvent.FORWARD
                        || !normalMapMode(selector)
                        || selector.target == null
                        || !selector.target.overlapsScreenPoint(
                                (int) event.current.x, (int) event.current.y)) {
                    return false;
                }

                press = event;
                heldTime = 0f;
                cancelled = false;
                return false;
            }

            if (press != null && event == press
                    && (event.type == PointerEvent.Type.UP || isCancelEvent(event))) {
                clearPress();
            }

            return false;
        }

        @Override
        public void update() {
            super.update();

            if (!(ShatteredPixelDungeon.scene() instanceof GameScene)
                    || parent != ShatteredPixelDungeon.scene()
                    || currentCellSelector() != selector) {
                killAndErase();
                return;
            }

            if (press == null) {
                return;
            }

            if (cancelled || movedTooFar()) {
                clearPress();
                return;
            }

            heldTime += Game.elapsed;
            if (heldTime < Button.longClick) {
                return;
            }

            CompanionHero companion = CoHero.findCompanion();
            if (companion == null
                    || !companion.isAlive()
                    || !normalMapMode(selector)
                    || selectorEvent(selector) != press
                    || Dungeon.hero == null
                    || !Dungeon.hero.ready
                    || GameScene.interfaceBlockingHero()) {
                clearPress();
                return;
            }

            if (!hitsCompanion(companion, press.current)) {
                // This long press belongs to stock SPD; do not reset/consume it.
                clearPress();
                return;
            }

            selector.reset();
            clearPress();
            GameScene.show(new WndCompanionInventory(companion));
            GameScene.ready();
        }

        private boolean movedTooFar() {
            return press != null
                    && PointF.distance(press.current, press.start) > dragThreshold;
        }

        private boolean hitsCompanion(CompanionHero companion, PointF screenPos) {
            if (screenPos == null || companion.sprite == null) {
                return false;
            }

            PointF point = Camera.main.screenToCamera((int) screenPos.x, (int) screenPos.y);
            if (!companion.sprite.overlapsPoint(point.x, point.y)) {
                return false;
            }

            PointF center = DungeonTilemap.tileCenterToWorld(companion.pos);
            return Math.abs(point.x - center.x) <= 12 && Math.abs(point.y - center.y) <= 12;
        }

        private void clearPress() {
            press = null;
            heldTime = 0f;
            cancelled = false;
        }

        @Override
        public void destroy() {
            PointerEvent.removePointerListener(this);
            clearPress();
            if (inputLayer == this) {
                inputLayer = null;
            }
            super.destroy();
        }
    }
}
