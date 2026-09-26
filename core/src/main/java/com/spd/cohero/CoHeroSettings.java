package com.spd.cohero;

import com.watabou.utils.GameSettings;

/** Global defaults used when a new CoHero run is created. */
final class CoHeroSettings {

    private static final String ENEMY_SPAWN_MULTIPLIER_QUARTERS =
            "cohero_default_enemy_spawn_multiplier_quarters";
    private static final String DEBUG_LOG =
            "cohero_default_debug_log";

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

    private static void validateEnemySpawnMultiplierQuarters(int value) {
        if (value < CompanionEnemySurge.MIN_MULTIPLIER_QUARTERS
                || value > CompanionEnemySurge.MAX_MULTIPLIER_QUARTERS) {
            throw new IllegalStateException(
                    "Invalid global CoHero enemy spawn multiplier: " + value);
        }
    }
}
