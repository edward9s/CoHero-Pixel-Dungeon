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
 * Owns temporary combat goals that change where CoHero wants a fight to happen.
 *
 * This controller does not replace ordinary combat. It can delay offense until a target reaches
 * an engagement zone and position CoHero to lure the target there. Hazard and survival handling
 * remain above this layer.
 */
final class CoHeroCombatObjectiveController {

    private final CoHeroAlly owner;
    private EngagementObjective objective;
    private boolean luring;
    private int stagingCell = -1;
    private boolean allowOutsideCombatThisTurn;

    CoHeroCombatObjectiveController(CoHeroAlly owner) {
        this.owner = owner;
    }

    void update() {
        allowOutsideCombatThisTurn = false;

        EngagementObjective resolved = resolveSacrificialFireObjective();
        if (resolved == null) {
            clearLure();
            objective = null;
            return;
        }

        if (objective == null
                || objective.anchorCell != resolved.anchorCell
                || objective.room != resolved.room) {
            clearLure();
        }
        objective = resolved;
    }

    boolean isActive() {
        return objective != null && luring;
    }

    /**
     * Called after survival actions but before ordinary offensive positioning/attacks.
     * Returns an action result when CoHero should keep luring instead of attacking.
     */
    Boolean actBeforeOffense(
            ArrayList<Mob> attackableThreats, ArrayList<Mob> visibleThreats) {
        if (objective == null) {
            return null;
        }

        for (Mob threat : attackableThreats) {
            if (objective.engagementZone[threat.pos]) {
                if (luring && owner.debugLogEnabled()) {
                    owner.logDebug("[CoHeroObjective] " + objective.name
                            + " engage target=" + threat.getClass().getSimpleName()
                            + " targetPos=" + threat.pos);
                }
                clearLure();
                return null;
            }
        }

        if (attackableThreats.isEmpty()) {
            return null;
        }

        if (!luring) {
            luring = true;
            stagingCell = -1;
            if (owner.debugLogEnabled()) {
                Mob trigger = attackableThreats.get(0);
                owner.logDebug("[CoHeroObjective] " + objective.name
                        + " lure_start target=" + trigger.getClass().getSimpleName()
                        + " targetPos=" + trigger.pos);
            }
        }

        return actLure(visibleThreats);
    }

    /**
     * Keeps CoHero staged inside the objective even before a visible enemy arrives.
     * This prevents ordinary guard behavior from placing CoHero at the doorway first.
     */
    Boolean actWithoutVisibleThreats() {
        if (objective == null || !luring) {
            return null;
        }
        return actLure(new ArrayList<>());
    }

    /**
     * Once an objective is active, ordinary offense only considers enemies already inside its
     * engagement zone. If lure positioning is impossible while CoHero is under immediate attack,
     * fail open for this turn so the AI cannot deadlock and die at the boundary.
     */
    ArrayList<Mob> offensiveThreats(ArrayList<Mob> attackableThreats) {
        if (objective == null || !luring || allowOutsideCombatThisTurn) {
            return attackableThreats;
        }

        ArrayList<Mob> result = new ArrayList<>();
        for (Mob threat : attackableThreats) {
            if (objective.engagementZone[threat.pos]) {
                result.add(threat);
            }
        }
        return result;
    }

    String debugState() {
        if (objective == null) {
            return "objective=none";
        }
        return "objective=" + objective.name
                + " state=" + (luring ? "luring" : "idle");
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
                "sacrificial_fire", objectiveRoom, anchorCell, engagementZone);
    }

    private Boolean actLure(ArrayList<Mob> visibleThreats) {
        owner.clearExplorationTarget();
        owner.clearCombatPositioningAfterRelocation();
        owner.allowAnyGuardMovement();

        if (!isUsableStagingCell(stagingCell)) {
            stagingCell = chooseStagingCell(visibleThreats);
        }

        if (stagingCell == -1) {
            return failOpenOrHold(visibleThreats, "no_staging_cell");
        }

        if (stagingCell == owner.pos) {
            return failOpenOrHold(visibleThreats, "staged");
        }

        if (owner.rooted) {
            return failOpenOrHold(visibleThreats, "rooted");
        }

        boolean[] passable = owner.ordinarySafePassable(false);
        passable[owner.pos] = true;
        int step = Dungeon.findStep(owner, stagingCell, passable, owner.fieldOfView, true);
        if (step == -1 || !passable[step] || !owner.isMovementSafe(step)) {
            stagingCell = chooseStagingCell(visibleThreats);
            if (stagingCell == -1 || stagingCell == owner.pos) {
                return failOpenOrHold(visibleThreats, "no_lure_path");
            }
            step = Dungeon.findStep(owner, stagingCell, passable, owner.fieldOfView, true);
        }

        if (step == -1 || !passable[step] || !owner.isMovementSafe(step)) {
            return failOpenOrHold(visibleThreats, "no_lure_path");
        }

        Char occupant = Actor.findChar(step);
        if (occupant != null && occupant != owner) {
            return failOpenOrHold(visibleThreats, "lure_path_blocked");
        }

        int oldPos = owner.pos;
        owner.clearNavigationPath();
        owner.setMovementDecision("combat_objective_" + objective.name, stagingCell);
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

    private boolean isUsableStagingCell(int cell) {
        if (objective == null
                || cell < 0
                || cell >= objective.engagementZone.length
                || !objective.engagementZone[cell]
                || !Dungeon.level.passable[cell]
                || !owner.isMovementSafe(cell)) {
            return false;
        }

        Char occupant = Actor.findChar(cell);
        return occupant == null || occupant == owner;
    }

    private void clearLure() {
        luring = false;
        stagingCell = -1;
        allowOutsideCombatThisTurn = false;
    }

    private Boolean failOpenOrHold(ArrayList<Mob> visibleThreats, String reason) {
        if (!visibleThreats.isEmpty()
                && owner.countCurrentAttackersAtCell(owner.pos, visibleThreats) > 0) {
            allowOutsideCombatThisTurn = true;
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
        final Room room;
        final int anchorCell;
        final boolean[] engagementZone;

        EngagementObjective(
                String name, Room room, int anchorCell, boolean[] engagementZone) {
            this.name = name;
            this.room = room;
            this.anchorCell = anchorCell;
            this.engagementZone = engagementZone;
        }
    }
}
