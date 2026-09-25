package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.CheckBox;
import com.shatteredpixel.shatteredpixeldungeon.ui.OptionSlider;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.DeviceCompat;

import java.util.Locale;

/** CoHero-specific settings embedded as a tab in SPD's settings window. */
public final class CoHeroSettingsTab extends Component {

    private static final int SLIDER_HEIGHT = 21;
    private static final int BUTTON_HEIGHT = 16;
    private static final float GAP = 1f;

    private CoHeroAlly companion;

    private RenderedTextBlock title;
    private ColorBlock separatorTop;

    private RenderedTextBlock enemySpawnLabel;
    private RenderedTextBlock enemySpawnValue;
    private OptionSlider enemySpawnSlider;
    private CheckBox debugLog;

    private ColorBlock separatorTransfer;
    private RedButton exportSave;
    private RedButton importSave;

    @Override
    protected void createChildren() {
        companion = CoHero.findCompanion();

        title = PixelScene.renderTextBlock("CoHero", 9);
        title.hardlight(Window.TITLE_COLOR);
        add(title);

        separatorTop = new ColorBlock(1, 1, 0xFF000000);
        add(separatorTop);

        enemySpawnLabel =
                PixelScene.renderTextBlock(CoHeroMessages.get("settings.enemy_spawn"), 7);
        add(enemySpawnLabel);

        enemySpawnValue = PixelScene.renderTextBlock(enemySpawnValueText(), 7);
        add(enemySpawnValue);

        enemySpawnSlider = new OptionSlider(
                "",
                "1.0x",
                "4.0x",
                CompanionEnemySurge.MIN_MULTIPLIER_QUARTERS,
                CompanionEnemySurge.MAX_MULTIPLIER_QUARTERS) {
            @Override
            protected void onChange() {
                int value = getSelectedValue();
                CoHeroSettings.setDefaultEnemySpawnMultiplierQuarters(value);
                if (hasLiveCompanion()) {
                    companion.setEnemySpawnMultiplierQuarters(value);
                }
                enemySpawnValue.text(enemySpawnValueText());
                layoutEnemySpawnValue();
            }
        };
        enemySpawnSlider.setSelectedValue(enemySpawnMultiplierQuarters());
        add(enemySpawnSlider);

        debugLog = new CheckBox(CoHeroMessages.get("settings.debug_log")) {
            @Override
            protected void onClick() {
                super.onClick();
                CoHeroSettings.setDefaultDebugLogEnabled(checked());
                if (hasLiveCompanion()) {
                    companion.setDebugLogEnabled(checked());
                }
            }
        };
        debugLog.checked(debugLogEnabled());
        add(debugLog);

        if (DeviceCompat.isAndroid() || DeviceCompat.isDesktop()) {
            separatorTransfer = new ColorBlock(1, 1, 0xFF000000);
            add(separatorTransfer);

            exportSave = new RedButton(CoHeroMessages.get("save_transfer.export"), 8) {
                @Override
                protected void onClick() {
                    super.onClick();
                    CoHeroSaveTransfer.exportSave();
                }
            };
            add(exportSave);

            importSave = new RedButton(CoHeroMessages.get("save_transfer.import"), 8) {
                @Override
                protected void onClick() {
                    super.onClick();
                    CoHeroSaveTransfer.importSave();
                }
            };
            add(importSave);
        }
    }

    @Override
    protected void layout() {
        title.setPos((width - title.width()) / 2f, y + GAP);

        separatorTop.size(width, 1);
        separatorTop.y = title.bottom() + 3 * GAP;

        float bottom = separatorTop.y + 1;

        float labelY = bottom + GAP;
        enemySpawnLabel.maxWidth(
                Math.max(
                        1,
                        (int) Math.floor(width - enemySpawnValue.width() - 2)));
        enemySpawnLabel.setPos(0, labelY);
        layoutEnemySpawnValue();

        enemySpawnSlider.setRect(
                0,
                Math.max(enemySpawnLabel.bottom(), enemySpawnValue.bottom()) + GAP,
                width,
                SLIDER_HEIGHT);

        debugLog.setRect(0, enemySpawnSlider.bottom() + GAP, width, BUTTON_HEIGHT);
        bottom = debugLog.bottom();

        if (separatorTransfer != null) {
            separatorTransfer.size(width, 1);
            separatorTransfer.y = bottom + GAP;

            float transferWidth = width / 2f - GAP / 2f;
            exportSave.setRect(
                    0,
                    separatorTransfer.y + 1 + GAP,
                    transferWidth,
                    BUTTON_HEIGHT);
            importSave.setRect(
                    exportSave.right() + GAP,
                    exportSave.top(),
                    transferWidth,
                    BUTTON_HEIGHT);
            bottom = importSave.bottom();
        }

        height = bottom;
    }

    private void layoutEnemySpawnValue() {
        if (enemySpawnValue == null) {
            return;
        }
        enemySpawnValue.setPos(
                Math.max(0, width - enemySpawnValue.width()),
                enemySpawnLabel == null ? y : enemySpawnLabel.top());
    }

    private String enemySpawnValueText() {
        return String.format(
                Locale.ENGLISH,
                "%.2fx",
                enemySpawnMultiplierQuarters() / 4f);
    }

    private int enemySpawnMultiplierQuarters() {
        return hasLiveCompanion()
                ? companion.enemySpawnMultiplierQuarters()
                : CoHeroSettings.defaultEnemySpawnMultiplierQuarters();
    }

    private boolean debugLogEnabled() {
        return hasLiveCompanion()
                ? companion.debugLogEnabled()
                : CoHeroSettings.defaultDebugLogEnabled();
    }

    private boolean hasLiveCompanion() {
        return companion != null && companion.isAlive();
    }
}
