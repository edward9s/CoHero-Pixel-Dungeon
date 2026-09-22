package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Burning;
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
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultBossElemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.quest.vault.VaultBossElemental.FireWall;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom.EternalFire;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;
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

    private static boolean[] vaultMechanismDanger;
    private static Char vaultMechanismOwner;
    private static float vaultMechanismAt = Float.NaN;
    private static int vaultMechanismInvisibility = -1;
    private static boolean vaultMechanismFlameImmune;

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
        return isWarned(cell)
                || isKnownActiveTrap(cell)
                || isVaultMechanismDanger(owner, cell)
                || isEnvironmentalDanger(owner, cell);
    }

    public static boolean hasActiveHazards(Char owner) {
        syncLevel();
        pruneExpired();
        return !WARNED_UNTIL.isEmpty()
                || hasKnownActiveTrap()
                || hasVaultMechanismHazard(owner)
                || hasEnvironmentalHazard(owner);
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

        for (int cell = 0; cell < result.length; cell++) {
            if (result[cell] && isKnownActiveTrap(cell)) {
                result[cell] = false;
            }
        }

        if (owner != null) {
            ensureVaultMechanismDanger(owner);
            for (int cell = 0; cell < result.length; cell++) {
                if (result[cell] && vaultMechanismDanger[cell]) {
                    result[cell] = false;
                }
            }
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
        invalidateVaultMechanismCache();
    }

    private static boolean isWarned(int cell) {
        syncLevel();
        pruneExpired();
        Float until = WARNED_UNTIL.get(cell);
        return until != null && until >= Actor.now();
    }

    private static boolean hasKnownActiveTrap() {
        if (Dungeon.level == null || Dungeon.level.traps == null) {
            return false;
        }
        for (int cell : Dungeon.level.traps.keyArray()) {
            if (isKnownActiveTrap(cell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownActiveTrap(int cell) {
        if (Dungeon.level == null
                || Dungeon.level.traps == null
                || cell < 0
                || cell >= Dungeon.level.length()) {
            return false;
        }

        Trap trap = Dungeon.level.traps.get(cell);
        return trap != null && trap.active && trap.visible;
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
                || activeFor(owner, Tengu.FireAbility.FireBlob.class, Fire.class)
                || hasTenguBombHazard()
                || activeVaultFlamesFor(owner)
                || activeEternalFireFor(owner)
                || activeVaultFireWallFor(owner);
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
                || presentFor(owner, cell, Tengu.FireAbility.FireBlob.class, Fire.class)
                || isTenguBombDanger(cell)
                || presentVaultFlamesFor(owner, cell)
                || presentEternalFireFor(owner, cell)
                || presentVaultFireWallFor(owner, cell);
    }

    private static boolean hasTenguBombHazard() {
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Tengu)) {
                continue;
            }
            for (Tengu.BombAbility bomb : ch.buffs(Tengu.BombAbility.class)) {
                if (bomb.bombPos >= 0 && bomb.bombPos < Dungeon.level.length()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTenguBombDanger(int cell) {
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Tengu)) {
                continue;
            }
            for (Tengu.BombAbility bomb : ch.buffs(Tengu.BombAbility.class)) {
                if (tenguBombReaches(bomb.bombPos, cell)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tenguBombReaches(int bombPos, int cell) {
        if (bombPos < 0
                || bombPos >= Dungeon.level.length()
                || cell < 0
                || cell >= Dungeon.level.length()) {
            return false;
        }
        if (cell == bombPos) {
            return true;
        }

        for (int firstOffset : PathFinder.NEIGHBOURS8) {
            int first = bombPos + firstOffset;
            if (!validBombStep(bombPos, first)) {
                continue;
            }
            if (first == cell) {
                return true;
            }

            for (int secondOffset : PathFinder.NEIGHBOURS8) {
                int second = first + secondOffset;
                if (validBombStep(first, second) && second == cell) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean validBombStep(int from, int to) {
        return to >= 0
                && to < Dungeon.level.length()
                && Dungeon.level.distance(from, to) == 1
                && !Dungeon.level.solid[to];
    }

    private static boolean hasVaultMechanismHazard(Char owner) {
        if (owner == null || Dungeon.level == null) {
            return false;
        }
        ensureVaultMechanismDanger(owner);
        for (boolean dangerous : vaultMechanismDanger) {
            if (dangerous) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVaultMechanismDanger(Char owner, int cell) {
        ensureVaultMechanismDanger(owner);
        return vaultMechanismDanger != null
                && cell >= 0
                && cell < vaultMechanismDanger.length
                && vaultMechanismDanger[cell];
    }

    private static void ensureVaultMechanismDanger(Char owner) {
        syncLevel();
        if (owner == null || Dungeon.level == null) {
            vaultMechanismDanger = null;
            return;
        }

        boolean flameImmune = owner.isImmune(VaultFlameTraps.class) || owner.isImmune(Fire.class);
        float now = Actor.now();
        if (vaultMechanismDanger != null
                && vaultMechanismDanger.length == Dungeon.level.length()
                && vaultMechanismOwner == owner
                && Float.compare(vaultMechanismAt, now) == 0
                && vaultMechanismInvisibility == owner.invisible
                && vaultMechanismFlameImmune == flameImmune) {
            return;
        }

        vaultMechanismDanger = new boolean[Dungeon.level.length()];
        vaultMechanismOwner = owner;
        vaultMechanismAt = now;
        vaultMechanismInvisibility = owner.invisible;
        vaultMechanismFlameImmune = flameImmune;

        if (!flameImmune) {
            markImminentVaultFlames();
        }
        markImminentVaultLasers();
        if (owner.invisible == 0) {
            markImminentVaultScans();
        }
    }

    private static void markImminentVaultFlames() {
        Blob blob = Dungeon.level.blobs.get(VaultFlameTraps.class);
        if (!(blob instanceof VaultFlameTraps)) {
            return;
        }

        VaultFlameTraps traps = (VaultFlameTraps) blob;
        if (traps.afterTriggerCooldowns == null
                || traps.curCooldowns == null
                || traps.triggersAfterCooldown == null) {
            return;
        }

        int limit = Math.min(vaultMechanismDanger.length,
                Math.min(traps.afterTriggerCooldowns.length,
                        Math.min(traps.curCooldowns.length, traps.triggersAfterCooldown.length)));
        for (int cell = 0; cell < limit; cell++) {
            int repeatCooldown = traps.afterTriggerCooldowns[cell];
            if (repeatCooldown < 0 || traps.triggersAfterCooldown[cell] <= 0) {
                continue;
            }

            int cooldown = traps.curCooldowns[cell];
            if (cooldown <= 0) {
                cooldown = repeatCooldown;
            }
            if (cooldown <= 1) {
                vaultMechanismDanger[cell] = true;
            }
        }
    }

    private static void markImminentVaultLasers() {
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof VaultLaser)) {
                continue;
            }
            VaultLaser laser = (VaultLaser) ch;
            if (laser.curCooldown > 1 || laser.laserDirs == null || laser.laserDirs.length == 0) {
                continue;
            }
            if (laser.laserDirIdx < 0 || laser.laserDirIdx >= laser.laserDirs.length) {
                continue;
            }

            Ballistica beam = new Ballistica(
                    laser.pos,
                    laser.laserDirs[laser.laserDirIdx],
                    Ballistica.STOP_SOLID);
            for (int cell : beam.subPath(1, beam.dist)) {
                if (cell >= 0 && cell < vaultMechanismDanger.length) {
                    vaultMechanismDanger[cell] = true;
                }
            }
        }
    }

    private static void markImminentVaultScans() {
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof VaultSentry)) {
                continue;
            }
            VaultSentry sentry = (VaultSentry) ch;
            if (sentry.curCooldown > 1 || sentry.scanDirs == null || sentry.scanDirs.length == 0) {
                continue;
            }
            if (sentry.scanDirIdx < 0 || sentry.scanDirIdx >= sentry.scanDirs.length) {
                continue;
            }

            boolean[] sentryFov = new boolean[Dungeon.level.length()];
            Dungeon.level.updateFieldOfView(sentry, sentryFov);
            for (int scanDir : sentry.scanDirs[sentry.scanDirIdx]) {
                Ballistica aim = new Ballistica(sentry.pos, scanDir, Ballistica.WONT_STOP);
                ConeAOE scan = new ConeAOE(
                        aim,
                        sentry.scanLength,
                        sentry.scanWidth,
                        Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);

                if (scan.cells.isEmpty() && aim.path.size() >= 2) {
                    scan.cells.add(aim.path.get(1));
                }
                for (int cell : scan.cells) {
                    if (cell >= 0
                            && cell < vaultMechanismDanger.length
                            && sentryFov[cell]) {
                        vaultMechanismDanger[cell] = true;
                    }
                }
            }
        }
    }

    private static void invalidateVaultMechanismCache() {
        vaultMechanismDanger = null;
        vaultMechanismOwner = null;
        vaultMechanismAt = Float.NaN;
        vaultMechanismInvisibility = -1;
        vaultMechanismFlameImmune = false;
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

    private static boolean activeEternalFireFor(Char owner) {
        return !owner.isImmune(EternalFire.class) && activeBlob(EternalFire.class);
    }

    private static boolean presentEternalFireFor(Char owner, int cell) {
        if (owner.isImmune(EternalFire.class)) {
            return false;
        }

        Blob fire = Dungeon.level.blobs.get(EternalFire.class);
        if (fire == null || fire.volume <= 0 || fire.cur == null) {
            return false;
        }

        // EternalFire ignites characters on the wall cell and on all four cardinally adjacent
        // cells during evolve(), so the danger region is one tile wider than the blob itself.
        if (blobPresent(cell, EternalFire.class)) {
            return true;
        }
        for (int offset : PathFinder.NEIGHBOURS4) {
            int adjacent = cell + offset;
            if (adjacent >= 0
                    && adjacent < Dungeon.level.length()
                    && Dungeon.level.distance(cell, adjacent) == 1
                    && blobPresent(adjacent, EternalFire.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean activeVaultFireWallFor(Char owner) {
        if (owner.isImmune(Burning.class)) {
            return false;
        }
        for (Char ch : Actor.chars()) {
            if (ch instanceof VaultBossElemental && ch.buff(FireWall.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean presentVaultFireWallFor(Char owner, int cell) {
        if (owner.isImmune(Burning.class)) {
            return false;
        }
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof VaultBossElemental)) {
                continue;
            }
            FireWall wall = ch.buff(FireWall.class);
            if (wall != null && wall.coHeroDangerAt(cell)) {
                return true;
            }
        }
        return false;
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
            invalidateVaultMechanismCache();
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
