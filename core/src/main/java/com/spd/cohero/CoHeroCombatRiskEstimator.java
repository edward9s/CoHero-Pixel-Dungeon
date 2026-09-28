package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Pure combat-risk estimation for CoHero.
 *
 * This component reads live combat state but does not move, spend turns, consume
 * items, or mutate tactical state.
 */
final class CoHeroCombatRiskEstimator {

    private static final int THREAT_APPROACH_SEARCH_STEPS = 8;

    private final CoHeroAlly owner;

    CoHeroCombatRiskEstimator(CoHeroAlly owner) {
        this.owner = owner;
    }

    CoHeroCombatRisk assess(
            Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null || threats == null || threats.isEmpty()) {
            throw new IllegalArgumentException("Combat risk requires a target and visible threats");
        }

        int attackersNow = 0;
        float incomingDpt = Math.max(0, owner.incomingDOT()) * 0.20f;
        float immediateIncoming = 0f;

        for (Mob threat : threats) {
            boolean attacksNow = canThreatAttackCell(threat, owner.pos);
            if (attacksNow) {
                attackersNow++;
            }

            float opportunity = threatOpportunity(threat, owner.pos, attacksNow);
            if (opportunity <= 0f) {
                continue;
            }

            float expectedHitDamage = estimatedThreatDamage(threat, owner.pos)
                    * estimatedHitChance(threat, owner.pos);
            incomingDpt += expectedHitDamage
                    * opportunity
                    / Math.max(0.25f, threat.attackDelay());

            if (attacksNow) {
                immediateIncoming += expectedHitDamage;
            }
        }

        float effectiveHp = owner.HP + owner.shielding();
        float reserve = estimatedNearTermSurvivalReserve(attackersNow);
        float outgoingDpt = estimateOutgoingDpt(targetMob);

        float ttd = incomingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : (effectiveHp + reserve) / incomingDpt;
        float ttk = estimateTargetTtk(targetMob, outgoingDpt);

        boolean immediateLethal = immediateIncoming * 1.35f >= effectiveHp;
        boolean overwhelmed = attackersNow >= 3;
        float currentTtd = incomingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : effectiveHp / incomingDpt;
        boolean criticalTtd = currentTtd <= 3f;

        boolean bossTarget = targetMob.properties().contains(Char.Property.BOSS);
        boolean losingRace = !bossTarget
                && incomingDpt > 0.01f
                && ttd <= ttk + 1.25f;
        boolean outnumberedRace = attackersNow >= 2
                && incomingDpt > 0.01f
                && ttd <= ttk * 1.5f;

        boolean retreat =
                immediateLethal || overwhelmed || criticalTtd || losingRace || outnumberedRace;

        return new CoHeroCombatRisk(
                retreat,
                attackersNow,
                incomingDpt,
                immediateIncoming,
                outgoingDpt,
                ttd,
                ttk);
    }

    CoHeroThreatTiming assessThreatTimingAtCell(
            int defenderCell, ArrayList<Mob> threats, float horizon) {
        return assessThreatTimingAtCell(defenderCell, threats, horizon, null);
    }

    CoHeroThreatTiming assessThreatTimingAtCellWithBlockedCells(
            int defenderCell, ArrayList<Mob> threats, float horizon, boolean[] blocked) {
        if (blocked == null || blocked.length != Dungeon.level.length()) {
            throw new IllegalArgumentException("Blocked-cell projection must match level size");
        }
        return assessThreatTimingAtCell(defenderCell, threats, horizon, blocked);
    }

    private CoHeroThreatTiming assessThreatTimingAtCell(
            int defenderCell, ArrayList<Mob> threats, float horizon, boolean[] blocked) {
        if (horizon < 0f) {
            throw new IllegalArgumentException("Threat timing horizon must be non-negative");
        }

        int attackersWithinHorizon = 0;
        float incomingDptWithinHorizon = Math.max(0, owner.incomingDOT()) * 0.20f;
        float nearestAttackTime = Float.POSITIVE_INFINITY;

        if (threats == null || threats.isEmpty()) {
            return new CoHeroThreatTiming(
                    attackersWithinHorizon,
                    incomingDptWithinHorizon,
                    nearestAttackTime);
        }

        for (Mob threat : threats) {
            float timeToAttack = estimatedTimeToAttackCell(threat, defenderCell, blocked);
            nearestAttackTime = Math.min(nearestAttackTime, timeToAttack);

            if (timeToAttack <= horizon + 0.001f) {
                attackersWithinHorizon++;
            }

            float opportunity = timeToAttack <= horizon + 0.001f
                    ? 1f
                    : threatOpportunityForTime(timeToAttack);
            if (opportunity <= 0f) {
                continue;
            }

            incomingDptWithinHorizon += estimatedThreatDamage(threat, defenderCell)
                    * estimatedHitChance(threat, defenderCell)
                    * opportunity
                    / Math.max(0.25f, threat.attackDelay());
        }

        return new CoHeroThreatTiming(
                attackersWithinHorizon,
                incomingDptWithinHorizon,
                nearestAttackTime);
    }

    float estimatedTimeToAttackCell(Mob threat, int defenderCell) {
        return estimatedTimeToAttackCell(threat, defenderCell, null);
    }

    private float estimatedTimeToAttackCell(
            Mob threat, int defenderCell, boolean[] blocked) {
        if (threat == null
                || !threat.isAlive()
                || !Dungeon.level.insideMap(defenderCell)
                || threat.paralysed > 0) {
            return Float.POSITIVE_INFINITY;
        }

        if (canThreatAttackCell(threat, defenderCell)) {
            return 0f;
        }
        if (threat.rooted) {
            return Float.POSITIVE_INFINITY;
        }

        float speed = threat.speed();
        if (speed <= 0.001f) {
            return Float.POSITIVE_INFINITY;
        }

        int steps = minimumMovementStepsToAttack(threat, defenderCell, blocked);
        return steps == Integer.MAX_VALUE
                ? Float.POSITIVE_INFINITY
                : steps / speed;
    }

    private int minimumMovementStepsToAttack(
            Mob threat, int defenderCell, boolean[] blocked) {
        int length = Dungeon.level.length();
        int[] steps = new int[length];
        Arrays.fill(steps, -1);

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        steps[threat.pos] = 0;
        queue.add(threat.pos);

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int stepCount = steps[cell];

            if (cell != threat.pos && canThreatAttackFromTo(threat, cell, defenderCell)) {
                return stepCount;
            }
            if (stepCount >= THREAT_APPROACH_SEARCH_STEPS) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = cell + offset;
                if (!Dungeon.level.insideMap(next)
                        || Dungeon.level.distance(cell, next) != 1
                        || steps[next] != -1
                        || (blocked != null && blocked[next] && next != defenderCell)
                        || !enemyCanEnterForRisk(threat, next)) {
                    continue;
                }
                steps[next] = stepCount + 1;
                queue.addLast(next);
            }
        }

        return Integer.MAX_VALUE;
    }

    int countCurrentAttackersAtCell(int defenderCell, ArrayList<Mob> threats) {
        int result = 0;
        for (Mob threat : threats) {
            if (canThreatAttackCell(threat, defenderCell)) {
                result++;
            }
        }
        return result;
    }

    float estimatedIncomingDptAtCell(int defenderCell, ArrayList<Mob> threats) {
        float result = 0f;
        for (Mob threat : threats) {
            float opportunity = threatOpportunity(threat, defenderCell);
            if (opportunity <= 0f) {
                continue;
            }
            float delay = Math.max(0.25f, threat.attackDelay());
            result += estimatedThreatDamage(threat, defenderCell)
                    * estimatedHitChance(threat, defenderCell)
                    * opportunity
                    / delay;
        }
        result += Math.max(0, owner.incomingDOT()) * 0.20f;
        return result;
    }

    float estimatedThreatDamage(Mob threat, int defenderCell) {
        return Math.max(0.5f, averageThreatDamage(threat, defenderCell) * 0.85f);
    }

    float averageThreatDamage(Mob threat, int defenderCell) {
        int livePos = owner.pos;
        Random.pushGenerator(0xC0E0A11L ^ ((long) threat.id() << 21) ^ defenderCell);
        try {
            owner.pos = defenderCell;
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                total += Math.max(0, threat.damageRoll());
            }
            return total / 7f;
        } finally {
            owner.pos = livePos;
            Random.popGenerator();
        }
    }

    float averageRangedThreatDamage(Mob threat, int defenderCell) {
        int livePos = owner.pos;
        Random.pushGenerator(0xC0E0A12L ^ ((long) threat.id() << 21) ^ defenderCell);
        try {
            owner.pos = defenderCell;
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                int damage = threat.coHeroRangedDamageRoll(owner);
                if (damage < 0) {
                    return -1f;
                }
                total += damage;
            }
            return total / 7f;
        } finally {
            owner.pos = livePos;
            Random.popGenerator();
        }
    }

    float estimatedHitChance(Mob threat, int defenderCell) {
        int livePos = owner.pos;
        try {
            owner.pos = defenderCell;
            return estimatedUniformHitChance(
                    Math.max(0, threat.attackSkill(owner)) * blessRollMultiplier(threat),
                    Math.max(0, owner.defenseSkill(threat)) * blessRollMultiplier(owner));
        } finally {
            owner.pos = livePos;
        }
    }

    float estimateTargetTtk(Mob targetMob) {
        return estimateTargetTtk(targetMob, estimateOutgoingDpt(targetMob));
    }

    private float estimateTargetTtk(Mob targetMob, float outgoingDpt) {
        if (outgoingDpt <= 0.01f) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(0.25f, estimateEffectiveTargetHp(targetMob, outgoingDpt) / outgoingDpt);
    }

    private float estimateEffectiveTargetHp(Mob targetMob, float outgoingDpt) {
        float result = targetMob == null
                ? 0f
                : Math.max(0, targetMob.HP) + Math.max(0, targetMob.shielding());
        if (!(targetMob instanceof Bat) || outgoingDpt <= 0.01f) {
            return result;
        }

        float baseTtk = Math.max(0.25f, targetMob.HP / outgoingDpt);
        float firstAttackTime = estimatedTimeToAttackCell(targetMob, owner.pos);
        if (Float.isInfinite(firstAttackTime) || firstAttackTime >= baseTtk) {
            return result;
        }

        float postArmorDamage = Math.max(
                0f,
                averageThreatDamage(targetMob, owner.pos)
                        - sampledDrRoll(owner, targetMob.id()));
        float expectedHealPerAttack =
                Math.max(0f, postArmorDamage - 4f)
                        * estimatedHitChance(targetMob, owner.pos);
        if (expectedHealPerAttack <= 0.01f) {
            return result;
        }

        float expectedAttacks =
                Math.max(0f, baseTtk - firstAttackTime)
                        / Math.max(0.25f, targetMob.attackDelay());
        float missingHp = Math.max(0f, targetMob.HT - Math.max(0, targetMob.HP));
        float projectedHealing = Math.min(
                missingHp,
                expectedHealPerAttack * expectedAttacks);
        return result + projectedHealing;
    }

    float estimateOutgoingDpt(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        float melee = estimateMeleeDpt(targetMob);
        return melee > 0f ? melee : estimateBestRangedDpt(targetMob);
    }

    float estimateBestRangedDpt(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        float best = 0f;
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            best = Math.max(best, estimateMissileDpt(targetMob, missile));
        }

        SpiritBow bow = owner.inventory().spiritBow();
        best = Math.max(best, estimateSpiritBowDpt(targetMob, bow));

        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, owner, targetMob)) {
                return Math.max(best, targetMob.HP);
            }
            best = Math.max(best, estimateDamageWandDpt(targetMob, wand));
        }

        return best;
    }

    float estimateMeleeDpt(Mob targetMob) {
        if (targetMob == null || !owner.canAttack(targetMob)) {
            return 0f;
        }
        if (targetMob instanceof GreatCrab && !targetMob.coHeroSurprisedBy(owner)) {
            return 0f;
        }

        float raw = sampledDamageRoll(owner, targetMob.id());
        float dr = sampledDrRoll(targetMob, owner.id());
        float effective = Math.max(0.5f, raw - dr);
        float hitChance = targetMob.coHeroSurprisedBy(owner)
                ? 1f
                : estimatedPhysicalHitChance(
                        owner.attackSkill(targetMob), targetMob, owner);
        return effective * hitChance / Math.max(0.25f, owner.attackDelay());
    }

    float estimateMissileDpt(Mob targetMob, MissileWeapon missile) {
        if (targetMob == null
                || missile == null
                || !owner.inventory().canUse(missile)
                || Dungeon.level.distance(owner.pos, targetMob.pos) <= 1
                || new Ballistica(
                        owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                        != targetMob.pos) {
            return 0f;
        }

        float targetDr = sampledDrRoll(targetMob, owner.id());
        float hitChance = estimatedPhysicalHitChance(
                owner.attackSkillWith(missile, targetMob), targetMob, owner);
        float effective = Math.max(
                0.5f, CoHeroMissileAdapter.expectedDamage(owner, missile) - targetDr);
        float delay = Math.max(0.25f, missile.castDelay(owner, targetMob.pos));
        return effective * hitChance / delay;
    }

    float estimateSpiritBowDpt(Mob targetMob, SpiritBow bow) {
        if (targetMob == null
                || !owner.inventory().canUse(bow)
                || Dungeon.level.distance(owner.pos, targetMob.pos) <= 1
                || new Ballistica(
                        owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                        != targetMob.pos) {
            return 0f;
        }

        MissileWeapon arrow = bow.knockArrow();
        float targetDr = sampledDrRoll(targetMob, owner.id());
        float hitChance = estimatedPhysicalHitChance(
                owner.attackSkillWith(arrow, targetMob), targetMob, owner);
        float effective = Math.max(
                0.5f, CoHeroMissileAdapter.expectedSpiritBowDamage(owner, bow) - targetDr);
        float delay = Math.max(0.25f, arrow.castDelay(owner, targetMob.pos));
        return effective * hitChance / delay;
    }

    float estimateDamageWandDpt(Mob targetMob, Wand wand) {
        if (targetMob == null
                || wand == null
                || !owner.inventory().canUse(wand)
                || !CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                || !CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
            return 0f;
        }
        return Math.max(0f, CoHeroWandAdapter.expectedDamage(wand, owner, targetMob));
    }

    private float estimatedNearTermSurvivalReserve(int attackersNow) {
        float reserve = 0f;

        Barrier barrier = owner.buff(Barrier.class);
        if ((barrier == null || barrier.shielding() <= 0)
                && owner.inventory().autoShieldingPotionCount() > 0) {
            float shieldingPotion = 0.6f * owner.HT + 10f;
            reserve += shieldingPotion * (attackersNow >= 2 ? 0.45f : 0.75f);
        }

        if (owner.buff(Healing.class) != null) {
            reserve += owner.HT * 0.15f;
        } else if (owner.inventory().autoHealingPotionCount() > 0) {
            float missingHp = Math.max(0, owner.HT - owner.HP);
            float potionTotal = Math.min(0.8f * owner.HT + 14f, missingHp);
            reserve += potionTotal * (attackersNow >= 2 ? 0.20f : 0.35f);
        }

        return reserve;
    }

    float threatOpportunity(Mob threat, int defenderCell) {
        return threatOpportunity(
                threat, defenderCell, canThreatAttackCell(threat, defenderCell));
    }

    private float threatOpportunity(
            Mob threat, int defenderCell, boolean attacksNow) {
        return attacksNow
                ? 1f
                : threatOpportunityForTime(estimatedTimeToAttackCell(threat, defenderCell));
    }

    private float threatOpportunityForTime(float timeToAttack) {
        if (timeToAttack <= 0.50f) {
            return 0.75f;
        }
        if (timeToAttack <= 1.00f) {
            return 0.55f;
        }
        if (timeToAttack <= 1.50f) {
            return 0.25f;
        }
        if (timeToAttack <= 2.00f) {
            return 0.10f;
        }
        return timeToAttack <= 3.00f ? 0.05f : 0f;
    }

    private boolean enemyCanEnterForRisk(Mob threat, int cell) {
        if (!Dungeon.level.passable[cell]) {
            // Match Mob.cellIsPathable(): flying mobs may cross avoid cells such as chasms,
            // but cannot treat arbitrary solid terrain as traversable.
            if (!threat.flying || !Dungeon.level.avoid[cell]) {
                return false;
            }
        }
        return !Char.hasProp(threat, Char.Property.IMMOVABLE)
                && (!Char.hasProp(threat, Char.Property.LARGE) || Dungeon.level.openSpace[cell]);
    }

    private boolean canThreatAttackCell(Mob threat, int defenderCell) {
        return canThreatAttackFromTo(threat, threat.pos, defenderCell);
    }

    boolean hasNonAdjacentAttackCapability(Mob threat, Char target) {
        if (threat == null
                || !threat.isAlive()
                || target == null
                || !target.isAlive()) {
            return false;
        }

        if (canThreatUseNonAdjacentAttackFrom(threat, threat.pos, target)) {
            return true;
        }

        for (int offset : PathFinder.NEIGHBOURS8) {
            int sourceCell = threat.pos + offset;
            if (!Dungeon.level.insideMap(sourceCell)
                    || Dungeon.level.distance(threat.pos, sourceCell) != 1
                    || !enemyCanEnterForRisk(threat, sourceCell)) {
                continue;
            }
            if (canThreatUseNonAdjacentAttackFrom(threat, sourceCell, target)) {
                return true;
            }
        }

        return false;
    }

    private boolean canThreatUseNonAdjacentAttackFrom(
            Mob threat, int sourceCell, Char target) {
        return Dungeon.level.distance(sourceCell, target.pos) > 1
                && threat.coHeroCanAttackFrom(sourceCell, target);
    }

    private boolean canThreatAttackFromTo(
            Mob threat, int sourceCell, int defenderCell) {
        int livePos = owner.pos;
        try {
            owner.pos = defenderCell;
            return threat.coHeroCanAttackFrom(sourceCell, owner);
        } finally {
            owner.pos = livePos;
        }
    }

    float blessRollMultiplier(Char target) {
        return target != null
                && (target.buff(Bless.class) != null
                    || CoHeroClassTraits.isClericBlessed(target))
                ? 1.25f
                : 1f;
    }

    private float estimatedUniformHitChance(float accuracy, float evasion) {
        if (accuracy <= 0f) {
            return 0f;
        }
        if (evasion <= 0f) {
            return 1f;
        }

        float chance;
        if (accuracy <= evasion) {
            chance = accuracy / (2f * evasion);
        } else {
            chance = 1f - evasion / (2f * accuracy);
        }
        return Math.max(0.20f, Math.min(0.98f, chance));
    }

    private float estimatedPhysicalHitChance(
            int accuracy, Mob targetMob, Char attacker) {
        if (targetMob instanceof GreatCrab && !targetMob.coHeroSurprisedBy(attacker)) {
            return 0f;
        }
        if (targetMob.coHeroSurprisedBy(attacker)) {
            return 1f;
        }
        return estimatedUniformHitChance(
                Math.max(0, accuracy) * blessRollMultiplier(attacker),
                Math.max(0, targetMob.defenseSkill(attacker))
                        * blessRollMultiplier(targetMob));
    }

    private float sampledDamageRoll(Char attacker, int salt) {
        Random.pushGenerator(0xC0E0D4A6L ^ ((long) attacker.id() << 19) ^ salt);
        try {
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                total += Math.max(0, attacker.damageRoll());
            }
            return total / 7f;
        } finally {
            Random.popGenerator();
        }
    }

    private float sampledDrRoll(Char defender, int salt) {
        Random.pushGenerator(0xC0E0D2L ^ ((long) defender.id() << 17) ^ salt);
        try {
            float total = 0f;
            for (int i = 0; i < 5; i++) {
                total += Math.max(0, defender.drRoll());
            }
            return total / 5f;
        } finally {
            Random.popGenerator();
        }
    }
}
