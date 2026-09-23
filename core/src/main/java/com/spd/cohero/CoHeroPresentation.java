package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tracks CoHero-feature presentation separately from gameplay timing.
 *
 * Presentation is strictly best-effort and must never suspend the SPD actor thread. This tracker
 * is shared by the companion and remote enemies whose actions are visible only through CoHero FOV.
 * At most one cosmetic presentation per actor may be in flight; gameplay always wins.
 */
public final class CoHeroPresentation {

    private static final Set<Char> pendingActors =
            Collections.newSetFromMap(new IdentityHashMap<Char, Boolean>());

    private CoHeroPresentation() {
    }

    public static synchronized void reset() {
        pendingActors.clear();
    }

    public static boolean shouldShow(int... cells) {
        if (cells == null || cells.length == 0) {
            return false;
        }
        for (int cell : cells) {
            if (CoHero.isVisibleToPlayer(cell)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to reserve this actor's presentation slot without ever blocking gameplay.
     */
    public static synchronized boolean tryBegin(Char actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Presentation actor is required");
        }
        return pendingActors.add(actor);
    }

    public static synchronized boolean isPending(Char actor) {
        return pendingActors.contains(actor);
    }

    public static synchronized void complete(Char actor) {
        if (!pendingActors.remove(actor)) {
            throw new IllegalStateException("Completed an untracked CoHero presentation");
        }
    }

    public static synchronized int pendingCount() {
        return pendingActors.size();
    }
}
