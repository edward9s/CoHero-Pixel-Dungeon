package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.watabou.utils.PathFinder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Mirrors SPD's GameScene targeted-cell telegraphs into gameplay-readable warning state for CoHero.
 *
 * The visual warning lifetime is Actor.now() + delay; this tracker intentionally uses the same
 * clock so CoHero never predicts beyond what the player is shown.
 */
public final class CoHeroHazards {

    private static final HashMap<Integer, Float> WARNED_UNTIL = new HashMap<>();
    private static Object trackedLevel;

    private CoHeroHazards() {
    }

    public static void warn(int cell, float delay) {
        syncLevel();
        if (Dungeon.level == null || cell < 0 || cell >= Dungeon.level.length()) {
            return;
        }

        float until = Actor.now() + Math.max(0f, delay);
        Float current = WARNED_UNTIL.get(cell);
        if (current == null || until > current) {
            WARNED_UNTIL.put(cell, until);
        }
    }

    public static boolean isDangerous(int cell) {
        syncLevel();
        pruneExpired();
        Float until = WARNED_UNTIL.get(cell);
        return until != null && until >= Actor.now();
    }

    public static boolean hasActiveWarnings() {
        syncLevel();
        pruneExpired();
        return !WARNED_UNTIL.isEmpty();
    }

    public static boolean[] maskDangerous(boolean[] passable) {
        boolean[] result = passable.clone();
        syncLevel();
        pruneExpired();
        float now = Actor.now();
        for (Map.Entry<Integer, Float> entry : WARNED_UNTIL.entrySet()) {
            int cell = entry.getKey();
            if (entry.getValue() >= now && cell >= 0 && cell < result.length) {
                result[cell] = false;
            }
        }
        return result;
    }

    public static int nearbyDangerCount(int cell) {
        int result = isDangerous(cell) ? 1 : 0;
        if (Dungeon.level == null) {
            return result;
        }
        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent >= 0 && adjacent < Dungeon.level.length() && isDangerous(adjacent)) {
                result++;
            }
        }
        return result;
    }

    public static void clear() {
        WARNED_UNTIL.clear();
        trackedLevel = Dungeon.level;
    }

    private static void syncLevel() {
        if (Dungeon.level != trackedLevel) {
            WARNED_UNTIL.clear();
            trackedLevel = Dungeon.level;
        }
    }

    private static void pruneExpired() {
        float now = Actor.now();
        Iterator<Map.Entry<Integer, Float>> iterator = WARNED_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now) {
                iterator.remove();
            }
        }
    }
}
