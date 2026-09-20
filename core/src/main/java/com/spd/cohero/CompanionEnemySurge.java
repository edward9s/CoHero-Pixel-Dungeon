package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.Bundle;

import java.lang.reflect.Field;

/**
 * Optional CoHero-mode pressure control. It preserves vanilla enemy selection and placement while
 * scaling only natural respawn rate and the natural enemy population limit.
 */
public final class CompanionEnemySurge extends Buff {

    static final int MIN_MULTIPLIER_TENTHS = 10;
    static final int DEFAULT_MULTIPLIER_TENTHS = 15;
    static final int MAX_MULTIPLIER_TENTHS = 30;

    private static final String MULTIPLIER_TENTHS = "multiplier_tenths";

    private static Field respawnerField;

    private int multiplierTenths = DEFAULT_MULTIPLIER_TENTHS;

    private transient Level trackedLevel;
    private transient int baseMobLimit = -1;
    private transient float extraSpawnCountdown = Float.NaN;

    int multiplierTenths() {
        return multiplierTenths;
    }

    float multiplier() {
        return multiplierTenths / 10f;
    }

    void setMultiplierTenths(int value) {
        if (value < MIN_MULTIPLIER_TENTHS || value > MAX_MULTIPLIER_TENTHS) {
            throw new IllegalArgumentException("enemy spawn multiplier must be between 1.0x and 3.0x");
        }
        multiplierTenths = value;
        extraSpawnCountdown = Float.NaN;
    }

    @Override
    public boolean act() {
        if (target == null || !target.isAlive() || Dungeon.level == null) {
            spend(TICK);
            return true;
        }

        if (trackedLevel != Dungeon.level) {
            trackedLevel = Dungeon.level;
            baseMobLimit = -1;
            extraSpawnCountdown = Float.NaN;
        }

        processExtraSpawns();

        spend(TICK);
        return true;
    }

    private void processExtraSpawns() {
        if (multiplierTenths == MIN_MULTIPLIER_TENTHS) {
            extraSpawnCountdown = Float.NaN;
            return;
        }

        if (!hasVanillaRespawner()) {
            extraSpawnCountdown = Float.NaN;
            return;
        }

        // mobLimit() contains randomness. Sample it once per visited level so the control does not
        // consume extra RNG every turn.
        if (baseMobLimit < 0) {
            baseMobLimit = Math.max(0, Dungeon.level.mobLimit());
        }
        if (baseMobLimit <= 0) {
            extraSpawnCountdown = Float.NaN;
            return;
        }

        float multiplier = multiplier();
        int effectiveLimit = Math.max(baseMobLimit, Math.round(baseMobLimit * multiplier));
        int currentCount = Dungeon.level.mobCount();
        if (currentCount >= effectiveLimit) {
            extraSpawnCountdown = Float.NaN;
            return;
        }

        if (Float.isNaN(extraSpawnCountdown)) {
            extraSpawnCountdown = extraSpawnInterval(currentCount, multiplier);
        }

        extraSpawnCountdown -= TICK;
        int attempts = 0;

        while (extraSpawnCountdown <= 0f && attempts < 2) {
            currentCount = Dungeon.level.mobCount();
            if (currentCount >= effectiveLimit) {
                extraSpawnCountdown = Float.NaN;
                break;
            }

            if (Dungeon.level.spawnMob(12)) {
                attempts++;
                currentCount = Dungeon.level.mobCount();
                extraSpawnCountdown += extraSpawnInterval(currentCount, multiplier);
            } else {
                // Match the vanilla respawner's failed-placement retry cadence.
                extraSpawnCountdown = TICK;
                break;
            }
        }
    }

    private float extraSpawnInterval(int currentCount, float multiplier) {
        // Below vanilla's population limit, vanilla already contributes 1.0x respawn rate.
        // Above it, vanilla stops spawning, so this controller supplies the full requested rate.
        float extraRate = currentCount < baseMobLimit ? multiplier - 1f : multiplier;
        return Math.max(0.1f, Dungeon.level.respawnCooldown() / extraRate);
    }

    private static boolean hasVanillaRespawner() {
        if (Dungeon.level == null) {
            return false;
        }

        try {
            if (respawnerField == null) {
                respawnerField = Level.class.getDeclaredField("respawner");
                respawnerField.setAccessible(true);
            }
            return respawnerField.get(Dungeon.level) != null;
        } catch (ReflectiveOperationException ex) {
            // If upstream changes this integration seam, fail closed instead of creating natural
            // spawning on floors where vanilla intentionally has no respawner.
            return false;
        }
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(MULTIPLIER_TENTHS, multiplierTenths);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        multiplierTenths = bundle.getInt(MULTIPLIER_TENTHS);
        if (multiplierTenths < MIN_MULTIPLIER_TENTHS
                || multiplierTenths > MAX_MULTIPLIER_TENTHS) {
            throw new IllegalStateException("Invalid CoHero enemy spawn multiplier in save");
        }

        trackedLevel = null;
        baseMobLimit = -1;
        extraSpawnCountdown = Float.NaN;
    }
}
