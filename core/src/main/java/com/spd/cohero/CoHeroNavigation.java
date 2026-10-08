package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Owns ordinary CoHero navigation state and exploration.
 *
 * Guard and combat can reuse the same safety/knowledge predicates without owning
 * or mutating the exploration target.
 */
final class CoHeroNavigation {

    private static final int IDLE_HERO_TETHER_RADIUS = 10;
    private static final int IDLE_ROAM_RADIUS = 6;

    private final CoHeroAlly owner;
    private int explorationTarget = -1;
    private boolean explorationTargetRoaming;
    private PathFinder.Path policyPath;
    private int policyPathTarget = -1;

    CoHeroNavigation(CoHeroAlly owner) {
        this.owner = owner;
    }

    int explorationTarget() {
        return explorationTarget;
    }

    boolean explorationTargetRoaming() {
        return explorationTargetRoaming;
    }

    void restoreExplorationTarget(int target, boolean roaming) {
        explorationTarget = target;
        explorationTargetRoaming = target != -1 && roaming;
    }

    void clearExplorationTarget() {
        explorationTargetRoaming = false;
        if (explorationTarget == -1) {
            return;
        }
        explorationTarget = -1;
        clearPolicyPath();
        owner.clearNavigationPath();
    }

    boolean actExplore() {
        long validateStarted = owner.timings().startNanos();
        boolean targetInvalid;
        try {
            targetInvalid = explorationTarget == -1
                    || explorationTarget == owner.pos
                    || !Dungeon.level.passable[explorationTarget]
                    || (Actor.findChar(explorationTarget) != null
                        && Actor.findChar(explorationTarget) != owner)
                    || !isMovementSafe(explorationTarget)
                    || !isValidIdleHeroTarget(explorationTarget);
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.EXPLORE_VALIDATE, validateStarted);
        }

        if (targetInvalid) {
            owner.clearNavigationPath();
            long selectStarted = owner.timings().startNanos();
            try {
                explorationTarget = chooseExplorationTarget();
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.EXPLORE_SELECT, selectStarted);
            }
        }

        int oldPos = owner.pos;
        if (explorationTarget != -1) {
            int radius = explorationTargetRoaming ? IDLE_ROAM_RADIUS : IDLE_HERO_TETHER_RADIUS;
            owner.setMovementDecision(
                    !isInsideHeroRadius(owner.pos, radius)
                            ? "explore_return_to_hero"
                            : explorationTargetRoaming ? "idle_roam" : "explore",
                    explorationTarget);
        }

        boolean moved = false;
        if (explorationTarget != -1) {
            long moveStarted = owner.timings().startNanos();
            try {
                moved = moveTowardExplorationTarget(explorationTarget);
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.EXPLORE_MOVE, moveStarted);
            }
        }
        if (moved) {
            owner.spendActionTime(1 / owner.speed());
            owner.refreshOwnFieldOfView();
            owner.passiveSearch();
            return owner.finishMovementAnimation(oldPos);
        }

        owner.clearNavigationPath();
        long selectStarted = owner.timings().startNanos();
        try {
            explorationTarget = chooseExplorationTarget();
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.EXPLORE_SELECT, selectStarted);
        }
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean[] movementSafeMask() {
        boolean[] allCells = new boolean[Dungeon.level.length()];
        Arrays.fill(allCells, true);
        return applyMovementSafety(allCells);
    }

    private boolean[] applyMovementSafety(boolean[] passable) {
        boolean[] result = CoHeroHazards.maskDangerous(owner, passable);
        maskSleepingEnemyWakeRisk(result);

        CoHeroTurnContext context = owner.currentTurnContext();
        if (context != null) {
            context.maskPiranhaDanger(result);
        }
        return result;
    }

    boolean[] ordinarySafePassable(boolean knownOnly) {
        CoHeroTurnContext context = owner.currentTurnContext();
        return context == null
                ? buildOrdinarySafePassable(knownOnly)
                : context.ordinarySafePassable(knownOnly, this);
    }

    boolean[] nonCombatSafePassable(boolean knownOnly) {
        CoHeroTurnContext context = owner.currentTurnContext();
        return context == null
                ? buildNonCombatSafePassable(knownOnly)
                : context.nonCombatSafePassable(knownOnly, this);
    }

    private boolean[] buildNonCombatSafePassable(boolean knownOnly) {
        boolean[] result = buildOrdinarySafePassable(knownOnly);
        maskSleepingDetectionIncrease(result);
        result[owner.pos] = true;
        return result;
    }

    private void maskSleepingDetectionIncrease(boolean[] passable) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob == owner
                    || mob.alignment != Char.Alignment.ENEMY
                    || !mob.isAlive()
                    || mob.state != mob.SLEEPING
                    || mob.pos < 0
                    || mob.pos >= owner.fieldOfView.length
                    || !owner.fieldOfView[mob.pos]) {
                continue;
            }

            float currentChance =
                    mob.coHeroSleepingDetectionChanceAt(owner, owner.pos);
            for (int cell = 0; cell < passable.length; cell++) {
                if (cell == owner.pos || !passable[cell]) {
                    continue;
                }

                float candidateChance =
                        mob.coHeroSleepingDetectionChanceAt(owner, cell);
                if (candidateChance > currentChance + 0.0001f) {
                    passable[cell] = false;
                }
            }
        }
    }

    boolean[] buildOrdinarySafePassable(boolean knownOnly) {
        boolean[] result = applyMovementSafety(Dungeon.level.passable);

        if (knownOnly) {
            for (int cell = 0; cell < result.length; cell++) {
                if (cell != owner.pos && result[cell] && !isKnown(cell)) {
                    result[cell] = false;
                }
            }
        }

        // The current cell must remain a valid pathfinding origin even when CoHero is already
        // standing in danger or next to a sleeping enemy.
        result[owner.pos] = true;
        return result;
    }

    private void maskSleepingEnemyWakeRisk(boolean[] passable) {
        CoHeroTurnContext context = owner.currentTurnContext();
        if (context != null) {
            context.maskSleepingEnemyWakeRisk(passable);
            return;
        }

        for (Mob mob : Dungeon.level.mobs) {
            if (mob == owner
                    || mob.alignment != Char.Alignment.ENEMY
                    || !mob.isAlive()
                    || mob.state != mob.SLEEPING
                    || mob.pos < 0
                    || mob.pos >= owner.fieldOfView.length
                    || !owner.fieldOfView[mob.pos]) {
                continue;
            }

            passable[mob.pos] = false;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int cell = mob.pos + offset;
                if (Dungeon.level.insideMap(cell)
                        && Dungeon.level.distance(mob.pos, cell) == 1) {
                    passable[cell] = false;
                }
            }
        }
    }

    Boolean tryAvoidHazard() {
        if (!CoHeroHazards.isDangerous(owner, owner.pos)) {
            return null;
        }

        long started = owner.timings().startNanos();
        try {
            boolean[] dangerMask = CoHeroHazards.dangerMask(owner);
            boolean[] escapePassable = hazardEscapePassable();
            float deathGazeDeadline = CoHeroHazards.eyeDeathGazeDeadline(owner);
            boolean imminentDeathGaze = !Float.isInfinite(deathGazeDeadline);

            int target = -1;
            int bestDistance = Integer.MAX_VALUE;
            int bestNearbyDanger = Integer.MAX_VALUE;
            int bestHeroDistance = Integer.MAX_VALUE;

            if (!owner.rooted) {
                PathFinder.buildDistanceMap(owner.pos, escapePassable);

                for (int cell = 0; cell < Dungeon.level.length(); cell++) {
                    if (cell == owner.pos
                            || PathFinder.distance[cell] == Integer.MAX_VALUE
                            || dangerMask[cell]) {
                        continue;
                    }

                    int distance = PathFinder.distance[cell];
                    int nearbyDanger = CoHeroHazards.nearbyDangerCount(dangerMask, cell);
                    int heroDistance = Dungeon.hero == null
                            ? 0
                            : Dungeon.level.distance(cell, Dungeon.hero.pos);

                    if (target == -1
                            || distance < bestDistance
                            || (distance == bestDistance && nearbyDanger < bestNearbyDanger)
                            || (distance == bestDistance
                                && nearbyDanger == bestNearbyDanger
                                && heroDistance < bestHeroDistance)) {
                        target = cell;
                        bestDistance = distance;
                        bestNearbyDanger = nearbyDanger;
                        bestHeroDistance = heroDistance;
                    }
                }
            }

            boolean walkInTime = target != -1
                    && (!imminentDeathGaze
                        || canReachHazardSafetyBefore(bestDistance, deathGazeDeadline));
            if (walkInTime) {
                Boolean moved = moveTowardHazardSafety(target, escapePassable);
                if (moved != null) {
                    return moved;
                }
            }

            if (imminentDeathGaze) {
                // A charged Eye re-evaluates its live enemy position immediately before firing.
                // If ordinary walking cannot break that retarget window in time, use an immediate
                // displacement first. The safe mask already excludes the Eye's current FOV while
                // it is tracking CoHero, as well as every locked Death Gaze beam.
                boolean[] blinkSafe = movementSafeMask();
                if (owner.controlItems().tryHazardBlinkRunestone(blinkSafe)) {
                    return true;
                }

                // Invisibility only works after CoHero has already left every locked beam. In that
                // state it prevents a tracking Eye from replacing beamTarget with the new position.
                if (CoHeroHazards.eyeDeathGazeCanBreakWithInvisibility(owner)
                        && owner.survival().tryUseInvisibilityPotion()) {
                    owner.setMovementDecision("hazard_eye_invisibility", owner.pos);
                    return true;
                }

                // Random teleport is less controlled than Blink, but it is still preferable to
                // knowingly remaining in a Death Gaze that cannot be escaped before the Eye acts.
                if (owner.controlItems().tryUseTeleportationScroll()) {
                    owner.setMovementDecision("hazard_teleport", owner.pos);
                    return true;
                }
            }

            // Keep the previous best-effort behavior when no emergency resource is available.
            // This also covers non-Eye hazards, which do not have an actor-action deadline here.
            if (target != -1) {
                return moveTowardHazardSafety(target, escapePassable);
            }
            return null;
        } finally {
            owner.timings().record(owner, CoHeroTimings.Action.HAZARD_ESCAPE, started);
        }
    }

    private boolean canReachHazardSafetyBefore(int pathDistance, float deadline) {
        if (pathDistance <= 1) {
            // The first movement happens during the current CoHero action, before time advances.
            return true;
        }

        float moveTime = 1f / Math.max(0.001f, owner.speed());
        float arrivalTime = (pathDistance - 1) * moveTime;
        return arrivalTime + 0.001f < deadline;
    }

    private Boolean moveTowardHazardSafety(int target, boolean[] escapePassable) {
        int step = Dungeon.findStep(
                owner, target, escapePassable, owner.fieldOfView, true);
        if (step == -1 || step == owner.pos || !escapePassable[step]) {
            owner.clearNavigationPath();
            return null;
        }

        int oldPos = owner.pos;
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("hazard_escape", step);
        owner.clearNavigationPath();
        owner.move(step, true);
        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.finishMovementAnimation(oldPos);
    }

    private boolean[] hazardEscapePassable() {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell == owner.pos) {
                result[cell] = true;
                continue;
            }
            if (!result[cell]
                    || Actor.findChar(cell) != null
                    || !isSleepSafe(cell)
                    || !isPiranhaSafe(cell)) {
                result[cell] = false;
            }
        }
        return result;
    }
    Boolean tryLeavePiranhaDanger() {
        CoHeroTurnContext context = owner.currentTurnContext();
        if (context == null || !context.hasPiranhaDanger() || context.isPiranhaSafe(owner.pos)) {
            return null;
        }

        // The connected Piranha water body and its shoreline reach are one mandatory escape zone.
        // Do not let normal combat run even at full HP or when the fish is far away.
        if (!owner.rooted) {
            boolean[] escapePassable = piranhaEscapePassable();
            PathFinder.buildDistanceMap(owner.pos, escapePassable);

            int target = -1;
            int bestDistance = Integer.MAX_VALUE;
            int bestHeroDistance = Integer.MAX_VALUE;
            for (int cell = 0; cell < escapePassable.length; cell++) {
                if (cell == owner.pos
                        || !escapePassable[cell]
                        || Dungeon.level.water[cell]
                        || !context.isPiranhaSafe(cell)
                        || PathFinder.distance[cell] == Integer.MAX_VALUE
                        || Actor.findChar(cell) != null) {
                    continue;
                }

                int distance = PathFinder.distance[cell];
                int heroDistance = Dungeon.hero == null
                        ? 0
                        : Dungeon.level.distance(cell, Dungeon.hero.pos);
                if (target == -1
                        || distance < bestDistance
                        || (distance == bestDistance && heroDistance < bestHeroDistance)) {
                    target = cell;
                    bestDistance = distance;
                    bestHeroDistance = heroDistance;
                }
            }

            if (target != -1) {
                int step = Dungeon.findStep(
                        owner, target, escapePassable, owner.fieldOfView, true);
                if (step != -1 && step != owner.pos) {
                    int oldPos = owner.pos;
                    owner.allowAnyGuardMovement();
                    owner.setMovementDecision("piranha_escape", target);
                    owner.clearNavigationPath();
                    owner.move(step, true);
                    if (owner.pos != oldPos) {
                        owner.spendActionTime(1 / owner.speed());
                        owner.refreshOwnFieldOfView();
                        return owner.finishMovementAnimation(oldPos);
                    }
                }
            }
        }

        // If ordinary walking cannot leave the attack zone, use existing emergency displacement.
        // The shared safety mask excludes the whole Piranha danger zone.
        boolean[] blinkSafe = movementSafeMask();
        if (owner.controlItems().tryHazardBlinkRunestone(blinkSafe)) {
            return true;
        }
        if (owner.controlItems().tryUseTeleportationScroll()) {
            owner.setMovementDecision("piranha_teleport", owner.pos);
            return true;
        }

        // Still do not enter ordinary combat while trapped in the pool.
        owner.setMovementDecision("piranha_trapped", owner.pos);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    private boolean[] piranhaEscapePassable() {
        boolean[] result = CoHeroHazards.maskDangerous(owner, Dungeon.level.passable);
        maskSleepingEnemyWakeRisk(result);

        for (int cell = 0; cell < result.length; cell++) {
            if (cell != owner.pos && Actor.findChar(cell) != null) {
                result[cell] = false;
            }
        }

        // Piranha water and shoreline danger are intentionally allowed as transit while escaping;
        // the destination selection requires the nearest reachable non-water cell outside the
        // cached Piranha danger mask.
        result[owner.pos] = true;
        return result;
    }

    boolean getCloser(int target) {
        long guardStarted = owner.timings().startNanos();
        boolean guardRestricted;
        try {
            guardRestricted = owner.isGuardMovementRestricted();
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.MOVE_GUARD_CHECK, guardStarted);
        }

        boolean activeHazards = false;
        if (!guardRestricted) {
            long hazardStarted = owner.timings().startNanos();
            try {
                activeHazards = CoHeroHazards.hasActiveHazards(owner);
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.MOVE_HAZARD_CHECK, hazardStarted);
            }
        }

        boolean sleepingEnemy = false;
        if (!guardRestricted && !activeHazards) {
            long sleepStarted = owner.timings().startNanos();
            try {
                sleepingEnemy = hasVisibleSleepingEnemy();
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.MOVE_SLEEP_CHECK, sleepStarted);
            }
        }

        boolean piranhaDanger = !guardRestricted
                && !activeHazards
                && !sleepingEnemy
                && hasPiranhaDanger();

        if (!guardRestricted && !activeHazards && !sleepingEnemy && !piranhaDanger) {
            clearPolicyPath();
            long stockPathStarted = owner.timings().startNanos();
            try {
                return owner.getCloserWithoutCoHeroPolicy(target);
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.MOVE_STOCK_PATH, stockPathStarted);
            }
        }
        if (owner.rooted || target == owner.pos || !Dungeon.level.insideMap(target)) {
            return false;
        }

        boolean[] safePassable;
        long safeMaskStarted = owner.timings().startNanos();
        try {
            safePassable = ordinarySafePassable(false);
            owner.restrictGuardPassable(safePassable);
            safePassable[owner.pos] = true;
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.MOVE_SAFE_MASK, safeMaskStarted);
        }

        int step;
        long policyPathStarted = owner.timings().startNanos();
        try {
            step = nextPolicyStep(target, safePassable);
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.MOVE_POLICY_PATH, policyPathStarted);
        }
        if (step == -1) {
            return false;
        }

        long executeStarted = owner.timings().startNanos();
        try {
            owner.move(step);
            return owner.pos == step;
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.MOVE_EXECUTE, executeStarted);
        }
    }

    boolean getCloserNonCombat(int target) {
        if (owner.rooted || target == owner.pos || !Dungeon.level.insideMap(target)) {
            return false;
        }

        boolean[] safePassable = nonCombatSafePassable(false);
        owner.restrictGuardPassable(safePassable);
        safePassable[owner.pos] = true;

        int step = nextPolicyStep(target, safePassable);
        if (step == -1) {
            return false;
        }

        owner.move(step);
        return owner.pos == step;
    }

    private int nextPolicyStep(int target, boolean[] safePassable) {
        boolean rebuild = policyPath == null
                || policyPath.isEmpty()
                || policyPathTarget != target
                || !Dungeon.level.adjacent(owner.pos, policyPath.getFirst())
                || !safePassable[policyPath.getFirst()]
                || Actor.findChar(policyPath.getFirst()) != null;

        if (rebuild) {
            clearPolicyPath();
            policyPath = Dungeon.findPath(
                    owner, target, safePassable, owner.fieldOfView, true);
            policyPathTarget = target;
        }

        if (policyPath == null || policyPath.isEmpty()) {
            clearPolicyPath();
            return -1;
        }

        int step = policyPath.removeFirst();
        if (!safePassable[step] || Actor.findChar(step) != null) {
            clearPolicyPath();
            return -1;
        }
        return step;
    }

    void clearPolicyPath() {
        policyPath = null;
        policyPathTarget = -1;
    }

    boolean isMovementSafe(int cell) {
        return !CoHeroHazards.isDangerous(owner, cell)
                && isSleepSafe(cell)
                && isPiranhaSafe(cell);
    }

    private boolean hasPiranhaDanger() {
        CoHeroTurnContext context = owner.currentTurnContext();
        return context != null && context.hasPiranhaDanger();
    }

    private boolean isPiranhaSafe(int cell) {
        CoHeroTurnContext context = owner.currentTurnContext();
        return context == null || context.isPiranhaSafe(cell);
    }

    private boolean hasVisibleSleepingEnemy() {
        CoHeroTurnContext context = owner.currentTurnContext();
        if (context != null) {
            return context.hasVisibleSleepingEnemy();
        }

        for (Mob mob : Dungeon.level.mobs) {
            if (mob != owner
                    && mob.alignment == Char.Alignment.ENEMY
                    && mob.isAlive()
                    && mob.state == mob.SLEEPING
                    && mob.pos >= 0
                    && mob.pos < owner.fieldOfView.length
                    && owner.fieldOfView[mob.pos]) {
                return true;
            }
        }
        return false;
    }

    boolean isKnown(int cell) {
        return cell >= 0
                && cell < Dungeon.level.length()
                && (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell]);
    }

    private boolean isSleepSafe(int cell) {
        CoHeroTurnContext context = owner.currentTurnContext();
        if (context != null) {
            return context.isSleepSafe(cell);
        }

        for (Mob mob : Dungeon.level.mobs) {
            if (mob != owner
                    && mob.alignment == Char.Alignment.ENEMY
                    && mob.isAlive()
                    && mob.state == mob.SLEEPING
                    && owner.fieldOfView[mob.pos]
                    && Dungeon.level.distance(cell, mob.pos) <= 1) {
                return false;
            }
        }
        return true;
    }

    private boolean moveTowardExplorationTarget(int target) {
        if (owner.rooted || target == owner.pos || !Dungeon.level.insideMap(target)) {
            return false;
        }

        return getCloserNonCombat(target);
    }

    private int chooseExplorationTarget() {
        boolean[] passable = nonCombatSafePassable(false);
        PathFinder.buildDistanceMap(owner.pos, passable);

        if (!isInsideIdleHeroTether(owner.pos)) {
            int returnTarget = chooseIdleHeroReturnTarget(passable, IDLE_HERO_TETHER_RADIUS);
            if (returnTarget != -1) {
                explorationTargetRoaming = false;
                return returnTarget;
            }
        }

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
                    || !isInsideIdleHeroTether(cell)
                    || !passable[cell]
                    || !Dungeon.level.discoverable[cell]
                    || (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell])
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant == null || occupant == owner) {
                unknown.add(cell);
            }
        }

        if (!unknown.isEmpty()) {
            explorationTargetRoaming = false;
            return Random.element(unknown);
        }

        // With nothing left to explore, return close to Hero before local roaming.
        if (!isInsideIdleRoamRadius(owner.pos)) {
            int returnTarget = chooseIdleHeroReturnTarget(passable, IDLE_ROAM_RADIUS);
            if (returnTarget != -1) {
                explorationTargetRoaming = true;
                return returnTarget;
            }
        }

        // Weighted reservoir sampling keeps nearby cells likelier without losing randomness.
        int roamTarget = -1;
        int totalWeight = 0;
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
                    || !isInsideIdleRoamRadius(cell)
                    || !isKnown(cell)
                    || !passable[cell]
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int pathDistance = PathFinder.distance[cell];
            int weight = pathDistance == 1 ? 8 : pathDistance == 2 ? 4
                    : pathDistance == 3 ? 2 : 1;
            totalWeight += weight;
            if (Random.Int(totalWeight) < weight) {
                roamTarget = cell;
            }
        }

        explorationTargetRoaming = roamTarget != -1;
        return roamTarget;
    }

    private int chooseIdleHeroReturnTarget(boolean[] passable, int radius) {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            return -1;
        }

        int ownerHeroDistance = Dungeon.level.distance(owner.pos, Dungeon.hero.pos);
        int bestInside = -1;
        int bestInsidePathDistance = Integer.MAX_VALUE;
        int bestInsideHeroDistance = Integer.MAX_VALUE;
        int bestFallback = -1;
        int bestFallbackHeroDistance = Integer.MAX_VALUE;
        int bestFallbackPathDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < passable.length; cell++) {
            if (cell == owner.pos
                    || !passable[cell]
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int heroDistance = Dungeon.level.distance(cell, Dungeon.hero.pos);
            int pathDistance = PathFinder.distance[cell];

            if (heroDistance <= radius) {
                if (bestInside == -1
                        || pathDistance < bestInsidePathDistance
                        || (pathDistance == bestInsidePathDistance
                            && heroDistance < bestInsideHeroDistance)
                        || (pathDistance == bestInsidePathDistance
                            && heroDistance == bestInsideHeroDistance
                            && cell < bestInside)) {
                    bestInside = cell;
                    bestInsidePathDistance = pathDistance;
                    bestInsideHeroDistance = heroDistance;
                }
                continue;
            }

            if (heroDistance >= ownerHeroDistance) {
                continue;
            }

            if (bestFallback == -1
                    || heroDistance < bestFallbackHeroDistance
                    || (heroDistance == bestFallbackHeroDistance
                        && pathDistance < bestFallbackPathDistance)
                    || (heroDistance == bestFallbackHeroDistance
                        && pathDistance == bestFallbackPathDistance
                        && cell < bestFallback)) {
                bestFallback = cell;
                bestFallbackHeroDistance = heroDistance;
                bestFallbackPathDistance = pathDistance;
            }
        }

        return bestInside != -1 ? bestInside : bestFallback;
    }

    private boolean isInsideIdleHeroTether(int cell) {
        return isInsideHeroRadius(cell, IDLE_HERO_TETHER_RADIUS);
    }

    private boolean isInsideIdleRoamRadius(int cell) {
        return isInsideHeroRadius(cell, IDLE_ROAM_RADIUS);
    }

    private boolean isInsideHeroRadius(int cell, int radius) {
        return Dungeon.hero == null
                || !Dungeon.hero.isAlive()
                || Dungeon.level.distance(cell, Dungeon.hero.pos) <= radius;
    }

    private boolean isValidIdleHeroTarget(int cell) {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            return true;
        }

        int radius = explorationTargetRoaming ? IDLE_ROAM_RADIUS : IDLE_HERO_TETHER_RADIUS;
        int ownerDistance = Dungeon.level.distance(owner.pos, Dungeon.hero.pos);
        int targetDistance = Dungeon.level.distance(cell, Dungeon.hero.pos);
        return ownerDistance <= radius
                ? targetDistance <= radius
                : targetDistance < ownerDistance;
    }
}
