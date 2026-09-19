package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;
import com.watabou.utils.PathFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Deterministic pre-cast planner for Wand of Fireblast.
 *
 * Direct cone hits on allies or sleeping enemies are never allowed. The planner also reproduces
 * Fireblast's extra terrain ignition cells, then prefers aim points whose resulting fire is less
 * likely to spread onto protected characters or sleeping enemies.
 */
final class CoHeroFireblastPlanner {

    private static final int FIRE_SPREAD_LOOKAHEAD = 4;

    private CoHeroFireblastPlanner() {
    }

    static Plan choose(WandOfFireblast wand, CoHeroAlly owner, Mob target) {
        if (wand == null || owner == null || target == null) {
            return null;
        }

        int charges = wand.coHeroChargesPerCast();
        Plan best = null;

        for (int offset : PathFinder.NEIGHBOURS9) {
            int aim = target.pos + offset;
            if (!legalAim(wand, owner, target, aim)) {
                continue;
            }

            Ballistica core = wand.coHeroBallistica(owner, aim);
            ConeAOE cone = new ConeAOE(
                    core,
                    3 + 2 * charges,
                    30 + 20 * charges,
                    Ballistica.STOP_TARGET | Ballistica.STOP_SOLID | Ballistica.IGNORE_SOFT_SOLID);

            if (!cone.cells.contains(target.pos)) {
                continue;
            }

            float resistanceTotal = 0f;
            int enemyCount = 0;
            boolean unsafe = false;

            for (int cell : cone.cells) {
                Char ch = Actor.findChar(cell);
                if (ch == null) {
                    continue;
                }
                if (ch.alignment != Char.Alignment.ENEMY) {
                    unsafe = true;
                    break;
                }
                if (ch instanceof Mob && ch != target && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    unsafe = true;
                    break;
                }
                if (!ch.isInvulnerable(wand.getClass())) {
                    resistanceTotal += ch.resist(wand.getClass());
                    enemyCount++;
                }
            }

            if (unsafe || enemyCount == 0) {
                continue;
            }

            HashSet<Integer> fireCells = fireCells(core, cone);
            for (int cell : fireCells) {
                Char ch = Actor.findChar(cell);
                if (ch == null || cone.cells.contains(cell)) {
                    continue;
                }
                if (ch.alignment != Char.Alignment.ENEMY) {
                    if (!ch.isImmune(Fire.class)) {
                        unsafe = true;
                        break;
                    }
                } else if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    unsafe = true;
                    break;
                }
            }

            if (unsafe) {
                continue;
            }

            int[] fireDelay = fireSpreadDelay(fireCells);
            int protectedRisk = 0;
            int sleepingRisk = 0;
            for (int cell = 0; cell < fireDelay.length; cell++) {
                int delay = fireDelay[cell];
                if (delay <= 0 || delay > FIRE_SPREAD_LOOKAHEAD) {
                    continue;
                }

                Char ch = Actor.findChar(cell);
                if (ch == null || ch.isImmune(Fire.class)) {
                    continue;
                }

                int risk = FIRE_SPREAD_LOOKAHEAD + 1 - delay;
                if (ch.alignment != Char.Alignment.ENEMY) {
                    protectedRisk += risk;
                } else if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    sleepingRisk += risk;
                }
            }

            int level = wand.buffedLvl();
            float averageDamage = (wand.min(level) + wand.max(level)) / 2f;
            float expectedDamage = averageDamage * resistanceTotal;

            Plan candidate = new Plan(
                    aim, expectedDamage, enemyCount, protectedRisk, sleepingRisk);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
    }

    private static boolean legalAim(
            WandOfFireblast wand, CoHeroAlly owner, Mob target, int aim) {
        return Dungeon.level.insideMap(aim)
                && Dungeon.level.distance(target.pos, aim) <= 1
                && aim != owner.pos
                && wand.coHeroBallistica(owner, aim).collisionPos == aim;
    }

    private static HashSet<Integer> fireCells(Ballistica core, ConeAOE cone) {
        HashSet<Integer> result = new HashSet<>();
        ArrayList<Integer> adjacentCells = new ArrayList<>();

        for (int cell : cone.cells) {
            if (cell == core.sourcePos) {
                continue;
            }

            if (Dungeon.level.adjacent(core.sourcePos, cell)
                    && !(Dungeon.level.flamable[cell] || Dungeon.level.solid[cell])) {
                adjacentCells.add(cell);
            } else {
                result.add(cell);
            }
        }

        if (cone.cells.isEmpty()) {
            adjacentCells.add(core.sourcePos);
        }

        for (int cell : adjacentCells) {
            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = cell + offset;
                if (!Dungeon.level.insideMap(next)) {
                    continue;
                }
                if (Dungeon.level.trueDistance(next, core.collisionPos)
                        < Dungeon.level.trueDistance(cell, core.collisionPos)
                        && Dungeon.level.flamable[next]
                        && Fire.volumeAt(next, Fire.class) == 0) {
                    result.add(next);
                }
            }
        }

        return result;
    }

    private static int[] fireSpreadDelay(HashSet<Integer> seeds) {
        int[] delay = new int[Dungeon.level.length()];
        java.util.Arrays.fill(delay, -1);

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int seed : seeds) {
            delay[seed] = 0;
            queue.addLast(seed);
        }

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int nextDelay = delay[cell] + 1;
            if (nextDelay > FIRE_SPREAD_LOOKAHEAD) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS4) {
                int next = cell + offset;
                if (!Dungeon.level.insideMap(next)
                        || Dungeon.level.distance(cell, next) != 1
                        || !Dungeon.level.flamable[next]
                        || delay[next] >= 0) {
                    continue;
                }
                delay[next] = nextDelay;
                queue.addLast(next);
            }
        }

        return delay;
    }

    static final class Plan {
        final int aimCell;
        final float expectedDamage;
        private final int enemyCount;
        private final int protectedRisk;
        private final int sleepingRisk;

        private Plan(
                int aimCell,
                float expectedDamage,
                int enemyCount,
                int protectedRisk,
                int sleepingRisk) {
            this.aimCell = aimCell;
            this.expectedDamage = expectedDamage;
            this.enemyCount = enemyCount;
            this.protectedRisk = protectedRisk;
            this.sleepingRisk = sleepingRisk;
        }

        private boolean betterThan(Plan other) {
            if (protectedRisk != other.protectedRisk) {
                return protectedRisk < other.protectedRisk;
            }
            if (sleepingRisk != other.sleepingRisk) {
                return sleepingRisk < other.sleepingRisk;
            }

            int damage = Float.compare(expectedDamage, other.expectedDamage);
            if (damage != 0) {
                return damage > 0;
            }
            if (enemyCount != other.enemyCount) {
                return enemyCount > other.enemyCount;
            }
            return aimCell < other.aimCell;
        }
    }
}
