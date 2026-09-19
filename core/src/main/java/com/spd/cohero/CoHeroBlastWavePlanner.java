package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfBlastWave;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TenguDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoHeroBlastWavePlanner {

    private CoHeroBlastWavePlanner() {}

    static Plan choose(WandOfBlastWave wand, CoHeroAlly owner, Mob target) {
        if (wand == null || owner == null || target == null || !wand.coHeroCanZap(owner)) return null;
        Plan best = null;
        for (int aim : candidateAimCells(target.pos)) {
            Plan plan = evaluate(wand, owner, target, aim);
            if (plan != null && (best == null || plan.score > best.score
                    || (plan.score == best.score && plan.aimCell < best.aimCell))) best = plan;
        }
        return best;
    }

    static Plan chooseEscape(WandOfBlastWave wand, CoHeroAlly owner, List<Mob> threats) {
        if (wand == null || owner == null || threats == null || threats.isEmpty()
                || !wand.coHeroCanZap(owner)) return null;
        int before = attackers(threats, owner, null);
        if (before == 0) return null;
        Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (Mob primary : threats) {
            if (primary == null || !primary.isAlive()) continue;
            for (int aim : candidateAimCells(primary.pos)) {
                Plan plan = evaluate(wand, owner, primary, aim);
                if (plan == null) continue;
                int after = attackers(threats, owner, plan);
                if (after >= before) continue;
                float score = (before - after) * 1000f + plan.chasmKills * 250f + plan.expectedDamage;
                if (best == null || score > bestScore
                        || (score == bestScore && plan.aimCell < best.aimCell)) {
                    best = plan;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int attackers(List<Mob> threats, CoHeroAlly owner, Plan plan) {
        int count = 0;
        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive() || (plan != null && plan.chasmKilled(threat))) continue;
            int source = plan == null ? threat.pos : plan.predictedPosition(threat);
            if (threat.coHeroCanAttackFrom(source, owner)) count++;
        }
        return count;
    }

    private static Plan evaluate(WandOfBlastWave wand, CoHeroAlly owner, Mob target, int aimCell) {
        if (!Dungeon.level.insideMap(aimCell)) return null;
        Ballistica bolt = wand.coHeroBallistica(owner, aimCell);
        int center = bolt.collisionPos;
        if (!Dungeon.level.insideMap(center) || Dungeon.level.distance(center, target.pos) > 1
                || wouldTriggerKnownTrap(center)) return null;

        float damage = 0f;
        float control = 0f;
        int chasmKills = 0;
        boolean targetAffected = false;
        Map<Char, Integer> predicted = new HashMap<>();
        Set<Char> killed = new HashSet<>();
        Set<Integer> destinations = new HashSet<>();

        for (int offset : PathFinder.NEIGHBOURS9) {
            int cell = center + offset;
            if (!Dungeon.level.insideMap(cell) || Dungeon.level.distance(center, cell) > 1) continue;
            Char ch = Actor.findChar(cell);
            if (ch == null) continue;

            // Fail closed for all friendly or neutral displacement, including CoHero itself.
            if (ch.alignment != Char.Alignment.ENEMY) return null;
            if (ch instanceof Mob && ch != target && ((Mob) ch).state == ((Mob) ch).SLEEPING) return null;
            if (ch == target) targetAffected = true;

            PushResult push = offset == 0
                    ? centerPush(wand, bolt, ch)
                    : radialPush(wand, ch, offset);
            if (push.moved && !destinations.add(push.destination)) return null;
            if (push.moved) predicted.put(ch, push.destination);

            boolean falls = push.moved && !ch.flying && Dungeon.level.pit[push.destination];
            if (falls) {
                killed.add(ch);
                chasmKills++;
                damage += ch.HP;
                control += 12f;
            } else {
                float direct = averageDirectDamage(wand, ch);
                float collision = push.collided ? push.distance * 1.5f : 0f;
                damage += Math.min(ch.HP, direct + collision);
                control += push.distance * 0.75f;
                if (push.collided && push.distance > 0) control += 2f + push.distance * 0.5f;
            }
        }

        if (!targetAffected) return null;
        return new Plan(aimCell, damage, damage + control, chasmKills, predicted, killed);
    }

    private static ArrayList<Integer> candidateAimCells(int targetCell) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int offset : PathFinder.NEIGHBOURS9) {
            int cell = targetCell + offset;
            if (Dungeon.level.insideMap(cell) && Dungeon.level.distance(targetCell, cell) <= 1) result.add(cell);
        }
        return result;
    }

    private static boolean wouldTriggerKnownTrap(int center) {
        for (int offset : PathFinder.NEIGHBOURS9) {
            int cell = center + offset;
            if (!Dungeon.level.insideMap(cell) || Dungeon.level.distance(center, cell) > 1) continue;
            Trap trap = Dungeon.level.traps.get(cell);
            if (trap != null && trap.active && trap.visible && !(trap instanceof TenguDartTrap)) return true;
        }
        return false;
    }

    private static float averageDirectDamage(WandOfBlastWave wand, Char target) {
        float avg = (wand.min(wand.buffedLvl()) + wand.max(wand.buffedLvl())) / 2f;
        return avg * target.resist(wand.getClass());
    }

    private static PushResult radialPush(WandOfBlastWave wand, Char ch, int direction) {
        Ballistica trajectory = new Ballistica(ch.pos, ch.pos + direction, Ballistica.MAGIC_BOLT);
        return simulateThrow(ch, trajectory, Math.round(1.5f + wand.buffedLvl() / 2f));
    }

    private static PushResult centerPush(WandOfBlastWave wand, Ballistica bolt, Char ch) {
        if (bolt.path.size() <= bolt.dist + 1) return PushResult.stationary(ch.pos);
        Ballistica trajectory = new Ballistica(ch.pos, bolt.path.get(bolt.dist + 1), Ballistica.MAGIC_BOLT);
        return simulateThrow(ch, trajectory, wand.buffedLvl() + 3);
    }

    private static PushResult simulateThrow(Char ch, Ballistica trajectory, int power) {
        if (ch.properties().contains(Char.Property.BOSS)) power = (power + 1) / 2;
        int dist = Math.min(trajectory.dist, power);
        boolean collided = dist == trajectory.dist;
        if (dist <= 0 || ch.rooted || ch.properties().contains(Char.Property.IMMOVABLE))
            return PushResult.stationary(ch.pos);

        if (Char.hasProp(ch, Char.Property.LARGE)) {
            for (int i = 1; i <= dist; i++) {
                if (!Dungeon.level.openSpace[trajectory.path.get(i)]) {
                    dist = i - 1;
                    collided = true;
                    break;
                }
            }
        }
        if (dist < 0) return PushResult.stationary(ch.pos);
        if (Actor.findChar(trajectory.path.get(dist)) != null) {
            dist--;
            collided = true;
        }
        if (dist <= 0) return PushResult.stationary(ch.pos);
        int destination = trajectory.path.get(dist);
        if (Actor.findChar(destination) != null) return PushResult.stationary(ch.pos);
        return new PushResult(true, destination, dist, collided);
    }

    static final class Plan {
        final int aimCell;
        final float expectedDamage;
        final float score;
        final int chasmKills;
        private final Map<Char, Integer> predicted;
        private final Set<Char> killed;

        Plan(int aimCell, float expectedDamage, float score, int chasmKills,
             Map<Char, Integer> predicted, Set<Char> killed) {
            this.aimCell = aimCell;
            this.expectedDamage = expectedDamage;
            this.score = score;
            this.chasmKills = chasmKills;
            this.predicted = predicted;
            this.killed = killed;
        }

        int predictedPosition(Char ch) {
            Integer value = predicted.get(ch);
            return value == null ? ch.pos : value;
        }

        boolean chasmKilled(Char ch) {
            return killed.contains(ch);
        }
    }

    private static final class PushResult {
        final boolean moved;
        final int destination;
        final int distance;
        final boolean collided;

        PushResult(boolean moved, int destination, int distance, boolean collided) {
            this.moved = moved;
            this.destination = destination;
            this.distance = distance;
            this.collided = collided;
        }

        static PushResult stationary(int cell) {
            return new PushResult(false, cell, 0, false);
        }
    }
}
