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

        Plan best = null;
        int gasAmount = 50 + 10 * wand.buffedLvl();

        for (int offset : PathFinder.NEIGHBOURS9) {
            int aim = target.pos + offset;
            if (!legalAim(wand, owner, target, aim)) {
                continue;
            }

            int[] firstExposure = firstExposureByCell(aim, gasAmount);
            int targetDelay = firstExposure[target.pos];
            if (targetDelay < 0 || targetDelay > TARGET_WINDOW) {
                continue;
            }

            float enemyWeight = 0f;
            int protectedRisk = 0;
            int sleepingRisk = 0;
            boolean unsafe = false;

            for (int cell = 0; cell < firstExposure.length; cell++) {
                int delay = firstExposure[cell];
                if (delay < 0) {
                    continue;
                }

                Char ch = Actor.findChar(cell);
                if (ch == null || ch.isImmune(CorrosiveGas.class)) {
                    continue;
                }

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

    private static int[] firstExposureByCell(int seedCell, int amount) {
        int length = Dungeon.level.length();
        int width = Dungeon.level.width();
        int[] cur = new int[length];

        CorrosiveGas existing = (CorrosiveGas) Dungeon.level.blobs.get(CorrosiveGas.class);
        Rect area = new Rect();
        if (existing != null && existing.volume > 0 && existing.cur != null) {
            System.arraycopy(existing.cur, 0, cur, 0, Math.min(existing.cur.length, length));
            area.set(existing.area);
        }

        cur[seedCell] += amount;
        area.union(seedCell % width, seedCell / width);

        int[] firstExposure = new int[length];
        Arrays.fill(firstExposure, -1);
        markExposure(cur, firstExposure, 0);

        for (int turn = 1; turn <= LOOKAHEAD; turn++) {
            cur = evolve(cur, area, width);
            markExposure(cur, firstExposure, turn);
        }

        return firstExposure;
    }

    /**
     * Mirrors Blob.evolve(): the area object deliberately expands while the loops are running.
     */
    private static int[] evolve(int[] cur, Rect area, int width) {
        int[] next = new int[cur.length];
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

        return next;
    }

    private static void markExposure(int[] gas, int[] firstExposure, int turn) {
        for (int cell = 0; cell < gas.length; cell++) {
            if (gas[cell] > 0 && firstExposure[cell] < 0) {
                firstExposure[cell] = turn;
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
