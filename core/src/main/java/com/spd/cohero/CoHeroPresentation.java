package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tracks visible CoHero presentation separately from gameplay timing.
 *
 * Gameplay remains fully serial on the SPD actor timeline. CoHero presentation never delays Hero
 * input. The only wait retained here is per-CoHero: the same sprite must not start a second action
 * presentation before its previous callback has completed.
 */
public final class CoHeroPresentation {

    private static final Set<Char> pendingActors =
            Collections.newSetFromMap(new IdentityHashMap<Char, Boolean>());
    private static final Set<Char> waitingActors =
            Collections.newSetFromMap(new IdentityHashMap<Char, Boolean>());

    private CoHeroPresentation() {
    }

    public static synchronized void reset() {
        pendingActors.clear();
        waitingActors.clear();
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

    public static synchronized void begin(Char actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Presentation actor is required");
        }
        if (!pendingActors.add(actor)) {
            throw new IllegalStateException(
                    "CoHero actor started a second presentation before the first completed");
        }
    }

    /**
     * Returns true when this actor must wait for its own previous presentation to finish.
     * Hero is never registered here.
     */
    public static synchronized boolean awaitActor(Char actor) {
        if (!pendingActors.contains(actor)) {
            return false;
        }
        waitingActors.add(actor);
        return true;
    }

    public static void complete(Char actor) {
        boolean wakeActor;

        synchronized (CoHeroPresentation.class) {
            if (!pendingActors.remove(actor)) {
                throw new IllegalStateException("Completed an untracked CoHero presentation");
            }
            wakeActor = waitingActors.remove(actor);
        }

        // next() only clears Actor.current when this actor is actually the one waiting.
        if (wakeActor) {
            actor.next();
        }
    }

    public static synchronized int pendingCount() {
        return pendingActors.size();
    }
}
