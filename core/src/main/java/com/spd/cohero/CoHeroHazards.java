package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blizzard;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ConfusionGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Electricity;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Freezing;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Inferno;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ParalyticGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.StenchGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.watabou.utils.PathFinder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Central movement-danger view for CoHero.
 *
 * Combines short-lived targeted-cell telegraphs with harmful stock SPD environmental blobs.
 * Environmental hazards are ignored when the companion is immune to their actual effect.
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

    public static boolean isDangerous(Char owner, int cell) {
        if (owner == null || Dungeon.level == null || cell < 0 || cell >= Dungeon.level.length()) {
            return false;
        }
        return isWarned(cell) || isEnvironmentalDanger(owner, cell);
    }

    public static boolean hasActiveHazards(Char owner) {
        syncLevel();
        pruneExpired();
        return !WARNED_UNTIL.isEmpty() || hasEnvironmentalHazard(owner);
    }

    public static boolean[] maskDangerous(Char owner, boolean[] passable) {
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

        if (owner != null) {
            for (int cell = 0; cell < result.length; cell++) {
                if (result[cell] && isEnvironmentalDanger(owner, cell)) {
                    result[cell] = false;
                }
            }
        }
        return result;
    }

    public static int nearbyDangerCount(Char owner, int cell) {
        int result = isDangerous(owner, cell) ? 1 : 0;
        if (Dungeon.level == null) {
            return result;
        }

        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent >= 0
                    && adjacent < Dungeon.level.length()
                    && Dungeon.level.distance(cell, adjacent) == 1
                    && isDangerous(owner, adjacent)) {
                result++;
            }
        }
        return result;
    }

    public static void clear() {
        WARNED_UNTIL.clear();
        trackedLevel = Dungeon.level;
    }

    private static boolean isWarned(int cell) {
        syncLevel();
        pruneExpired();
        Float until = WARNED_UNTIL.get(cell);
        return until != null && until >= Actor.now();
    }

    private static boolean hasEnvironmentalHazard(Char owner) {
        if (owner == null || Dungeon.level == null) {
            return false;
        }

        return activeFor(owner, Fire.class, Fire.class)
                || activeFor(owner, ToxicGas.class, ToxicGas.class)
                || activeFor(owner, CorrosiveGas.class, CorrosiveGas.class)
                || activeFor(owner, ParalyticGas.class, ParalyticGas.class)
                || activeFor(owner, ConfusionGas.class, ConfusionGas.class)
                || activeFor(owner, StenchGas.class, StenchGas.class)
                || activeFor(owner, Electricity.class, Electricity.class)
                || activeFor(owner, Freezing.class, Freezing.class)
                || activeFor(owner, Inferno.class, Fire.class)
                || activeFor(owner, Blizzard.class, Freezing.class)
                || activeVaultFlamesFor(owner);
    }

    private static boolean isEnvironmentalDanger(Char owner, int cell) {
        return presentFor(owner, cell, Fire.class, Fire.class)
                || presentFor(owner, cell, ToxicGas.class, ToxicGas.class)
                || presentFor(owner, cell, CorrosiveGas.class, CorrosiveGas.class)
                || presentFor(owner, cell, ParalyticGas.class, ParalyticGas.class)
                || presentFor(owner, cell, ConfusionGas.class, ConfusionGas.class)
                || presentFor(owner, cell, StenchGas.class, StenchGas.class)
                || presentFor(owner, cell, Electricity.class, Electricity.class)
                || presentFor(owner, cell, Freezing.class, Freezing.class)
                || presentFor(owner, cell, Inferno.class, Fire.class)
                || presentFor(owner, cell, Blizzard.class, Freezing.class)
                || presentVaultFlamesFor(owner, cell);
    }

    private static boolean activeFor(
            Char owner, Class<? extends Blob> blobType, Class<?> immunityType) {
        return !owner.isImmune(immunityType) && activeBlob(blobType);
    }

    private static boolean presentFor(
            Char owner, int cell, Class<? extends Blob> blobType, Class<?> immunityType) {
        return !owner.isImmune(immunityType) && blobPresent(cell, blobType);
    }

    private static boolean activeVaultFlamesFor(Char owner) {
        return !owner.isImmune(VaultFlameTraps.class)
                && !owner.isImmune(Fire.class)
                && activeBlob(VaultFlameTraps.class);
    }

    private static boolean presentVaultFlamesFor(Char owner, int cell) {
        return !owner.isImmune(VaultFlameTraps.class)
                && !owner.isImmune(Fire.class)
                && blobPresent(cell, VaultFlameTraps.class);
    }

    private static boolean activeBlob(Class<? extends Blob> type) {
        Blob blob = Dungeon.level.blobs.get(type);
        return blob != null && blob.volume > 0 && blob.cur != null;
    }

    private static boolean blobPresent(int cell, Class<? extends Blob> type) {
        Blob blob = Dungeon.level.blobs.get(type);
        return blob != null
                && blob.volume > 0
                && blob.cur != null
                && cell >= 0
                && cell < blob.cur.length
                && blob.cur[cell] > 0;
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
