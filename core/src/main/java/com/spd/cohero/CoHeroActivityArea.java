package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
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
            return null;
        }

        int[] previousDistance = PathFinder.distance == null
                ? null
                : PathFinder.distance.clone();
        try {
            return isExitKnown()
                    ? nearestPercentArea(true, EXPLORED_ROAMING_PERCENT)
                    : nearestPercentArea(false, PRE_EXIT_EXPLORATION_PERCENT);
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
    }

    public static boolean containsClericCooperationArea(int cell) {
        boolean[] area = clericCooperationArea();
        return area != null
                && cell >= 0
                && cell < area.length
                && area[cell];
    }

    static boolean containsExploredRoamingArea(int cell) {
        boolean[] area = exploredRoamingArea();
        return area != null
                && cell >= 0
                && cell < area.length
                && area[cell];
    }

    private static boolean[] nearestPercentArea(boolean exploredOnly, int percent) {
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
        return allowed;
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
