package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.watabou.utils.FileUtils;

/** A bounded, in-memory report of companion action timings. */
final class CoHeroTimings {

    enum Action {
        ATTACK("attack"),
        ATTACK_KILL("attack_kill"),
        ATTACK_ANIMATION("attack_animation"),
        PICKUP_GOLD("pickup_gold"),
        PICKUP_ITEM("pickup_item"),
        PICKUP_FAILED("pickup_failed"),
        LOOT_SEARCH("loot_search");

        final String label;

        Action(String label) {
            this.label = label;
        }
    }

    private static final int HISTORY_SIZE = 64;
    private static final long SLOW_SEARCH_NANOS = 10_000_000L;

    private final String[] history = new String[HISTORY_SIZE];
    private final int[] counts = new int[Action.values().length];
    private final long[] totals = new long[Action.values().length];
    private final long[] maxima = new long[Action.values().length];
    private int next;
    private int size;
    private boolean dirty;

    synchronized void record(CoHeroAlly owner, Action action, long started) {
        long elapsed = Math.max(0L, System.nanoTime() - started);
        int index = action.ordinal();
        counts[index]++;
        totals[index] += elapsed;
        maxima[index] = Math.max(maxima[index], elapsed);
        dirty = true;

        if (action == Action.LOOT_SEARCH && elapsed < SLOW_SEARCH_NANOS) {
            return;
        }

        int heroPos = Dungeon.hero == null ? -1 : Dungeon.hero.pos;
        boolean visible = Dungeon.level != null
                && Dungeon.level.heroFOV != null
                && owner.pos >= 0
                && owner.pos < Dungeon.level.length()
                && Dungeon.level.heroFOV[owner.pos];
        history[next] = "depth=" + Dungeon.depth + " branch=" + Dungeon.branch
                + " t=" + (int) Actor.now() + " " + action.label
                + " " + milliseconds(elapsed) + "ms"
                + " co=" + owner.pos + " hero=" + heroPos
                + (visible ? " visible" : " outside");
        next = (next + 1) % HISTORY_SIZE;
        size = Math.min(size + 1, HISTORY_SIZE);
    }

    synchronized String report() {
        StringBuilder result = new StringBuilder("Count / average / maximum (ms)\n");
        for (Action action : Action.values()) {
            int index = action.ordinal();
            if (counts[index] > 0) {
                result.append(action.label).append(": ").append(counts[index])
                        .append(" / ").append(milliseconds(totals[index] / counts[index]))
                        .append(" / ").append(milliseconds(maxima[index])).append('\n');
            }
        }
        if (size == 0) {
            return result.append("\nNo detailed events yet.").toString();
        }

        result.append("\nRecent events (newest first)\n");
        for (int i = 0; i < size; i++) {
            int index = (next - 1 - i + HISTORY_SIZE) % HISTORY_SIZE;
            result.append(history[index]).append('\n');
        }
        return result.toString();
    }

    synchronized void saveReport() {
        if (!dirty) {
            return;
        }
        String path = GamesInProgress.gameFolder(GamesInProgress.curSlot)
                + "/cohero-timings.txt";
        FileUtils.getFileHandle(path).writeString(report(), false, "UTF-8");
        dirty = false;
    }

    private static String milliseconds(long nanoseconds) {
        long tenths = nanoseconds / 100_000L;
        return (tenths / 10) + "." + (tenths % 10);
    }
}
