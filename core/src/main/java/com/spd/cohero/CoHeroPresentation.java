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
 * while later actors process. A background presentation never delays Hero input; a foreground
 * presentation may delay Hero input until the slowest foreground presentation in the batch ends.
 * The same CoHero never starts a second presentation on top of its previous one.
 */
public final class CoHeroPresentation {

    private static int pending;
    private static int foregroundPending;

    // A CoHero may have at most one in-flight presentation. The boolean records whether that
    // presentation is foreground and therefore allowed to delay Hero input.
    private static final IdentityHashMap<Char, Boolean> pendingByActor = new IdentityHashMap<>();
    private static final Set<Char> waitingActors =
            Collections.newSetFromMap(new IdentityHashMap<Char, Boolean>());

    private CoHeroPresentation() {
    }

    public static synchronized void reset() {
        pending = 0;
        foregroundPending = 0;
        pendingByActor.clear();
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

    /**
     * Foreground means the player Hero can directly see at least one endpoint of the action.
     * CoHero-only visibility is still presented, but is background and never delays Hero input.
     */
    public static boolean isForeground(int... cells) {
        if (cells == null || cells.length == 0) {
            return false;
        }
        for (int cell : cells) {
            if (CoHero.heroCanSee(cell)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void begin(Char actor, boolean foreground) {
        if (actor == null) {
            throw new IllegalArgumentException("Presentation actor is required");
        }
        if (pendingByActor.containsKey(actor)) {
            throw new IllegalStateException(
                    "CoHero actor started a second presentation before the first completed");
        }

        pendingByActor.put(actor, foreground);
        pending++;
        if (foreground) {
            foregroundPending++;
        }
    }

    /**
     * Returns true when this actor must wait for its own previous presentation to finish.
     * Background presentations still obey this rule so a sprite callback is never overwritten.
     */
    public static synchronized boolean awaitActor(Char actor) {
        if (!pendingByActor.containsKey(actor)) {
            return false;
        }
        waitingActors.add(actor);
        return true;
    }

    /**
     * Returns true only when player input must wait for a foreground CoHero presentation.
     */
    public static synchronized boolean awaitForeground(Char hero) {
        if (foregroundPending <= 0) {
            return false;
        }
        waitingActors.add(hero);
        return true;
    }

    public static void complete(Char actor) {
        Char wakeActor = null;
        Char wakeHero = null;

        synchronized (CoHeroPresentation.class) {
            Boolean foreground = pendingByActor.remove(actor);
            if (foreground == null) {
                throw new IllegalStateException("Completed an untracked CoHero presentation");
            }

            pending--;
            if (pending < 0) {
                throw new IllegalStateException("Negative CoHero presentation count");
            }

            if (foreground) {
                foregroundPending--;
                if (foregroundPending < 0) {
                    throw new IllegalStateException("Negative foreground CoHero presentation count");
                }
            }

            if (waitingActors.remove(actor)) {
                wakeActor = actor;
            }

            if (foregroundPending == 0
                    && Dungeon.hero != null
                    && waitingActors.remove(Dungeon.hero)) {
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

    public static synchronized int foregroundPendingCount() {
        return foregroundPending;
    }
}
