package com.spd.cohero;

import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.watabou.utils.FileUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/** A bounded, in-memory report of companion action timings. */
final class CoHeroTimings {

    enum Action {
        ACT("act"),
        FRAME_INTERVAL("frame_interval"),
        REMOTE_VIEW("remote_view"),
        PREPARE("prepare"),
        COMBAT("combat"),
        COMBAT_SETUP("combat_setup"),
        COMBAT_SETUP_FILTER("combat_setup_filter"),
        COMBAT_SETUP_INVULNERABLE("combat_setup_invulnerable"),
        COMBAT_SETUP_CHARM("combat_setup_charm"),
        COMBAT_SETUP_TARGET("combat_setup_target"),
        COMBAT_SETUP_SURVIVAL_TARGET("combat_setup_survival_target"),
        COMBAT_RISK("combat_risk"),
        COMBAT_SURVIVAL("combat_survival"),
        COMBAT_OBJECTIVE("combat_objective"),
        COMBAT_TACTICS("combat_tactics"),
        COMBAT_RANGED("combat_ranged"),
        COMBAT_ACTION("combat_action"),
        COMBAT_ESCAPE("combat_escape"),
        MELEE_POSITIONING("melee_positioning"),
        ENCIRCLEMENT_SEARCH("encirclement_search"),
        ENCIRCLEMENT_DISTANCE_MAP("encirclement_distance_map"),
        ENCIRCLEMENT_FILTER("encirclement_filter"),
        ENCIRCLEMENT_TOPOLOGY_BUILD("encirclement_topology_build"),
        ENCIRCLEMENT_DYNAMIC("encirclement_dynamic"),
        ENCIRCLEMENT_ESCAPE("encirclement_escape"),
        SUPPORT("support"),
        RECOVERY("recovery"),
        RECOVERY_VALIDATE("recovery_validate"),
        RECOVERY_MOVE("recovery_move"),
        RECOVERY_ANIMATION("recovery_animation"),
        MOVE_GUARD_CHECK("move_guard_check"),
        MOVE_HAZARD_CHECK("move_hazard_check"),
        MOVE_SLEEP_CHECK("move_sleep_check"),
        MOVE_STOCK_PATH("move_stock_path"),
        MOVE_SAFE_MASK("move_safe_mask"),
        MOVE_POLICY_PATH("move_policy_path"),
        MOVE_EXECUTE("move_execute"),
        HAZARD_ESCAPE("hazard_escape"),
        GUARD("guard"),
        EXPLORE("explore"),
        EXPLORE_VALIDATE("explore_validate"),
        EXPLORE_SELECT("explore_select"),
        EXPLORE_MOVE("explore_move"),
        TTK_TOTAL("ttk_total"),
        TTK_MELEE("ttk_melee"),
        TTK_RANGED("ttk_ranged"),
        TTK_MISSILE("ttk_missile"),
        TTK_SPIRIT_BOW("ttk_spirit_bow"),
        TTK_WAND("ttk_wand"),
        TTK_WAND_WARDING("ttk_wand_warding"),
        TTK_WAND_CORROSION("ttk_wand_corrosion"),
        TTK_WAND_FIREBLAST("ttk_wand_fireblast"),
        TTK_WAND_BLAST_WAVE("ttk_wand_blast_wave"),
        TTK_WAND_LIGHTNING("ttk_wand_lightning"),
        TTK_WAND_DISINTEGRATION("ttk_wand_disintegration"),
        TTK_WAND_OTHER("ttk_wand_other"),
        VISION("vision"),
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
    private static final long SLOW_PHASE_NANOS = 10_000_000L;
    private static final long SLOW_FRAME_NANOS = 50_000_000L;
    private static final Method ANDROID_GC_STAT = androidGcStat();

    private final String[] history = new String[HISTORY_SIZE];
    private final int[] counts = new int[Action.values().length];
    private final long[] totals = new long[Action.values().length];
    private final long[] maxima = new long[Action.values().length];
    private int next;
    private int size;
    private boolean dirty;
    private volatile boolean enabled;
    private long lastFrameStarted;
    private long peakFrameSinceStep;
    private int lastHeroPos = -1;
    private long lastGcCount = -1;
    private long lastGcTime = -1;
    private long lastBlockingGcCount = -1;
    private long lastBlockingGcTime = -1;
    private long lastBytesAllocated = -1;

    synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        Arrays.fill(history, null);
        Arrays.fill(counts, 0);
        Arrays.fill(totals, 0L);
        Arrays.fill(maxima, 0L);
        next = 0;
        size = 0;
        dirty = false;
        sceneStarted();
    }

    boolean isEnabled() {
        return enabled;
    }

    synchronized void sceneStarted() {
        lastFrameStarted = 0L;
        peakFrameSinceStep = 0L;
        lastHeroPos = -1;
        lastGcCount = -1L;
        lastGcTime = -1L;
        lastBlockingGcCount = -1L;
        lastBlockingGcTime = -1L;
        lastBytesAllocated = -1L;
    }

    // Called on the render thread. No allocations or platform queries on ordinary frames.
    synchronized void frameStarted(int heroPos) {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameStarted != 0L) {
            long elapsed = Math.max(0L, now - lastFrameStarted);
            accumulate(Action.FRAME_INTERVAL, elapsed);
            peakFrameSinceStep = Math.max(peakFrameSinceStep, elapsed);
            if (elapsed >= SLOW_FRAME_NANOS) {
                appendHistory("depth=" + Dungeon.depth + " t=" + (int) Actor.now()
                        + " frame_interval " + milliseconds(elapsed) + "ms hero=" + heroPos);
            }
        }
        lastFrameStarted = now;

        if (lastHeroPos != heroPos) {
            recordHeroStep(heroPos);
            lastHeroPos = heroPos;
            peakFrameSinceStep = 0L;
        }
    }

    synchronized void remoteViewUpdated(long started) {
        if (!enabled) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - started);
        accumulate(Action.REMOTE_VIEW, elapsed);
        if (elapsed >= SLOW_PHASE_NANOS) {
            appendHistory("depth=" + Dungeon.depth + " t=" + (int) Actor.now()
                    + " remote_view " + milliseconds(elapsed) + "ms hero="
                    + (Dungeon.hero == null ? -1 : Dungeon.hero.pos));
        }
    }

    private void recordHeroStep(int heroPos) {
        if (ANDROID_GC_STAT == null) {
            return;
        }
        long gcCount = androidStat("art.gc.gc-count");
        long gcTime = androidStat("art.gc.gc-time");
        long blockingGcCount = androidStat("art.gc.blocking-gc-count");
        long blockingGcTime = androidStat("art.gc.blocking-gc-time");
        long allocated = androidStat("art.gc.bytes-allocated");
        if (lastGcCount >= 0) {
            appendHistory("depth=" + Dungeon.depth + " t=" + (int) Actor.now()
                    + (lastHeroPos == heroPos
                        ? " save_snapshot pos=" + heroPos
                        : " hero_step from=" + lastHeroPos + " to=" + heroPos)
                    + " frame_peak=" + milliseconds(peakFrameSinceStep) + "ms"
                    + " gc_count=" + (gcCount - lastGcCount)
                    + " gc_time=" + (gcTime - lastGcTime) + "ms"
                    + " blocking_gc_count=" + (blockingGcCount - lastBlockingGcCount)
                    + " blocking_gc_time=" + (blockingGcTime - lastBlockingGcTime) + "ms"
                    + " allocated=" + (allocated - lastBytesAllocated) + "B");
        }
        lastGcCount = gcCount;
        lastGcTime = gcTime;
        lastBlockingGcCount = blockingGcCount;
        lastBlockingGcTime = blockingGcTime;
        lastBytesAllocated = allocated;
    }

    private static Method androidGcStat() {
        try {
            Class<?> androidVersion = Class.forName("android.os.Build$VERSION");
            if (androidVersion.getField("SDK_INT").getInt(null) < 23) {
                return null;
            }
            return Class.forName("android.os.Debug").getMethod("getRuntimeStat", String.class);
        } catch (ClassNotFoundException desktop) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Android GC statistics are unavailable", error);
        }
    }

    private static long androidStat(String key) {
        try {
            String value = (String) ANDROID_GC_STAT.invoke(null, key);
            if (value == null) {
                throw new IllegalStateException("Missing Android GC statistic: " + key);
            }
            return Long.parseLong(value);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Could not read Android GC statistic: " + key, error);
        }
    }

    private void accumulate(Action action, long elapsed) {
        int index = action.ordinal();
        counts[index]++;
        totals[index] += elapsed;
        maxima[index] = Math.max(maxima[index], elapsed);
        dirty = true;
    }

    private void appendHistory(String event) {
        history[next] = event;
        next = (next + 1) % HISTORY_SIZE;
        size = Math.min(size + 1, HISTORY_SIZE);
    }

    synchronized void record(CoHeroAlly owner, Action action, long started) {
        record(owner, action, started, null);
    }

    synchronized void record(CoHeroAlly owner, Action action, long started, String detail) {
        if (!enabled) {
            return;
        }
        recordElapsedLocked(
                owner, action, Math.max(0L, System.nanoTime() - started), detail);
    }

    synchronized void recordElapsed(CoHeroAlly owner, Action action, long elapsed) {
        if (!enabled) {
            return;
        }
        recordElapsedLocked(owner, action, Math.max(0L, elapsed), null);
    }

    private void recordElapsedLocked(
            CoHeroAlly owner, Action action, long elapsed, String detail) {
        accumulate(action, elapsed);

        if ((action == Action.LOOT_SEARCH
                || action == Action.PREPARE
                || action == Action.COMBAT
                || action == Action.COMBAT_SETUP
                || action == Action.COMBAT_SETUP_FILTER
                || action == Action.COMBAT_SETUP_INVULNERABLE
                || action == Action.COMBAT_SETUP_CHARM
                || action == Action.COMBAT_SETUP_TARGET
                || action == Action.COMBAT_SETUP_SURVIVAL_TARGET
                || action == Action.COMBAT_RISK
                || action == Action.COMBAT_SURVIVAL
                || action == Action.COMBAT_OBJECTIVE
                || action == Action.COMBAT_TACTICS
                || action == Action.COMBAT_RANGED
                || action == Action.COMBAT_ACTION
                || action == Action.COMBAT_ESCAPE
                || action == Action.MELEE_POSITIONING
                || action == Action.ENCIRCLEMENT_SEARCH
                || action == Action.ENCIRCLEMENT_DISTANCE_MAP
                || action == Action.ENCIRCLEMENT_FILTER
                || action == Action.ENCIRCLEMENT_TOPOLOGY_BUILD
                || action == Action.ENCIRCLEMENT_DYNAMIC
                || action == Action.ENCIRCLEMENT_ESCAPE
                || action == Action.SUPPORT
                || action == Action.RECOVERY
                || action == Action.RECOVERY_VALIDATE
                || action == Action.RECOVERY_MOVE
                || action == Action.RECOVERY_ANIMATION
                || action == Action.MOVE_GUARD_CHECK
                || action == Action.MOVE_HAZARD_CHECK
                || action == Action.MOVE_SLEEP_CHECK
                || action == Action.MOVE_STOCK_PATH
                || action == Action.MOVE_SAFE_MASK
                || action == Action.MOVE_POLICY_PATH
                || action == Action.MOVE_EXECUTE
                || action == Action.HAZARD_ESCAPE
                || action == Action.GUARD
                || action == Action.EXPLORE
                || action == Action.EXPLORE_VALIDATE
                || action == Action.EXPLORE_SELECT
                || action == Action.EXPLORE_MOVE
                || action == Action.TTK_TOTAL
                || action == Action.TTK_MELEE
                || action == Action.TTK_RANGED
                || action == Action.TTK_MISSILE
                || action == Action.TTK_SPIRIT_BOW
                || action == Action.TTK_WAND
                || action == Action.TTK_WAND_WARDING
                || action == Action.TTK_WAND_CORROSION
                || action == Action.TTK_WAND_FIREBLAST
                || action == Action.TTK_WAND_BLAST_WAVE
                || action == Action.TTK_WAND_LIGHTNING
                || action == Action.TTK_WAND_DISINTEGRATION
                || action == Action.TTK_WAND_OTHER
                || action == Action.VISION)
                && elapsed < SLOW_PHASE_NANOS) {
            return;
        }

        int heroPos = Dungeon.hero == null ? -1 : Dungeon.hero.pos;
        boolean visible = Dungeon.level != null
                && Dungeon.level.heroFOV != null
                && owner.pos >= 0
                && owner.pos < Dungeon.level.length()
                && Dungeon.level.heroFOV[owner.pos];
        appendHistory("depth=" + Dungeon.depth + " branch=" + Dungeon.branch
                + " t=" + (int) Actor.now() + " " + action.label
                + " " + milliseconds(elapsed) + "ms"
                + " co=" + owner.pos + " hero=" + heroPos
                + (visible ? " visible" : " outside")
                + (detail == null ? "" : " decision=" + detail));
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
        String path = GamesInProgress.gameFolder(GamesInProgress.curSlot)
                + "/cohero-timings.txt";
        FileHandle file = FileUtils.getFileHandle(path);
        if (!enabled) {
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("Could not delete disabled CoHero timing report: " + path);
            }
            return;
        }
        if (!dirty) {
            return;
        }
        if (ANDROID_GC_STAT != null && lastGcCount >= 0) {
            // Export may happen before the next movement; include the last observed step.
            recordHeroStep(lastHeroPos);
        }
        file.writeString(report(), false, "UTF-8");
        dirty = false;
    }

    private static String milliseconds(long nanoseconds) {
        long tenths = nanoseconds / 100_000L;
        return (tenths / 10) + "." + (tenths % 10);
    }
}
