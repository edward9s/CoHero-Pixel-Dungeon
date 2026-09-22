package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tracks visible CoHero presentation separately from gameplay timing.
 *
 * Gameplay remains fully serial on the SPD actor timeline. Visible CoHero animations may continue
 * while later actors process. The same CoHero never starts a second action presentation on top of
 * its previous one, and Hero input is released only after the current visible CoHero presentation
 * batch has finished.
 */
public final class CoHeroPresentation {

    private static int pending;
    private static final IdentityHashMap<Char, Integer> pendingByActor = new IdentityHashMap<>();
    private static final Set<Char> waitingActors =
            Collections.newSetFromMap(new IdentityHashMap<Char, Boolean>());

    private CoHeroPresentation() {
    }

    public static synchronized void reset() {
        pending = 0;
        pendingByActor.clear();
        waitingActors.clear();
    }

    public static synchronized void begin(Char actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Presentation actor is required");
        }
        pending++;
        Integer actorPending = pendingByActor.get(actor);
        pendingByActor.put(actor, actorPending == null ? 1 : actorPending + 1);
    }

    /**
     * Returns true when this actor must wait for its own previous presentation to finish.
     * The actor should return false from act() in that case.
     */
    public static synchronized boolean awaitActor(Char actor) {
        Integer actorPending = pendingByActor.get(actor);
        if (actorPending == null || actorPending <= 0) {
            return false;
        }
        waitingActors.add(actor);
        return true;
    }

    /**
     * Returns true when Hero input must wait for any visible CoHero presentation still in flight.
     * Hero should return false from act() without becoming ready in that case.
     */
    public static synchronized boolean awaitAll(Char hero) {
        if (pending <= 0) {
            return false;
        }
        waitingActors.add(hero);
        return true;
    }

    public static void complete(Char actor) {
        Char wakeActor = null;
        Char wakeHero = null;

        synchronized (CoHeroPresentation.class) {
            Integer actorPending = pendingByActor.get(actor);
            if (actorPending == null || actorPending <= 0) {
                throw new IllegalStateException("Completed an untracked CoHero presentation");
            }

            if (actorPending == 1) {
                pendingByActor.remove(actor);
                if (waitingActors.remove(actor)) {
                    wakeActor = actor;
                }
            } else {
                pendingByActor.put(actor, actorPending - 1);
            }

            pending--;
            if (pending < 0) {
                throw new IllegalStateException("Negative CoHero presentation count");
            }

            if (pending == 0 && Dungeon.hero != null && waitingActors.remove(Dungeon.hero)) {
                wakeHero = Dungeon.hero;
            }
        }

        // These are intentionally outside our lock. next() only clears Actor.current when the
        // corresponding actor is actually the one waiting on the actor thread.
        if (wakeActor != null) {
            wakeActor.next();
        }
        if (wakeHero != null && wakeHero != wakeActor) {
            wakeHero.next();
        }
    }

    public static synchronized int pendingCount() {
        return pending;
    }
}
