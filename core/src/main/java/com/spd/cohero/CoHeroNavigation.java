package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Owns ordinary CoHero navigation state and exploration.
 *
 * Guard and combat can reuse the same safety/knowledge predicates without owning
 * or mutating the exploration target.
 */
final class CoHeroNavigation {

    private final CoHeroAlly owner;
    private int explorationTarget = -1;

    CoHeroNavigation(CoHeroAlly owner) {
        this.owner = owner;
    }

    int explorationTarget() {
        return explorationTarget;
    }

    void restoreExplorationTarget(int target) {
        explorationTarget = target;
    }

    void clearExplorationTarget() {
        explorationTarget = -1;
    }

    boolean actExplore() {
        if (explorationTarget == -1
                || explorationTarget == owner.pos
                || !Dungeon.level.passable[explorationTarget]
                || (Actor.findChar(explorationTarget) != null
                    && Actor.findChar(explorationTarget) != owner)
                || !isMovementSafe(explorationTarget)) {
            explorationTarget = chooseExplorationTarget();
        }

        int oldPos = owner.pos;
        if (explorationTarget != -1) {
            owner.setMovementDecision("explore", explorationTarget);
        }
        if (explorationTarget != -1 && moveTowardExplorationTarget(explorationTarget)) {
            owner.spendActionTime(1 / owner.speed());
            owner.refreshOwnFieldOfView();
            return owner.finishMovementAnimation(oldPos);
        }

        explorationTarget = chooseExplorationTarget();
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean[] ordinarySafePassable(boolean knownOnly) {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell == owner.pos) {
                result[cell] = true;
                continue;
            }
            if (!result[cell]
                    || !isMovementSafe(cell)
                    || (knownOnly && !isKnown(cell))) {
                result[cell] = false;
            }
        }
        return result;
    }

    Boolean tryAvoidHazard() {
        if (owner.rooted || !CoHeroHazards.isDangerous(owner, owner.pos)) {
            return null;
        }

        int best = -1;
        int bestNearbyDanger = Integer.MAX_VALUE;
        int bestHeroDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)) {
                continue;
            }

            int nearbyDanger = CoHeroHazards.nearbyDangerCount(owner, cell);
            int heroDistance = Dungeon.hero == null
                    ? 0
                    : Dungeon.level.distance(cell, Dungeon.hero.pos);

            if (best == -1
                    || nearbyDanger < bestNearbyDanger
                    || (nearbyDanger == bestNearbyDanger && heroDistance < bestHeroDistance)) {
                best = cell;
                bestNearbyDanger = nearbyDanger;
                bestHeroDistance = heroDistance;
            }
        }

        if (best == -1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("hazard_escape", best);
        owner.move(best, true);
        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.finishMovementAnimation(oldPos);
    }

    boolean getCloser(int target) {
        if (!owner.isGuardMovementRestricted()
                && !CoHeroHazards.hasActiveHazards(owner)
                && !hasVisibleSleepingEnemy()) {
            return owner.getCloserWithoutCoHeroPolicy(target);
        }
        if (owner.rooted || target == owner.pos || !Dungeon.level.insideMap(target)) {
            return false;
        }

        boolean[] safePassable = ordinarySafePassable(false);
        owner.restrictGuardPassable(safePassable);

        safePassable[owner.pos] = true;
        int step = Dungeon.findStep(owner, target, safePassable, owner.fieldOfView, true);
        if (step == -1 || !isMovementSafe(step)) {
            owner.clearNavigationPath();
            return false;
        }

        owner.clearNavigationPath();
        owner.move(step);
        return owner.pos == step;
    }

    boolean isMovementSafe(int cell) {
        return !CoHeroHazards.isDangerous(owner, cell) && isSleepSafe(cell);
    }

    private boolean hasVisibleSleepingEnemy() {
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

        boolean[] passable = ordinarySafePassable(false);
        int step = Dungeon.findStep(owner, target, passable, owner.fieldOfView, true);
        if (step == -1 || !isMovementSafe(step)) {
            owner.clearNavigationPath();
            return false;
        }

        owner.clearNavigationPath();
        owner.move(step, true);
        return true;
    }

    private int chooseExplorationTarget() {
        boolean[] passable = ordinarySafePassable(false);
        PathFinder.buildDistanceMap(owner.pos, passable);

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
                    || !Dungeon.level.passable[cell]
                    || !Dungeon.level.discoverable[cell]
                    || (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell])
                    || PathFinder.distance[cell] == Integer.MAX_VALUE
                    || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant == null || occupant == owner) {
                unknown.add(cell);
            }
        }

        if (!unknown.isEmpty()) {
            return Random.element(unknown);
        }

        ArrayList<Integer> roaming = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
                    || !isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || PathFinder.distance[cell] == Integer.MAX_VALUE
                    || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant == null || occupant == owner) {
                roaming.add(cell);
            }
        }

        return roaming.isEmpty() ? -1 : Random.element(roaming);
    }
}
