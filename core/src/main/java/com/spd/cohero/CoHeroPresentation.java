package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import java.util.IdentityHashMap;

/**
 * Tracks best-effort presentation created by the CoHero feature.
 *
 * Gameplay never waits on this tracker. Each actor owns at most one current presentation token.
 * A newer high-priority presentation may replace an older cosmetic token; callbacks carrying an
 * obsolete token are ignored when they eventually arrive.
 */
public final class CoHeroPresentation {

    public static final long NONE = 0L;

    private static final IdentityHashMap<Char, Long> pendingByActor = new IdentityHashMap<>();
    private static long nextToken = 1L;

    private CoHeroPresentation() {
    }

    public static synchronized void reset() {
        pendingByActor.clear();
        nextToken = 1L;
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
     * Starts a presentation only when this actor has no cosmetic already in flight.
     */
    public static synchronized long tryBegin(Char actor) {
        requireActor(actor);
        if (pendingByActor.containsKey(actor)) {
            return NONE;
        }
        return replaceInternal(actor);
    }

    /**
     * Gives a newer presentation priority over any older cosmetic for this actor.
     * Stale callbacks remain safe because completion is token-scoped.
     */
    public static synchronized long replace(Char actor) {
        requireActor(actor);
        return replaceInternal(actor);
    }

    public static synchronized void cancel(Char actor) {
        if (actor != null) {
            pendingByActor.remove(actor);
        }
    }

    public static synchronized boolean isPending(Char actor) {
        return actor != null && pendingByActor.containsKey(actor);
    }

    /**
     * Completes only the matching generation. Obsolete callbacks are intentionally ignored.
     */
    public static synchronized boolean complete(Char actor, long token) {
        if (actor == null || token == NONE) {
            return false;
        }
        Long current = pendingByActor.get(actor);
        if (current == null || current.longValue() != token) {
            return false;
        }
        pendingByActor.remove(actor);
        return true;
    }

    public static synchronized int pendingCount() {
        return pendingByActor.size();
    }

    private static long replaceInternal(Char actor) {
        long token = nextToken++;
        if (token == NONE) {
            token = nextToken++;
        }
        pendingByActor.put(actor, token);
        return token;
    }

    private static void requireActor(Char actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Presentation actor is required");
        }
    }
}
