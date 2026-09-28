package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorrosion;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Rect;

import java.util.Arrays;

/**
 * Deterministic pre-cast planner for Wand of Corrosion.
 *
 * It models the stock Blob.evolve() diffusion rule without consuming combat RNG. The simulation
 * includes an already-active CorrosiveGas blob because a new cast joins that same global blob.
 */
final class CoHeroCorrosionPlanner {

    private static final int LOOKAHEAD = 8;
    private static final int PROTECTED_WINDOW = 3;
    private static final int TARGET_WINDOW = 2;

    private CoHeroCorrosionPlanner() {
    }

    static Plan choose(WandOfCorrosion wand, CoHeroAlly owner, Mob target) {
        if (wand == null
                || owner == null
                || target == null
                || target.isImmune(CorrosiveGas.class)) {
            return null;
        }

        SimulationContext simulation = new SimulationContext();
        Plan best = null;
        int gasAmount = 50 + 10 * wand.buffedLvl();

        for (int offset : PathFinder.NEIGHBOURS9) {
            int aim = target.pos + offset;
            if (!legalAim(wand, owner, target, aim)) {
                continue;
            }

            int targetDelay = simulation.simulate(aim, gasAmount, target);
            if (targetDelay < 0 || targetDelay > TARGET_WINDOW) {
                continue;
            }

            float enemyWeight = 0f;
            int protectedRisk = 0;
            int sleepingRisk = 0;
            boolean unsafe = false;

            for (int i = 0; i < simulation.characters.length; i++) {
                int delay = simulation.firstExposure[i];
                if (delay < 0) {
                    continue;
                }

                Char ch = simulation.characters[i];
                if (ch.alignment != Char.Alignment.ENEMY) {
                    if (delay <= PROTECTED_WINDOW) {
                        unsafe = true;
                        break;
                    }
                    protectedRisk += LOOKAHEAD + 1 - delay;
                } else if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    if (delay <= PROTECTED_WINDOW) {
                        unsafe = true;
                        break;
                    }
                    sleepingRisk += LOOKAHEAD + 1 - delay;
                } else {
                    enemyWeight += 1f / (1f + 0.35f * delay);
                }
            }

            if (unsafe || enemyWeight <= 0f) {
                continue;
            }

            int strength = 2 + wand.buffedLvl();
            float expectedDamage = (strength * 2f + 1f) * enemyWeight;
            Plan candidate = new Plan(
                    aim, expectedDamage, enemyWeight, protectedRisk, sleepingRisk, targetDelay);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
    }

    private static boolean legalAim(
            WandOfCorrosion wand, CoHeroAlly owner, Mob target, int aim) {
        return Dungeon.level.insideMap(aim)
                && Dungeon.level.distance(target.pos, aim) <= 1
                && !Dungeon.level.solid[aim]
                && aim != owner.pos
                && wand.coHeroBallistica(owner, aim).collisionPos == aim;
    }

    private static final class SimulationContext {
        final int length = Dungeon.level.length();
        final int width = Dungeon.level.width();
        final int[] baseGas = new int[length];
        final int[] scratchA = new int[length];
        final int[] scratchB = new int[length];
        final Rect baseArea = new Rect();
        final Char[] characters;
        final int[] firstExposure;

        SimulationContext() {
            CorrosiveGas existing =
                    (CorrosiveGas) Dungeon.level.blobs.get(CorrosiveGas.class);
            if (existing != null && existing.volume > 0 && existing.cur != null) {
                System.arraycopy(
                        existing.cur, 0, baseGas, 0,
                        Math.min(existing.cur.length, length));
                baseArea.set(existing.area);
                if (baseArea.isEmpty()) {
                    for (int cell = 0; cell < baseGas.length; cell++) {
                        if (baseGas[cell] > 0) {
                            baseArea.union(cell % width, cell / width);
                        }
                    }
                }
            }

            Char[] byCell = new Char[length];
            int count = 0;
            for (int cell = 0; cell < length; cell++) {
                Char ch = Actor.findChar(cell);
                if (ch != null && !ch.isImmune(CorrosiveGas.class)) {
                    byCell[count++] = ch;
                }
            }
            characters = Arrays.copyOf(byCell, count);
            firstExposure = new int[count];
        }

        int simulate(int seedCell, int amount, Mob target) {
            System.arraycopy(baseGas, 0, scratchA, 0, length);
            Arrays.fill(firstExposure, -1);

            Rect area = new Rect();
            area.set(baseArea);
            scratchA[seedCell] += amount;
            area.union(seedCell % width, seedCell / width);

            int[] cur = scratchA;
            int[] next = scratchB;
            recordExposure(cur, 0);
            int targetDelay = cur[target.pos] > 0 ? 0 : -1;

            for (int turn = 1; turn <= TARGET_WINDOW; turn++) {
                Arrays.fill(next, 0);
                evolve(cur, next, area, width);
                int[] swap = cur;
                cur = next;
                next = swap;

                recordExposure(cur, turn);
                if (targetDelay < 0 && cur[target.pos] > 0) {
                    targetDelay = turn;
                }
            }

            // Existing policy discards any aim that does not expose the intended target by turn 2.
            // Do not simulate the remaining six turns for a candidate that can no longer win.
            if (targetDelay < 0) {
                return -1;
            }

            for (int turn = TARGET_WINDOW + 1; turn <= LOOKAHEAD; turn++) {
                Arrays.fill(next, 0);
                evolve(cur, next, area, width);
                int[] swap = cur;
                cur = next;
                next = swap;
                recordExposure(cur, turn);
            }

            return targetDelay;
        }

        private void recordExposure(int[] gas, int turn) {
            for (int i = 0; i < characters.length; i++) {
                if (firstExposure[i] >= 0) {
                    continue;
                }
                int cell = characters[i].pos;
                if (cell >= 0 && cell < gas.length && gas[cell] > 0) {
                    firstExposure[i] = turn;
                }
            }
        }
    }

    /**
     * Mirrors Blob.evolve(): the area object deliberately expands while the loops are running.
     */
    private static void evolve(int[] cur, int[] next, Rect area, int width) {
        boolean[] blocking = Dungeon.level.solid;

        for (int y = area.top - 1; y <= area.bottom; y++) {
            for (int x = area.left - 1; x <= area.right; x++) {
                int cell = x + y * width;
                if (!Dungeon.level.insideMap(cell)) {
                    continue;
                }
                if (blocking[cell]) {
                    next[cell] = 0;
                    continue;
                }

                int count = 1;
                int sum = cur[cell];

                if (x > area.left && !blocking[cell - 1]) {
                    sum += cur[cell - 1];
                    count++;
                }
                if (x < area.right && !blocking[cell + 1]) {
                    sum += cur[cell + 1];
                    count++;
                }
                if (y > area.top && !blocking[cell - width]) {
                    sum += cur[cell - width];
                    count++;
                }
                if (y < area.bottom && !blocking[cell + width]) {
                    sum += cur[cell + width];
                    count++;
                }

                int value = sum >= count ? (sum / count) - 1 : 0;
                next[cell] = value;
                if (value > 0) {
                    area.union(x, y);
                }
            }
        }
    }

    static final class Plan {
        final int aimCell;
        final float expectedDamage;
        private final float enemyWeight;
        private final int protectedRisk;
        private final int sleepingRisk;
        private final int targetDelay;

        private Plan(
                int aimCell,
                float expectedDamage,
                float enemyWeight,
                int protectedRisk,
                int sleepingRisk,
                int targetDelay) {
            this.aimCell = aimCell;
            this.expectedDamage = expectedDamage;
            this.enemyWeight = enemyWeight;
            this.protectedRisk = protectedRisk;
            this.sleepingRisk = sleepingRisk;
            this.targetDelay = targetDelay;
        }

        private boolean betterThan(Plan other) {
            if (protectedRisk != other.protectedRisk) {
                return protectedRisk < other.protectedRisk;
            }
            if (sleepingRisk != other.sleepingRisk) {
                return sleepingRisk < other.sleepingRisk;
            }

            int enemies = Float.compare(enemyWeight, other.enemyWeight);
            if (enemies != 0) {
                return enemies > 0;
            }
            if (targetDelay != other.targetDelay) {
                return targetDelay < other.targetDelay;
            }
            return aimCell < other.aimCell;
        }
    }
}
