package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Point;

import java.util.ArrayList;

/**
 * Owns current-turn combat goals that change where CoHero wants a fight to happen.
 *
 * Objective behavior is derived from the live level and currently visible threats every turn.
 * No lure target, lure state, or remembered enemy is persisted across turns.
 */
final class CoHeroCombatObjectiveController {

    private final CoHeroAlly owner;
    private EngagementObjective objective;

    CoHeroCombatObjectiveController(CoHeroAlly owner) {
        this.owner = owner;
    }

    void update() {
        objective = resolveSacrificialFireObjective();
    }

    /**
     * Runs after survival actions but before ordinary offense.
     *
     * Ranged pressure bypasses the objective completely and restores unrestricted combat movement.
     * A melee threat already inside the engagement zone also uses ordinary combat. Only visible
     * melee threats that are still outside the zone are lured inward.
     */
    Boolean actBeforeOffense(
            ArrayList<Mob> attackableThreats, ArrayList<Mob> visibleThreats) {
        if (objective == null || attackableThreats == null || attackableThreats.isEmpty()) {
            return null;
        }

        Mob rangedThreat = firstCurrentRangedThreat(attackableThreats);
        if (rangedThreat != null) {
            // Guard scope may already have been prepared earlier in the turn. A ranged enemy that
            // can attack into the room must hand control to unrestricted ordinary combat instead
            // of leaving CoHero unable to close or reposition.
            owner.allowAnyGuardMovement();
            if (owner.debugLogEnabled()) {
                owner.logDebug("[CoHeroObjective] " + objective.name
                        + " bypass reason=ranged_threat"
                        + " target=" + rangedThreat.getClass().getSimpleName()
                        + " targetPos=" + rangedThreat.pos);
            }
            return null;
        }

        if (hasThreatInEngagementZone(attackableThreats)) {
            return null;
        }

        return actLure(visibleThreats);
    }

    /**
     * Filters ordinary offense from the same current-turn facts used by actBeforeOffense().
     * There is no remembered "engage" or "fail-open" state.
     */
    ArrayList<Mob> offensiveThreats(ArrayList<Mob> attackableThreats) {
        if (objective == null
                || attackableThreats == null
                || attackableThreats.isEmpty()
                || firstCurrentRangedThreat(attackableThreats) != null) {
            return attackableThreats;
        }

        ArrayList<Mob> inZone = new ArrayList<>();
        for (Mob threat : attackableThreats) {
            if (isInEngagementZone(threat)) {
                inZone.add(threat);
            }
        }
        if (!inZone.isEmpty()) {
            return inZone;
        }

        // actLure() only falls through without spending a turn when movement cannot be arranged
        // and CoHero is already under direct attack. In that case, ordinary combat is safer than
        // preserving the sacrifice objective.
        if (owner.countCurrentAttackersAtCell(owner.pos, attackableThreats) > 0) {
            return attackableThreats;
        }

        return inZone;
    }

    String debugState() {
        return objective == null
                ? "objective=none"
                : "objective=" + objective.name;
    }

    private Mob firstCurrentRangedThreat(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive()) {
                continue;
            }

            if (Dungeon.hero != null
                    && Dungeon.hero.isAlive()
                    && Dungeon.level.distance(threat.pos, Dungeon.hero.pos) > 1
                    && threat.coHeroCanAttackFrom(threat.pos, Dungeon.hero)) {
                return threat;
            }

            if (Dungeon.level.distance(threat.pos, owner.pos) > 1
                    && threat.coHeroCanAttackFrom(threat.pos, owner)) {
                return threat;
            }
        }

        return null;
    }

    private boolean hasThreatInEngagementZone(ArrayList<Mob> threats) {
        for (Mob threat : threats) {
            if (isInEngagementZone(threat)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInEngagementZone(Mob threat) {
        return threat != null
                && threat.isAlive()
                && threat.pos >= 0
                && threat.pos < objective.engagementZone.length
                && objective.engagementZone[threat.pos];
    }

    private EngagementObjective resolveSacrificialFireObjective() {
        if (!(Dungeon.level instanceof RegularLevel)
                || Dungeon.hero == null
                || !Dungeon.hero.isAlive()) {
            return null;
        }

        Blob fire = Dungeon.level.blobs.get(SacrificialFire.class);
        if (fire == null
                || fire.volume <= 0
                || fire.cur == null
                || fire.cur.length != Dungeon.level.length()) {
            return null;
        }

        RegularLevel level = (RegularLevel) Dungeon.level;
        Room objectiveRoom = null;
        int anchorCell = -1;

        for (int cell = 0; cell < fire.cur.length; cell++) {
            if (fire.cur[cell] <= 0) {
                continue;
            }

            Room candidate = level.room(cell);
            if (candidate == null || !isRoomBoundsCell(candidate, Dungeon.hero.pos)) {
                continue;
            }

            objectiveRoom = candidate;
            anchorCell = cell;
            break;
        }

        if (objectiveRoom == null || anchorCell == -1) {
            return null;
        }

        boolean[] engagementZone = new boolean[level.length()];
        for (int fireCell = 0; fireCell < fire.cur.length; fireCell++) {
            if (fire.cur[fireCell] <= 0) {
                continue;
            }
            for (int offset : PathFinder.NEIGHBOURS9) {
                int cell = fireCell + offset;
                if (level.insideMap(cell) && isRoomBoundsCell(objectiveRoom, cell)) {
                    engagementZone[cell] = true;
                }
            }
        }

        return new EngagementObjective(
                "sacrificial_fire", anchorCell, engagementZone);
    }

    private Boolean actLure(ArrayList<Mob> visibleThreats) {
        owner.clearExplorationTarget();
        owner.allowAnyGuardMovement();

        int target = chooseStagingCell(visibleThreats);
        if (target == -1) {
            return failOpenOrHold(visibleThreats, "no_staging_cell");
        }

        if (target == owner.pos) {
            return failOpenOrHold(visibleThreats, "staged");
        }

        if (owner.rooted) {
            return failOpenOrHold(visibleThreats, "rooted");
        }

        boolean[] passable = owner.ordinarySafePassable(false);
        passable[owner.pos] = true;
        int step = Dungeon.findStep(owner, target, passable, owner.fieldOfView, true);
        if (step == -1 || !passable[step] || !owner.isMovementSafe(step)) {
            return failOpenOrHold(visibleThreats, "no_lure_path");
        }

        Char occupant = Actor.findChar(step);
        if (occupant != null && occupant != owner) {
            return failOpenOrHold(visibleThreats, "lure_path_blocked");
        }

        int oldPos = owner.pos;
        owner.clearNavigationPath();
        owner.setMovementDecision("combat_objective_" + objective.name, target);
        owner.move(step, true);
        if (owner.pos == oldPos) {
            return failOpenOrHold(visibleThreats, "lure_move_blocked");
        }

        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.finishMovementAnimation(oldPos);
    }

    private int chooseStagingCell(ArrayList<Mob> visibleThreats) {
        boolean[] passable = owner.ordinarySafePassable(false);
        passable[owner.pos] = true;
        PathFinder.buildDistanceMap(owner.pos, passable);

        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestThreatDistance = Integer.MIN_VALUE;
        int bestAnchorDistance = Integer.MAX_VALUE;
        int bestPathDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < objective.engagementZone.length; cell++) {
            if (!objective.engagementZone[cell]
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int attackers = visibleThreats.isEmpty()
                    ? 0
                    : owner.countCurrentAttackersAtCell(cell, visibleThreats);
            float incoming = visibleThreats.isEmpty()
                    ? 0f
                    : owner.estimatedIncomingDptAtCell(cell, visibleThreats);
            int threatDistance = visibleThreats.isEmpty()
                    ? 0
                    : owner.nearestThreatDistance(cell, visibleThreats);
            int anchorDistance = Dungeon.level.distance(cell, objective.anchorCell);
            int pathDistance = PathFinder.distance[cell];

            boolean better = best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && threatDistance > bestThreatDistance)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && threatDistance == bestThreatDistance
                        && anchorDistance < bestAnchorDistance)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && threatDistance == bestThreatDistance
                        && anchorDistance == bestAnchorDistance
                        && pathDistance < bestPathDistance);

            if (better) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
                bestThreatDistance = threatDistance;
                bestAnchorDistance = anchorDistance;
                bestPathDistance = pathDistance;
            }
        }

        return best;
    }

    private Boolean failOpenOrHold(ArrayList<Mob> visibleThreats, String reason) {
        if (!visibleThreats.isEmpty()
                && owner.countCurrentAttackersAtCell(owner.pos, visibleThreats) > 0) {
            if (owner.debugLogEnabled()) {
                owner.logDebug("[CoHeroObjective] " + objective.name
                        + " fail-open reason=" + reason
                        + " pos=" + owner.pos);
            }
            return null;
        }

        owner.setMovementDecision("combat_objective_hold_" + objective.name, owner.pos);
        if (owner.debugLogEnabled()) {
            owner.logDebug("[CoHeroObjective] " + objective.name
                    + " hold reason=" + reason
                    + " pos=" + owner.pos);
        }
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    private boolean isRoomBoundsCell(Room room, int cell) {
        if (room == null || !Dungeon.level.insideMap(cell)) {
            return false;
        }
        Point point = Dungeon.level.cellToPoint(cell);
        return point.x >= room.left
                && point.x <= room.right
                && point.y >= room.top
                && point.y <= room.bottom;
    }

    private static final class EngagementObjective {
        final String name;
        final int anchorCell;
        final boolean[] engagementZone;

        EngagementObjective(String name, int anchorCell, boolean[] engagementZone) {
            this.name = name;
            this.anchorCell = anchorCell;
            this.engagementZone = engagementZone;
        }
    }
}
