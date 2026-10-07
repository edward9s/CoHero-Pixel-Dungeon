package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Piranha;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

/**
 * Per-decision cache for live facts that are expensive to rebuild and remain valid until CoHero
 * spends the turn.
 *
 * Mutable arrays are never exposed directly. Callers keep the old fresh-array semantics while the
 * expensive hazard and sleeping-enemy scans are shared within the decision.
 */
final class CoHeroTurnContext {

    private final CoHeroAlly owner;
    private Level level;
    private boolean active;
    private final ArrayList<Mob> visibleAwakeEnemies = new ArrayList<>();
    private final ArrayList<Mob> visibleSleepingEnemies = new ArrayList<>();
    private final ArrayList<Mob> heroSupportCandidates = new ArrayList<>();
    private final ArrayList<Piranha> visiblePiranhas = new ArrayList<>();
    private boolean[] piranhaDangerMask;
    private boolean heroSupportThreatEvaluated;
    private Mob heroSupportThreat;

    private boolean[] ordinarySafePassable;
    private boolean[] knownSafePassable;

    CoHeroTurnContext(CoHeroAlly owner) {
        if (owner == null) {
            throw new IllegalArgumentException("CoHero turn context requires an owner");
        }
        this.owner = owner;
    }

    void begin() {
        if (active) {
            throw new IllegalStateException("CoHero turn context started twice");
        }
        if (Dungeon.level == null) {
            throw new IllegalStateException("CoHero turn context started without a level");
        }
        if (owner.fieldOfView == null || owner.fieldOfView.length != Dungeon.level.length()) {
            throw new IllegalStateException("CoHero turn context started without current field of view");
        }

        level = Dungeon.level;
        active = true;
        visibleAwakeEnemies.clear();
        visibleSleepingEnemies.clear();
        heroSupportCandidates.clear();
        visiblePiranhas.clear();
        piranhaDangerMask = null;
        heroSupportThreatEvaluated = false;
        heroSupportThreat = null;
        ordinarySafePassable = null;
        knownSafePassable = null;
        scanVisibleEnemies();
    }

    void end() {
        active = false;
        level = null;
        visibleAwakeEnemies.clear();
        visibleSleepingEnemies.clear();
        heroSupportCandidates.clear();
        visiblePiranhas.clear();
        piranhaDangerMask = null;
        heroSupportThreat = null;
        ordinarySafePassable = null;
        knownSafePassable = null;
    }

    boolean isActive() {
        return active;
    }

    ArrayList<Mob> visibleAwakeEnemies() {
        assertActive();
        return visibleAwakeEnemies;
    }

    boolean[] ordinarySafePassable(boolean knownOnly, CoHeroNavigation navigation) {
        assertActive();
        if (ordinarySafePassable == null) {
            ordinarySafePassable = navigation.buildOrdinarySafePassable(false);
        }

        if (!knownOnly) {
            return ordinarySafePassable.clone();
        }

        if (knownSafePassable == null) {
            knownSafePassable = ordinarySafePassable.clone();
            for (int cell = 0; cell < knownSafePassable.length; cell++) {
                if (cell != owner.pos
                        && knownSafePassable[cell]
                        && !navigation.isKnown(cell)) {
                    knownSafePassable[cell] = false;
                }
            }
        }
        return knownSafePassable.clone();
    }

    Mob heroSupportThreat() {
        assertActive();
        if (heroSupportThreatEvaluated) {
            return heroSupportThreat;
        }
        heroSupportThreatEvaluated = true;

        if (Dungeon.hero == null
                || !Dungeon.hero.isAlive()
                || heroSupportCandidates.isEmpty()) {
            return null;
        }

        for (Mob mob : heroSupportCandidates) {
            if (!mob.isAlive()) {
                continue;
            }

            int distance = level.distance(Dungeon.hero.pos, mob.pos);
            if (distance <= CoHeroSupportController.MELEE_SUPPORT_RADIUS
                    || (distance <= CoHeroSupportController.RANGED_SUPPORT_RADIUS
                        && mob.coHeroCanAttackFrom(mob.pos, Dungeon.hero))) {
                heroSupportThreat = mob;
                break;
            }
        }
        return heroSupportThreat;
    }

    boolean hasVisibleSleepingEnemy() {
        assertActive();
        return !visibleSleepingEnemies.isEmpty();
    }

    boolean isSleepSafe(int cell) {
        assertActive();
        if (cell < 0 || cell >= level.length()) {
            return true;
        }
        if (visibleSleepingEnemies.isEmpty()) {
            return true;
        }

        for (Mob mob : visibleSleepingEnemies) {
            if (level.distance(cell, mob.pos) <= 1) {
                return false;
            }
        }
        return true;
    }

    void maskSleepingEnemyWakeRisk(boolean[] passable) {
        assertActive();
        if (passable == null || passable.length != level.length()) {
            throw new IllegalArgumentException("Invalid CoHero movement mask length");
        }
        if (visibleSleepingEnemies.isEmpty()) {
            return;
        }

        for (Mob mob : visibleSleepingEnemies) {
            passable[mob.pos] = false;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int cell = mob.pos + offset;
                if (level.insideMap(cell) && level.distance(mob.pos, cell) == 1) {
                    passable[cell] = false;
                }
            }
        }
    }

    boolean hasVisiblePiranhaDanger() {
        assertActive();
        return !visiblePiranhas.isEmpty();
    }

    boolean isPiranhaSafe(int cell) {
        assertActive();
        if (cell < 0 || cell >= level.length() || visiblePiranhas.isEmpty()) {
            return true;
        }
        return !piranhaDangerMask()[cell];
    }

    void maskPiranhaDanger(boolean[] passable) {
        assertActive();
        if (passable == null || passable.length != level.length()) {
            throw new IllegalArgumentException("Invalid CoHero Piranha movement mask length");
        }
        if (visiblePiranhas.isEmpty()) {
            return;
        }

        boolean[] danger = piranhaDangerMask();
        for (int cell = 0; cell < passable.length; cell++) {
            if (danger[cell]) {
                passable[cell] = false;
            }
        }
    }

    private boolean[] piranhaDangerMask() {
        if (piranhaDangerMask != null) {
            return piranhaDangerMask;
        }

        int length = level.length();
        boolean[] danger = new boolean[length];
        int[] queue = new int[length];
        int head = 0;
        int tail = 0;

        for (Piranha piranha : visiblePiranhas) {
            int cell = piranha.pos;
            if (cell < 0
                    || cell >= length
                    || !level.water[cell]
                    || !level.passable[cell]
                    || danger[cell]) {
                continue;
            }
            danger[cell] = true;
            queue[tail++] = cell;
        }

        while (head < tail) {
            int cell = queue[head++];
            for (int offset : PathFinder.NEIGHBOURS8) {
                int adjacent = cell + offset;
                if (!level.insideMap(adjacent)
                        || level.distance(cell, adjacent) != 1
                        || danger[adjacent]
                        || !level.water[adjacent]
                        || !level.passable[adjacent]) {
                    continue;
                }
                danger[adjacent] = true;
                queue[tail++] = adjacent;
            }
        }

        // queue[0..tail) is exactly the connected water component(s). Expand only from those
        // cells so shoreline marking is O(pool size), not another full-level scan.
        int waterCount = tail;
        for (int i = 0; i < waterCount; i++) {
            int cell = queue[i];
            for (int offset : PathFinder.NEIGHBOURS8) {
                int adjacent = cell + offset;
                if (level.insideMap(adjacent)
                        && level.distance(cell, adjacent) == 1
                        && level.passable[adjacent]) {
                    danger[adjacent] = true;
                }
            }
        }

        piranhaDangerMask = danger;
        return piranhaDangerMask;
    }

    private void scanVisibleEnemies() {
        for (Mob mob : level.mobs) {
            if (mob == owner || !mob.isAlive()) {
                continue;
            }

            boolean passiveStatue = mob instanceof Statue && mob.state == mob.PASSIVE;
            if (!passiveStatue
                    && (mob.alignment == Char.Alignment.ENEMY || mob instanceof Mimic)) {
                heroSupportCandidates.add(mob);
            }

            if (mob.alignment != Char.Alignment.ENEMY || passiveStatue) {
                continue;
            }

            if (mob instanceof Piranha) {
                if (mob.pos < 0 || mob.pos >= owner.fieldOfView.length) {
                    throw new IllegalStateException(
                            "Piranha has invalid position: "
                                    + mob.getClass().getSimpleName() + "@" + mob.pos);
                }
                if (mob.invisible <= 0 && owner.fieldOfView[mob.pos]) {
                    visiblePiranhas.add((Piranha) mob);
                }
            }

            if (mob.state == mob.SLEEPING) {
                if (mob.pos < 0 || mob.pos >= owner.fieldOfView.length) {
                    throw new IllegalStateException(
                            "Sleeping CoHero enemy has invalid position: "
                                    + mob.getClass().getSimpleName() + "@" + mob.pos);
                }
                if (owner.fieldOfView[mob.pos]) {
                    visibleSleepingEnemies.add(mob);
                }
            }

            if (mob.invisible > 0
                    || mob.state == mob.SLEEPING
                    || mob.buff(Challenge.SpectatorFreeze.class) != null) {
                continue;
            }
            if (mob.pos < 0 || mob.pos >= owner.fieldOfView.length) {
                throw new IllegalStateException(
                        "Visible CoHero enemy has invalid position: "
                                + mob.getClass().getSimpleName() + "@" + mob.pos);
            }
            if (owner.fieldOfView[mob.pos]) {
                visibleAwakeEnemies.add(mob);
            }
        }
    }

    private void assertActive() {
        if (!active || Dungeon.level != level || owner.currentTurnContext() != this) {
            throw new IllegalStateException("Stale CoHero turn context");
        }
    }
}
