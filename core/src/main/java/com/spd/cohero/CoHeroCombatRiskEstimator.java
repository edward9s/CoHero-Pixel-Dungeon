package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Pure combat-risk estimation for CoHero.
 *
 * This component reads live combat state but does not move, spend turns, consume
 * items, or mutate tactical state.
 */
final class CoHeroCombatRiskEstimator {

    private final CoHeroAlly owner;

    CoHeroCombatRiskEstimator(CoHeroAlly owner) {
        this.owner = owner;
    }

    CoHeroCombatRisk assess(
            Mob targetMob, ArrayList<Mob> threats) {
        int attackersNow = countCurrentAttackersAtCell(owner.pos, threats);
        float incomingDpt = estimatedIncomingDptAtCell(owner.pos, threats);
        float immediateIncoming = estimatedImmediateIncomingAtCell(owner.pos, threats);
        float effectiveHp = owner.HP + owner.shielding();
        float reserve = estimatedNearTermSurvivalReserve(attackersNow);
        float outgoingDpt = estimateOutgoingDpt(targetMob);

        float ttd = incomingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : (effectiveHp + reserve) / incomingDpt;
        float ttk = outgoingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : Math.max(0.25f, targetMob.HP / outgoingDpt);

        boolean immediateLethal = immediateIncoming * 1.35f >= effectiveHp;
        boolean overwhelmed = attackersNow >= 3;

        boolean bossTarget = targetMob.properties().contains(Char.Property.BOSS);
        boolean losingRace = !bossTarget
                && incomingDpt > 0.01f
                && ttd <= ttk + 1.25f;
        boolean outnumberedRace = attackersNow >= 2
                && incomingDpt > 0.01f
                && ttd <= ttk * 1.5f;

        boolean retreat =
                immediateLethal || overwhelmed || losingRace || outnumberedRace;

        return new CoHeroCombatRisk(
                retreat, attackersNow, incomingDpt, immediateIncoming, ttd, ttk);
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
        int livePos = owner.pos;
        Random.pushGenerator(0xC0E0A11L ^ ((long) threat.id() << 21) ^ defenderCell);
        try {
            owner.pos = defenderCell;
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                total += Math.max(0, threat.damageRoll());
            }
            return Math.max(0.5f, total / 7f * 0.85f);
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

    float estimateOutgoingDpt(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        if (owner.canAttack(targetMob)) {
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

        float best = 0f;
        float targetDr = sampledDrRoll(targetMob, owner.id());

        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (!CoHeroMissileAdapter.supported(missile)
                    || missile.cursed
                    || new Ballistica(
                            owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                            != targetMob.pos) {
                continue;
            }
            float hitChance = estimatedPhysicalHitChance(
                    owner.attackSkillWith(missile, targetMob), targetMob, owner);
            best = Math.max(best,
                    Math.max(0.5f, CoHeroMissileAdapter.expectedDamage(owner, missile) - targetDr)
                            * hitChance);
        }

        SpiritBow bow = owner.inventory().spiritBow();
        if (bow != null
                && !bow.cursed
                && new Ballistica(
                        owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                        == targetMob.pos) {
            MissileWeapon arrow = bow.knockArrow();
            float hitChance = estimatedPhysicalHitChance(
                    owner.attackSkillWith(arrow, targetMob), targetMob, owner);
            best = Math.max(best,
                    Math.max(0.5f, CoHeroMissileAdapter.expectedSpiritBowDamage(owner, bow) - targetDr)
                            * hitChance);
        }

        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, owner, targetMob)) {
                return Math.max(best, targetMob.HP);
            }
            if (CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                best = Math.max(
                        best, CoHeroWandAdapter.expectedDamage(wand, owner, targetMob));
            }
        }

        return best;
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

    private float estimatedImmediateIncomingAtCell(
            int defenderCell, ArrayList<Mob> threats) {
        float result = 0f;
        for (Mob threat : threats) {
            if (canThreatAttackCell(threat, defenderCell)) {
                result += estimatedThreatDamage(threat, defenderCell)
                        * estimatedHitChance(threat, defenderCell);
            }
        }
        return result;
    }

    float threatOpportunity(Mob threat, int defenderCell) {
        if (canThreatAttackCell(threat, defenderCell)) {
            return 1f;
        }
        if (threat.rooted || threat.paralysed > 0) {
            return 0f;
        }

        for (int offset : PathFinder.NEIGHBOURS8) {
            int source = threat.pos + offset;
            if (!Dungeon.level.insideMap(source)
                    || Dungeon.level.distance(threat.pos, source) != 1
                    || !enemyCanEnterForRisk(threat, source)) {
                continue;
            }
            if (canThreatAttackFromTo(threat, source, defenderCell)) {
                return 0.55f;
            }
        }

        return Dungeon.level.distance(threat.pos, defenderCell) <= 3 ? 0.10f : 0f;
    }

    private boolean enemyCanEnterForRisk(Mob threat, int cell) {
        if (!Dungeon.level.passable[cell]) {
            if (!threat.flying || Dungeon.level.avoid[cell]) {
                return false;
            }
        }
        return !Char.hasProp(threat, Char.Property.LARGE) || Dungeon.level.openSpace[cell];
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
