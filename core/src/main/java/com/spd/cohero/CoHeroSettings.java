package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.watabou.utils.GameSettings;

/** Global defaults used when a new CoHero run is created. */
final class CoHeroSettings {

    private static final String ENEMY_SPAWN_MULTIPLIER_QUARTERS =
            "cohero_default_enemy_spawn_multiplier_quarters";
    private static final String DEBUG_LOG =
            "cohero_default_debug_log";
    private static final String AUTO_FILL_LINK = "cohero_auto_fill_link";
    private static final String TEST_OPTIONS_VISIBLE = "cohero_test_options_visible";

    private CoHeroSettings() {
    }

    static int defaultEnemySpawnMultiplierQuarters() {
        int value = GameSettings.getInt(
                ENEMY_SPAWN_MULTIPLIER_QUARTERS,
                CompanionEnemySurge.DEFAULT_MULTIPLIER_QUARTERS);
        validateEnemySpawnMultiplierQuarters(value);
        return value;
    }

    static void setDefaultEnemySpawnMultiplierQuarters(int value) {
        validateEnemySpawnMultiplierQuarters(value);
        GameSettings.put(ENEMY_SPAWN_MULTIPLIER_QUARTERS, value);
    }

    static boolean defaultDebugLogEnabled() {
        return GameSettings.getBoolean(DEBUG_LOG, false);
    }

    static void setDefaultDebugLogEnabled(boolean enabled) {
        GameSettings.put(DEBUG_LOG, enabled);
    }

    /** Explicit opt-in for game testing; never enabled in ordinary play by default. */
    static boolean autoFillLinkEnabled() {
        return GameSettings.getBoolean(AUTO_FILL_LINK, false);
    }

    static void setAutoFillLinkEnabled(boolean enabled) {
        GameSettings.put(AUTO_FILL_LINK, enabled);
    }

    /** Visibility is global across runs, and independent of the test option's value. */
    static boolean testOptionsVisible() {
        return GameSettings.getBoolean(TEST_OPTIONS_VISIBLE, false);
    }

    /**
     * Secret trigger, called only on a debug-log OFF -> ON click.
     * No hidden state is attached to the companion or its save.
     */
    static boolean toggleTestOptionsIfEligible(CoHeroAlly companion) {
        if (companion == null || !companion.isAlive()) {
            return false;
        }
        CompanionInventory inventory = companion.inventory();
        if (inventory.weapon() != null || inventory.armor() != null
                || inventory.ringOne() != null || inventory.ringTwo() != null) {
            return false;
        }

        boolean hasWaterskin = false;
        boolean hasVelvetPouch = false;
        for (Item item : inventory.backpack()) {
            hasWaterskin |= item instanceof Waterskin;
            hasVelvetPouch |= item instanceof VelvetPouch;
        }
        if (!hasWaterskin || !hasVelvetPouch) {
            return false;
        }
        GameSettings.put(TEST_OPTIONS_VISIBLE, !testOptionsVisible());
        return true;
    }

    private static void validateEnemySpawnMultiplierQuarters(int value) {
        if (value < CompanionEnemySurge.MIN_MULTIPLIER_QUARTERS
                || value > CompanionEnemySurge.MAX_MULTIPLIER_QUARTERS) {
            throw new IllegalStateException(
                    "Invalid global CoHero enemy spawn multiplier: " + value);
        }
    }
}
