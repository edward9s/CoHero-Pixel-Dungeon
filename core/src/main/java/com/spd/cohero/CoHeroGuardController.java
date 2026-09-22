package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Point;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Owns the persistent CoHero room-guard state machine.
 *
 * A guard session is created from the live level geometry and remains active
 * until Hero leaves the original room. Ordinary exploration is never a state
 * transition from an active guard session.
 */
final class CoHeroGuardController {

    private final CoHeroAlly owner;
    private int guardTarget = -1;
    private GuardSession session;
    private MoveScope moveScope = MoveScope.ANY;

    CoHeroGuardController(CoHeroAlly owner) {
        this.owner = owner;
    }

    void beginTurn() {
        moveScope = MoveScope.ANY;
    }

    boolean isActive() {
        return session != null;
    }

    boolean isMovementRestricted() {
        return session != null && moveScope != MoveScope.ANY;
    }

    void allowAnyMovement() {
        moveScope = MoveScope.ANY;
    }

    String blockedMovementReason(int step) {
        if (session == null) {
            return null;
        }

        if (moveScope == MoveScope.HERO_ROOM
                && !isRoomInteriorCell(session.heroRoom, step)) {
            return "BLOCKED_HERO_ROOM";
        }

        if (moveScope == MoveScope.GUARD_DOMAIN
                && !areaContains(session.area, step)
                && !isRoomBoundsCell(session.heroRoom, step)) {
            return "BLOCKED_GUARD_DOMAIN";
        }

        return null;
    }

    void prepareMovementScope(Mob heroSupportThreat) {
        if (session == null) {
            return;
        }

        if (heroSupportThreat != null && isRoomBoundsCell(session.heroRoom, owner.pos)) {
            moveScope = MoveScope.HERO_ROOM;
            if (owner.movementDebugLogEnabled()) {
                owner.logMovementDebug("[CoHeroMove] SUPPORT_LOCK"
                        + " threat=" + heroSupportThreat.getClass().getSimpleName()
                        + " threatPos=" + heroSupportThreat.pos
                        + " " + owner.movementContext());
            }
        } else if (areaContains(session.area, owner.pos)
                || isRoomBoundsCell(session.heroRoom, owner.pos)) {
            moveScope = MoveScope.GUARD_DOMAIN;
        }
    }

    void prepareHeroSupportMovement() {
        guardTarget = -1;

        if (session == null) {
            moveScope = MoveScope.ANY;
        } else if (isRoomBoundsCell(session.heroRoom, owner.pos)) {
            moveScope = MoveScope.HERO_ROOM;
        } else if (areaContains(session.area, owner.pos)) {
            moveScope = MoveScope.GUARD_DOMAIN;
        } else {
            moveScope = MoveScope.ANY;
        }
    }

    void updateSession() {
        if (!(Dungeon.level instanceof RegularLevel)
                || Dungeon.hero == null
                || !Dungeon.hero.isAlive()) {
            leaveSession();
            return;
        }

        if (session != null) {
            if (isRoomBoundsCell(session.heroRoom, Dungeon.hero.pos)) {
                return;
            }
            leaveSession();
        }

        RegularLevel level = (RegularLevel) Dungeon.level;
        Room heroRoom = level.room(Dungeon.hero.pos);
        if (heroRoom == null || heroRoom.isEntrance() || heroRoom.isExit()) {
            return;
        }

        ArrayList<RoomExit> exits = runtimeRoomExits(level, heroRoom);
        if (exits.size() != 1) {
            return;
        }

        RoomExit heroExit = exits.get(0);
        Room outsideRoom = findOutsideRoom(level, heroRoom, heroExit);
        if (outsideRoom == null) {
            return;
        }

        boolean[] area = buildGuardArea(level, heroRoom, outsideRoom, heroExit);
        if (area == null) {
            return;
        }

        session = new GuardSession(heroRoom, outsideRoom, heroExit.cell, area);
        guardTarget = -1;
        owner.clearExplorationTarget();

        int areaCells = 0;
        for (boolean allowed : area) {
            if (allowed) {
                areaCells++;
            }
        }

        if (owner.movementDebugLogEnabled()) {
            owner.logMovementDebug("[CoHeroMove] GUARD_SESSION enter"
                    + " heroRoom=" + heroRoom.getClass().getSimpleName()
                    + " outsideRoom=" + outsideRoom.getClass().getSimpleName()
                    + " door=" + heroExit.cell
                    + " areaCells=" + areaCells
                    + " pos=" + owner.pos
                    + " hero=" + Dungeon.hero.pos);
        }
    }

    Boolean act() {
        if (session == null) {
            return null;
        }

        owner.clearExplorationTarget();

        if (areaContains(session.area, owner.pos)) {
            owner.clearDefensingPos();
            owner.clearNavigationPath();
            moveScope = MoveScope.GUARD_DOMAIN;

            if (!areaContains(session.area, guardTarget) || guardTarget == owner.pos) {
                guardTarget = chooseGuardRoamingTarget();
            }
            if (guardTarget == -1) {
                owner.setMovementDecision("guard_hold", owner.pos);
                if (owner.movementDebugLogEnabled()) {
                    owner.logMovementDebug("[CoHeroMove] GUARD hold " + owner.movementContext());
                }
                owner.spendActionTime(Actor.TICK);
                return true;
            }

            owner.setMovementDecision("guard_roam", guardTarget);
            return moveWithinGuardArea(guardTarget);
        }

        if (isRoomBoundsCell(session.heroRoom, owner.pos)) {
            guardTarget = nearestReachableGuardCell(session.area);
            if (guardTarget == -1) {
                owner.spendActionTime(Actor.TICK);
                return true;
            }

            moveScope = MoveScope.GUARD_DOMAIN;
            owner.setMovementDecision("guard_return_from_hero_room", guardTarget);
            return actTowardGuardTarget(guardTarget);
        }

        // A third room is support-Hero territory. Active guard never transitions to explore.
        guardTarget = -1;
        moveScope = MoveScope.ANY;
        owner.setMovementDecision("guard_external_support", Dungeon.hero.pos);
        return owner.followHeroDirectiveForGuard();
    }

    void clearDirective() {
        guardTarget = -1;
        owner.clearDefensingPos();
        owner.clearNavigationPath();
    }

    void restrictPassable(boolean[] safePassable) {
        if (session == null || moveScope == MoveScope.ANY) {
            return;
        }

        for (int cell = 0; cell < safePassable.length; cell++) {
            boolean allowed;
            if (moveScope == MoveScope.HERO_ROOM) {
                allowed = isRoomInteriorCell(session.heroRoom, cell);
            } else {
                allowed = areaContains(session.area, cell)
                        || isRoomBoundsCell(session.heroRoom, cell);
            }
            safePassable[cell] = safePassable[cell] && allowed;
        }
    }

    String debugState() {
        return "guard=" + (session != null)
                + " inGuardArea=" + (session != null && areaContains(session.area, owner.pos))
                + " inHeroRoom="
                + (session != null && isRoomBoundsCell(session.heroRoom, owner.pos))
                + " scope=" + moveScope;
    }

    private void leaveSession() {
        if (session != null) {
            if (owner.movementDebugLogEnabled()) {
                owner.logMovementDebug("[CoHeroMove] GUARD_SESSION exit"
                        + " heroRoom=" + session.heroRoom.getClass().getSimpleName()
                        + " pos=" + owner.pos
                        + " hero=" + (Dungeon.hero == null ? -1 : Dungeon.hero.pos));
            }
        }

        session = null;
        guardTarget = -1;
        owner.clearDefensingPos();
        owner.clearNavigationPath();
    }

    private ArrayList<RoomExit> runtimeRoomExits(RegularLevel level, Room room) {
        ArrayList<RoomExit> exits = new ArrayList<>();

        for (int x = room.left + 1; x < room.right; x++) {
            addRuntimeRoomExit(level, exits, x, room.top, 0, 1);
            addRuntimeRoomExit(level, exits, x, room.bottom, 0, -1);
        }
        for (int y = room.top + 1; y < room.bottom; y++) {
            addRuntimeRoomExit(level, exits, room.left, y, 1, 0);
            addRuntimeRoomExit(level, exits, room.right, y, -1, 0);
        }

        return exits;
    }

    private void addRuntimeRoomExit(
            RegularLevel level,
            ArrayList<RoomExit> exits,
            int x,
            int y,
            int inwardDx,
            int inwardDy) {
        Point boundary = new Point(x, y);
        int cell = level.pointToCell(boundary);
        Point inwardPoint = new Point(x + inwardDx, y + inwardDy);
        Point outwardPoint = new Point(x - inwardDx, y - inwardDy);

        if (inwardPoint.x < 0
                || inwardPoint.y < 0
                || outwardPoint.x < 0
                || outwardPoint.y < 0
                || inwardPoint.x >= level.width()
                || outwardPoint.x >= level.width()
                || inwardPoint.y >= level.height()
                || outwardPoint.y >= level.height()) {
            return;
        }

        int inwardCell = level.pointToCell(inwardPoint);
        int outwardCell = level.pointToCell(outwardPoint);
        if (!level.passable[cell]
                || !level.passable[inwardCell]
                || !level.passable[outwardCell]) {
            return;
        }

        exits.add(new RoomExit(cell, outwardCell));
    }

    private Room findOutsideRoom(RegularLevel level, Room heroRoom, RoomExit heroExit) {
        Room interiorMatch = null;
        Point outsidePoint = level.cellToPoint(heroExit.outsideCell);
        for (Room candidate : level.rooms()) {
            if (candidate == heroRoom) {
                continue;
            }
            if (candidate.inside(outsidePoint)) {
                if (interiorMatch != null) {
                    return null;
                }
                interiorMatch = candidate;
            }
        }
        if (interiorMatch != null) {
            return interiorMatch;
        }

        Room boundsMatch = null;
        for (Room candidate : level.rooms()) {
            if (candidate == heroRoom
                    || !isRoomBoundsCell(candidate, heroExit.outsideCell)) {
                continue;
            }
            if (boundsMatch != null) {
                return null;
            }
            boundsMatch = candidate;
        }
        return boundsMatch;
    }

    private boolean[] buildGuardArea(
            RegularLevel level,
            Room heroRoom,
            Room outsideRoom,
            RoomExit heroExit) {
        boolean[] roomPassable = new boolean[level.length()];
        for (int cell = 0; cell < roomPassable.length; cell++) {
            roomPassable[cell] = level.passable[cell]
                    && isRoomBoundsCell(outsideRoom, cell)
                    && !isRoomBoundsCell(heroRoom, cell);
        }

        int startCell = heroExit.outsideCell;
        if (!areaContains(roomPassable, startCell)) {
            return null;
        }

        boolean[] otherExit = new boolean[level.length()];
        for (RoomExit exit : runtimeRoomExits(level, outsideRoom)) {
            if (exit.cell != heroExit.cell
                    && exit.cell >= 0
                    && exit.cell < otherExit.length) {
                otherExit[exit.cell] = true;
            }
        }

        boolean[] area = new boolean[level.length()];
        int previous = heroExit.cell;
        int current = startCell;
        boolean any = false;

        // Follow the live cardinal floor topology from the Hero-room exit.
        // A straight/cornering one-cell corridor has exactly one forward continuation.
        // The first branch, open room, dead end, or another room exit ends the guard domain.
        while (areaContains(roomPassable, current) && !otherExit[current]) {
            area[current] = true;
            any = true;

            int next = -1;
            int forwardChoices = 0;
            for (int offset : PathFinder.NEIGHBOURS4) {
                int candidate = current + offset;
                if (!level.insideMap(candidate)
                        || candidate == previous
                        || !areaContains(roomPassable, candidate)
                        || area[candidate]) {
                    continue;
                }

                forwardChoices++;
                next = candidate;
                if (forwardChoices > 1) {
                    break;
                }
            }

            if (forwardChoices != 1) {
                break;
            }

            previous = current;
            current = next;
        }

        return any ? area : null;
    }

    private boolean actTowardGuardTarget(int target) {
        owner.defendPos(target);
        boolean result = owner.actCurrentState();
        owner.refreshOwnFieldOfView();

        if (owner.defendingPosition() == owner.pos && target != owner.pos) {
            guardTarget = -1;
        }
        return result;
    }

    private int nearestReachableGuardCell(boolean[] area) {
        boolean[] passable = owner.ordinarySafePassable(false);
        PathFinder.buildDistanceMap(owner.pos, passable);

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int cell = 0; cell < passable.length; cell++) {
            if (!areaContains(area, cell)
                    || !owner.isMovementSafe(cell)
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int distance = PathFinder.distance[cell];
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private int chooseGuardRoamingTarget() {
        boolean[] passable = guardAreaPassable();
        PathFinder.buildDistanceMap(owner.pos, passable);

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < passable.length; cell++) {
            if (cell == owner.pos
                    || !passable[cell]
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant == null || occupant == owner) {
                candidates.add(cell);
            }
        }
        return candidates.isEmpty() ? -1 : Random.element(candidates);
    }

    private boolean moveWithinGuardArea(int target) {
        if (owner.rooted) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        boolean[] passable = guardAreaPassable();
        int step = Dungeon.findStep(owner, target, passable, owner.fieldOfView, true);
        if (step == -1 || !passable[step] || !owner.isMovementSafe(step)) {
            if (owner.movementDebugLogEnabled()) {
                owner.logMovementDebug("[CoHeroMove] GUARD path_failed"
                        + " target=" + target
                        + " step=" + step
                        + " " + owner.movementContext());
            }
            guardTarget = -1;
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        int oldPos = owner.pos;
        owner.move(step, true);
        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.finishMovementAnimation(oldPos);
    }

    private boolean[] guardAreaPassable() {
        boolean[] passable = owner.ordinarySafePassable(false);
        for (int cell = 0; cell < passable.length; cell++) {
            passable[cell] = passable[cell] && areaContains(session.area, cell);
        }
        if (isRoomBoundsCell(session.heroRoom, owner.pos)) {
            passable[owner.pos] = true;
        }
        return passable;
    }

    private boolean areaContains(boolean[] area, int cell) {
        return area != null
                && cell >= 0
                && cell < area.length
                && area[cell];
    }

    private boolean isRoomInteriorCell(Room room, int cell) {
        return room != null
                && cell >= 0
                && cell < Dungeon.level.length()
                && room.inside(Dungeon.level.cellToPoint(cell));
    }

    private boolean isRoomBoundsCell(Room room, int cell) {
        if (room == null
                || cell < 0
                || cell >= Dungeon.level.length()) {
            return false;
        }

        Point point = Dungeon.level.cellToPoint(cell);
        return point.x >= room.left
                && point.x <= room.right
                && point.y >= room.top
                && point.y <= room.bottom;
    }

    private enum MoveScope {
        ANY,
        GUARD_DOMAIN,
        HERO_ROOM
    }

    private static final class RoomExit {
        final int cell;
        final int outsideCell;

        RoomExit(int cell, int outsideCell) {
            this.cell = cell;
            this.outsideCell = outsideCell;
        }
    }

    private static final class GuardSession {
        final Room heroRoom;
        final Room outsideRoom;
        final int heroDoorCell;
        final boolean[] area;

        GuardSession(Room heroRoom, Room outsideRoom, int heroDoorCell, boolean[] area) {
            this.heroRoom = heroRoom;
            this.outsideRoom = outsideRoom;
            this.heroDoorCell = heroDoorCell;
            this.area = area;
        }
    }
}
