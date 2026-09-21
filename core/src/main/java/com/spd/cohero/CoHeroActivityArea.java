package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

/**
 * Shared Hero-centered percentage-area calculations used by CoHero.
 *
 * Before the exit is known, exploration uses the nearest 30% of reachable
 * discoverable cells. The idle explored-roaming rule uses the nearest 25% of
 * reachable explored/mapped cells. Cleric cooperation reuses those same area
 * definitions: 30% before the exit is known, 25% afterwards.
 */
public final class CoHeroActivityArea {

    static final int PRE_EXIT_EXPLORATION_PERCENT = 30;
    static final int EXPLORED_ROAMING_PERCENT = 25;

    private static Level cachedLevel;
    private static int cachedHeroPos = -1;
    private static boolean cachedExitKnown;
    private static float cachedActorTime = Float.NaN;
    private static int cachedStateSignature;
    private static boolean[] cachedClericArea;
    private static int[] cachedClericDistance;
    private static int cachedClericMaxDistance = -1;

    private CoHeroActivityArea() {
    }

    static boolean[] preExitExplorationArea() {
        if (!hasLiveHero() || isExitKnown()) {
            return null;
        }
        return nearestPercentArea(false, PRE_EXIT_EXPLORATION_PERCENT);
    }

    static boolean[] exploredRoamingArea() {
        if (!hasLiveHero()) {
            return null;
        }
        return nearestPercentArea(true, EXPLORED_ROAMING_PERCENT);
    }

    /**
     * Returns the activity area currently presented to the player.
     * This method preserves PathFinder.distance because it is also called from
     * combat stat calculation and rendering, where changing shared pathfinder
     * scratch state would be surprising.
     */
    public static boolean[] clericCooperationArea() {
        if (!hasLiveHero()) {
            clearClericCache();
            return null;
        }

        Level level = Dungeon.level;
        int heroPos = Dungeon.hero.pos;
        boolean exitKnown = isExitKnown();
        float actorTime = Actor.now();

        if (level == cachedLevel
                && heroPos == cachedHeroPos
                && exitKnown == cachedExitKnown
                && Float.compare(actorTime, cachedActorTime) == 0) {
            return cachedClericArea;
        }

        int signature = stateSignature(exitKnown);
        cachedActorTime = actorTime;

        if (level == cachedLevel
                && heroPos == cachedHeroPos
                && exitKnown == cachedExitKnown
                && signature == cachedStateSignature) {
            return cachedClericArea;
        }

        int[] previousDistance = PathFinder.distance == null
                ? null
                : PathFinder.distance.clone();
        PercentArea result;
        try {
            result = nearestPercentAreaResult(
                    exitKnown,
                    exitKnown ? EXPLORED_ROAMING_PERCENT : PRE_EXIT_EXPLORATION_PERCENT);
        } finally {
            if (previousDistance != null
                    && PathFinder.distance != null
                    && previousDistance.length == PathFinder.distance.length) {
                System.arraycopy(
                        previousDistance,
                        0,
                        PathFinder.distance,
                        0,
                        previousDistance.length);
            }
        }

        cachedLevel = level;
        cachedHeroPos = heroPos;
        cachedExitKnown = exitKnown;
        cachedStateSignature = signature;
        cachedClericArea = result == null ? null : result.cells;
        cachedClericDistance = result == null ? null : result.distance;
        cachedClericMaxDistance = result == null ? -1 : result.maxDistance;
        return cachedClericArea;
    }

    public static boolean containsClericCooperationArea(int cell) {
        boolean[] area = clericCooperationArea();
        return area != null
                && cell >= 0
                && cell < area.length
                && area[cell];
    }

    public static boolean isClericBoundaryCell(int cell) {
        clericCooperationArea();
        return cachedClericArea != null
                && cachedClericDistance != null
                && cell >= 0
                && cell < cachedClericArea.length
                && cachedClericArea[cell]
                && cachedClericDistance[cell] == cachedClericMaxDistance;
    }

    public static boolean isBeyondClericBoundary(int cell) {
        clericCooperationArea();
        return cachedClericDistance != null
                && cell >= 0
                && cell < cachedClericDistance.length
                && Dungeon.level.passable[cell]
                && cachedClericDistance[cell] != Integer.MAX_VALUE
                && cachedClericDistance[cell] > cachedClericMaxDistance;
    }

    static boolean containsExploredRoamingArea(int cell) {
        boolean[] area = exploredRoamingArea();
        return area != null
                && cell >= 0
                && cell < area.length
                && area[cell];
    }

    private static boolean[] nearestPercentArea(boolean exploredOnly, int percent) {
        PercentArea result = nearestPercentAreaResult(exploredOnly, percent);
        return result == null ? null : result.cells;
    }

    private static PercentArea nearestPercentAreaResult(boolean exploredOnly, int percent) {
        Level level = Dungeon.level;
        PathFinder.buildDistanceMap(Dungeon.hero.pos, level.passable);

        ArrayList<Integer> reachable = new ArrayList<>();
        for (int cell = 0; cell < level.length(); cell++) {
            if (!level.passable[cell]
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            if (exploredOnly) {
                if (!level.visited[cell] && !level.mapped[cell]) {
                    continue;
                }
            } else if (!level.discoverable[cell]) {
                continue;
            }

            reachable.add(cell);
        }

        if (reachable.isEmpty()) {
            return null;
        }

        reachable.sort((a, b) -> Integer.compare(
                PathFinder.distance[a],
                PathFinder.distance[b]));

        int areaSize = Math.max(
                1,
                (reachable.size() * percent + 99) / 100);
        int maxDistance = PathFinder.distance[reachable.get(areaSize - 1)];

        boolean[] allowed = new boolean[level.length()];
        for (int cell : reachable) {
            if (PathFinder.distance[cell] > maxDistance) {
                break;
            }
            allowed[cell] = true;
        }

        return new PercentArea(
                allowed,
                PathFinder.distance.clone(),
                maxDistance);
    }

    private static final class PercentArea {
        final boolean[] cells;
        final int[] distance;
        final int maxDistance;

        PercentArea(boolean[] cells, int[] distance, int maxDistance) {
            this.cells = cells;
            this.distance = distance;
            this.maxDistance = maxDistance;
        }
    }

    private static int stateSignature(boolean exitKnown) {
        int hash = 1;
        Level level = Dungeon.level;
        for (int cell = 0; cell < level.length(); cell++) {
            int bits = level.passable[cell] ? 1 : 0;
            if (exitKnown) {
                if (level.visited[cell]) {
                    bits |= 2;
                }
                if (level.mapped[cell]) {
                    bits |= 4;
                }
            } else if (level.discoverable[cell]) {
                bits |= 2;
            }
            hash = 31 * hash + bits;
        }
        return hash;
    }

    private static void clearClericCache() {
        cachedLevel = null;
        cachedHeroPos = -1;
        cachedExitKnown = false;
        cachedActorTime = Float.NaN;
        cachedStateSignature = 0;
        cachedClericArea = null;
        cachedClericDistance = null;
        cachedClericMaxDistance = -1;
    }

    private static boolean hasLiveHero() {
        return Dungeon.hero != null
                && Dungeon.hero.isAlive()
                && Dungeon.level != null
                && Dungeon.hero.pos >= 0
                && Dungeon.hero.pos < Dungeon.level.length();
    }

    private static boolean isExitKnown() {
        int exit = Dungeon.level.exit();
        return exit >= 0
                && exit < Dungeon.level.length()
                && (Dungeon.level.visited[exit] || Dungeon.level.mapped[exit]);
    }
}
