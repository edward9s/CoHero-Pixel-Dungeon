package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tactical placement planner for CoHero-owned wards.
 *
 * Low-tier wards have only 1 HP and a newly-created ward is added with a 1-turn delay. Placement
 * therefore models where every currently-visible awake enemy can move before the ward's first
 * action and calls that enemy's real canAttack() implementation from each projected cell.
 */
final class CoHeroWardingPlanner {

    private static final float MIN_INITIAL_DETECTION_CHANCE = 2f / 3f;

    private CoHeroWardingPlanner() {
    }

    static Plan choose(WandOfWarding wand, CoHeroAlly owner, Mob target) {
        if (wand == null || owner == null || target == null || !wand.coHeroCanZap(owner)) {
            return null;
        }

        Plan best = null;

        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Ward)) {
                continue;
            }

            Ward ward = (Ward) ch;
            if (!ward.coHeroOwned()
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[ward.pos]
                    || CoHeroHazards.isDangerous(owner, ward.pos)
                    || wand.coHeroBallistica(owner, ward.pos).collisionPos != ward.pos
                    || !wand.coHeroWouldIncreaseWardEnergy(owner, ward.pos)) {
                continue;
            }

            int projectedTier = Math.min(6, ward.tier + 1);
            int projectedViewDistance = ward.viewDistance + (ward.tier < 6 ? 1 : 0);
            int coverage = movementCoverage(ward.pos, projectedViewDistance, target);
            if (coverage == 0
                    || wouldWakeSleepingEnemy(ward.pos, projectedViewDistance, target)) {
                continue;
            }

            int preFireThreats = preFirstActionThreats(owner, ward.pos, projectedTier);
            if (projectedTier <= 3 && preFireThreats > 0) {
                continue;
            }

            float expectedDamage = expectedNextZapDamage(wand)
                    * upgradeValue(ward.tier)
                    / (1f + 0.35f * preFireThreats);

            Plan candidate = new Plan(
                    ward.pos,
                    expectedDamage,
                    true,
                    ward.tier,
                    coverage,
                    preFireThreats,
                    CoHeroHazards.nearbyDangerCount(owner, ward.pos),
                    Dungeon.level.distance(ward.pos, target.pos),
                    Dungeon.level.distance(owner.pos, ward.pos),
                    1f);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        // Fresh wards are tier 1, have one HP, and do not act until one time unit after placement.
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (owner.fieldOfView == null
                    || !owner.fieldOfView[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || CoHeroHazards.isDangerous(owner, cell)
                    || Dungeon.level.distance(cell, target.pos) > 4
                    || wand.coHeroBallistica(owner, cell).collisionPos != cell
                    || !wand.coHeroWouldIncreaseWardEnergy(owner, cell)
                    || !canEngage(cell, 4, target)
                    || wouldWakeSleepingEnemy(cell, 4, target)) {
                continue;
            }

            int preFireThreats = preFirstActionThreats(owner, cell, 1);
            if (preFireThreats > 0) {
                continue;
            }

            float detectionChance = initialDetectionChance(cell, target);
            if (detectionChance < MIN_INITIAL_DETECTION_CHANCE) {
                continue;
            }

            int coverage = movementCoverage(cell, 4, target);
            if (coverage == 0) {
                continue;
            }

            float expectedDamage = expectedNextZapDamage(wand) * 0.70f * detectionChance;
            Plan candidate = new Plan(
                    cell,
                    expectedDamage,
                    false,
                    0,
                    coverage,
                    0,
                    CoHeroHazards.nearbyDangerCount(owner, cell),
                    Dungeon.level.distance(cell, target.pos),
                    Dungeon.level.distance(owner.pos, cell),
                    detectionChance);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
    }

    static RecallPlan chooseRecall(WandOfWarding wand, CoHeroAlly owner, Mob target) {
        if (wand == null || owner == null || target == null || !wand.coHeroCanZap(owner)) {
            return null;
        }

        int currentEnergy = wand.coHeroCurrentWardEnergy(owner);
        int maxEnergy = wand.coHeroMaxWardEnergy(owner);
        if (maxEnergy <= 0 || currentEnergy < maxEnergy) {
            return null;
        }

        // Only recall when the wand actually has a useful fresh placement that is blocked by
        // energy. If an ordinary placement/upgrade is already legal, dismantling is unnecessary.
        if (choose(wand, owner, target) != null) {
            return null;
        }

        Plan replacement = chooseFreshIgnoringBudget(wand, owner, target);
        if (replacement == null) {
            return null;
        }

        float replacementValue = replacementValue(replacement);
        RecallPlan best = null;

        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Ward)) {
                continue;
            }

            Ward ward = (Ward) ch;
            if (!ward.coHeroOwned() || !ward.isAlive()) {
                continue;
            }

            int coverage = wardBattlefieldCoverage(owner, ward);
            float retainedValue = retainedWardValue(wand, owner, ward, coverage);
            int travelDistance = Dungeon.level.distance(owner.pos, ward.pos);
            float gain = replacementValue - retainedValue - 3f * travelDistance;

            // Avoid churn for marginal rearrangements. Tier 4-6 sentries carry a large retention
            // reserve below, so they are only recalled when their current battlefield value is
            // essentially exhausted and the replacement is materially better.
            if (gain < 15f) {
                continue;
            }

            RecallPlan candidate = new RecallPlan(
                    ward,
                    replacement.aimCell,
                    gain,
                    retainedValue,
                    replacementValue,
                    coverage,
                    travelDistance);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
    }

    private static Plan chooseFreshIgnoringBudget(
            WandOfWarding wand, CoHeroAlly owner, Mob target) {
        Plan best = null;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (owner.fieldOfView == null
                    || !owner.fieldOfView[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || CoHeroHazards.isDangerous(owner, cell)
                    || Dungeon.level.distance(cell, target.pos) > 4
                    || wand.coHeroBallistica(owner, cell).collisionPos != cell
                    || !canEngage(cell, 4, target)
                    || wouldWakeSleepingEnemy(cell, 4, target)) {
                continue;
            }

            int preFireThreats = preFirstActionThreats(owner, cell, 1);
            if (preFireThreats > 0) {
                continue;
            }

            float detectionChance = initialDetectionChance(cell, target);
            if (detectionChance < MIN_INITIAL_DETECTION_CHANCE) {
                continue;
            }

            int coverage = movementCoverage(cell, 4, target);
            if (coverage == 0) {
                continue;
            }

            float expectedDamage = expectedNextZapDamage(wand) * 0.70f * detectionChance;
            Plan candidate = new Plan(
                    cell,
                    expectedDamage,
                    false,
                    0,
                    coverage,
                    0,
                    CoHeroHazards.nearbyDangerCount(owner, cell),
                    Dungeon.level.distance(cell, target.pos),
                    Dungeon.level.distance(owner.pos, cell),
                    detectionChance);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
    }

    private static float replacementValue(Plan replacement) {
        return replacement.movementCoverage * 20f
                + replacement.expectedDamage * 4f
                + replacement.detectionChance * 10f
                - replacement.nearbyDanger * 8f;
    }

    private static int wardBattlefieldCoverage(CoHeroAlly owner, Ward ward) {
        int coverage = 0;
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Mob)
                    || ch.alignment != Char.Alignment.ENEMY
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[ch.pos]) {
                continue;
            }
            Mob enemy = (Mob) ch;
            if (enemy.state == enemy.SLEEPING || enemy.state == enemy.PASSIVE) {
                continue;
            }
            coverage += movementCoverage(ward.pos, ward.viewDistance, enemy);
        }
        return coverage;
    }

    private static int wardEngagedEnemyCount(CoHeroAlly owner, Ward ward) {
        int count = 0;
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Mob)
                    || ch.alignment != Char.Alignment.ENEMY
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[ch.pos]) {
                continue;
            }
            Mob enemy = (Mob) ch;
            if (enemy.state == enemy.SLEEPING || enemy.state == enemy.PASSIVE) {
                continue;
            }
            if (canEngage(ward.pos, ward.viewDistance, enemy)) {
                count++;
            }
        }
        return count;
    }

    private static float retainedWardValue(
            WandOfWarding wand, CoHeroAlly owner, Ward ward, int coverage) {
        float value = coverage * 20f;
        value += wardEngagedEnemyCount(owner, ward) * expectedNextZapDamage(wand) * 3f;

        switch (ward.tier) {
            case 1:
                break;
            case 2:
                value += 12f;
                break;
            case 3:
                value += 30f;
                break;
            case 4:
                value += 90f;
                break;
            case 5:
                value += 140f;
                break;
            case 6:
            default:
                value += 220f;
                break;
        }

        if (CoHeroHazards.isDangerous(owner, ward.pos)) {
            value *= 0.75f;
        }
        return value;
    }

    static final class RecallPlan {
        final Ward ward;
        final int replacementAimCell;
        final float gain;

        private final float retainedValue;
        private final float replacementValue;
        private final int currentCoverage;
        private final int travelDistance;

        RecallPlan(
                Ward ward,
                int replacementAimCell,
                float gain,
                float retainedValue,
                float replacementValue,
                int currentCoverage,
                int travelDistance) {
            this.ward = ward;
            this.replacementAimCell = replacementAimCell;
            this.gain = gain;
            this.retainedValue = retainedValue;
            this.replacementValue = replacementValue;
            this.currentCoverage = currentCoverage;
            this.travelDistance = travelDistance;
        }

        private boolean betterThan(RecallPlan other) {
            int gainCompare = Float.compare(gain, other.gain);
            if (gainCompare != 0) {
                return gainCompare > 0;
            }
            if (ward.tier != other.ward.tier) {
                return ward.tier < other.ward.tier;
            }
            if (currentCoverage != other.currentCoverage) {
                return currentCoverage < other.currentCoverage;
            }
            if (travelDistance != other.travelDistance) {
                return travelDistance < other.travelDistance;
            }
            return ward.pos < other.ward.pos;
        }
    }

    private static int preFirstActionThreats(CoHeroAlly owner, int wardCell, int projectedTier) {
        Ward probe = new Ward();
        probe.pos = wardCell;
        probe.tier = projectedTier;

        int threats = 0;
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Mob)
                    || ch.alignment != Char.Alignment.ENEMY
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[ch.pos]) {
                continue;
            }

            Mob enemy = (Mob) ch;
            if (enemy.state == enemy.SLEEPING || enemy.state == enemy.PASSIVE) {
                continue;
            }

            boolean threatens = false;
            for (int source : reachableBeforeWardActs(enemy)) {
                if (enemy.coHeroCanAttackFrom(source, probe)) {
                    threatens = true;
                    break;
                }
            }
            if (threatens) {
                threats++;
            }
        }
        return threats;
    }

    /**
     * A new ward is inserted with delay=1f. Over-approximate every terrain cell an enemy may
     * occupy during that time. Character blockers are intentionally ignored: that makes this a
     * conservative safety test rather than assuming another actor will keep blocking the route.
     */
    private static ArrayList<Integer> reachableBeforeWardActs(Mob enemy) {
        ArrayList<Integer> result = new ArrayList<>();
        result.add(enemy.pos);

        if (enemy.rooted || enemy.paralysed > 0) {
            return result;
        }

        int steps = Math.max(1, (int) Math.ceil(enemy.speed()));
        int[] depth = new int[Dungeon.level.length()];
        Arrays.fill(depth, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        depth[enemy.pos] = 0;
        queue.addLast(enemy.pos);

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            if (depth[cell] >= steps) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = cell + offset;
                if (!Dungeon.level.insideMap(next)
                        || Dungeon.level.distance(cell, next) != 1
                        || depth[next] >= 0
                        || !enemyCanEnter(enemy, next)) {
                    continue;
                }
                depth[next] = depth[cell] + 1;
                queue.addLast(next);
                result.add(next);
            }
        }

        return result;
    }

    private static boolean enemyCanEnter(Mob enemy, int cell) {
        if (!Dungeon.level.passable[cell]) {
            if (!enemy.flying || Dungeon.level.avoid[cell]) {
                return false;
            }
        }
        return !Char.hasProp(enemy, Char.Property.LARGE) || Dungeon.level.openSpace[cell];
    }

    private static int movementCoverage(int wardCell, int viewDistance, Mob target) {
        int covered = 0;
        for (int future : reachableBeforeWardActs(target)) {
            if (Dungeon.level.distance(wardCell, future) <= viewDistance
                    && new Ballistica(wardCell, future, Ballistica.MAGIC_BOLT).collisionPos == future) {
                covered++;
            }
        }
        return covered;
    }

    private static float initialDetectionChance(int wardCell, Mob target) {
        float denominator = Dungeon.level.distance(wardCell, target.pos) / 2f + target.stealth();
        return denominator <= 0f ? 1f : Math.min(1f, 1f / denominator);
    }

    private static boolean canEngage(int wardCell, int viewDistance, Mob target) {
        return Dungeon.level.distance(wardCell, target.pos) <= viewDistance
                && new Ballistica(wardCell, target.pos, Ballistica.MAGIC_BOLT).collisionPos == target.pos;
    }

    private static boolean wouldWakeSleepingEnemy(int wardCell, int viewDistance, Mob intendedTarget) {
        for (Char ch : Actor.chars()) {
            if (!(ch instanceof Mob) || ch == intendedTarget || ch.alignment != Char.Alignment.ENEMY) {
                continue;
            }

            Mob mob = (Mob) ch;
            if (mob.state != mob.SLEEPING
                    || Dungeon.level.distance(wardCell, mob.pos) > viewDistance) {
                continue;
            }

            if (new Ballistica(wardCell, mob.pos, Ballistica.MAGIC_BOLT).collisionPos == mob.pos) {
                return true;
            }
        }
        return false;
    }

    private static float expectedNextZapDamage(WandOfWarding wand) {
        int level = wand.buffedLvl();
        return ((2 + level) + (8 + 4 * level)) / 2f;
    }

    private static float upgradeValue(int currentTier) {
        switch (currentTier) {
            case 1:
            case 2:
                return 1.10f;
            case 3:
                return 1.35f; // tier 3 -> 4 is the first upgrade that gives real durability.
            case 4:
            case 5:
                return 1.15f;
            default:
                return 1f;
        }
    }

    static final class Plan {
        final int aimCell;
        final float expectedDamage;

        private final boolean upgradesExisting;
        private final int existingTier;
        private final int movementCoverage;
        private final int preFireThreats;
        private final int nearbyDanger;
        private final int targetDistance;
        private final int ownerDistance;
        private final float detectionChance;

        private Plan(
                int aimCell,
                float expectedDamage,
                boolean upgradesExisting,
                int existingTier,
                int movementCoverage,
                int preFireThreats,
                int nearbyDanger,
                int targetDistance,
                int ownerDistance,
                float detectionChance) {
            this.aimCell = aimCell;
            this.expectedDamage = expectedDamage;
            this.upgradesExisting = upgradesExisting;
            this.existingTier = existingTier;
            this.movementCoverage = movementCoverage;
            this.preFireThreats = preFireThreats;
            this.nearbyDanger = nearbyDanger;
            this.targetDistance = targetDistance;
            this.ownerDistance = ownerDistance;
            this.detectionChance = detectionChance;
        }

        private boolean betterThan(Plan other) {
            if (preFireThreats != other.preFireThreats) {
                return preFireThreats < other.preFireThreats;
            }
            if (movementCoverage != other.movementCoverage) {
                return movementCoverage > other.movementCoverage;
            }

            int damage = Float.compare(expectedDamage, other.expectedDamage);
            if (damage != 0) {
                return damage > 0;
            }
            if (nearbyDanger != other.nearbyDanger) {
                return nearbyDanger < other.nearbyDanger;
            }
            if (Float.compare(detectionChance, other.detectionChance) != 0) {
                return detectionChance > other.detectionChance;
            }

            // Do not blindly prefer an upgrade: only use tier and type as late deterministic
            // tie-breakers after survival, movement coverage and action value.
            if (upgradesExisting != other.upgradesExisting) {
                return upgradesExisting;
            }
            if (existingTier != other.existingTier) {
                return existingTier > other.existingTier;
            }

            // Around three cells is the useful low-tier compromise: enough standoff to avoid
            // immediate melee destruction while keeping the first detection roll reasonably high.
            int distancePenalty = Math.abs(targetDistance - 3);
            int otherPenalty = Math.abs(other.targetDistance - 3);
            if (distancePenalty != otherPenalty) {
                return distancePenalty < otherPenalty;
            }
            if (ownerDistance != other.ownerDistance) {
                return ownerDistance < other.ownerDistance;
            }
            return aimCell < other.aimCell;
        }
    }
}
