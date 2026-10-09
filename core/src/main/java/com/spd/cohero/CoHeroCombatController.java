package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Piranha;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.ParalyticDart;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.IdentityHashMap;

final class CoHeroCombatController {

    private final CoHeroAlly owner;
    private final CoHeroCombatTargeting targeting;
    private final CoHeroCombatPositioning positioning;
    private final CoHeroEnemyTactics enemyTactics;
    private final IdentityHashMap<Mob, RangedTurnCache> rangedTurnCaches =
            new IdentityHashMap<>();
    private int rangedTurnSerial;
    private Object rangedCacheLevel;
    private int meleeDamageTurn = -1;
    private float meleeDamage;
    private CoHeroWardingPlanner.PlanningContext wardingPlanningContext;

    private static final class RangedTurnCache {
        int turn = -1;
        boolean projectileLineEvaluated;
        int projectileCollisionPos;
        boolean highEvasionWandEvaluated;
        Wand highEvasionWand;
        boolean bestAverageDamageEvaluated;
        float bestAverageDamage;
        boolean preferRangedEvaluated;
        boolean preferRanged;
        boolean choiceEvaluated;
        RangedChoice choice;
        boolean bestDamageWandEvaluated;
        Wand bestDamageWand;
        float bestDamageWandDamage;
        int bestDamageWandAimCell;
        final IdentityHashMap<Wand, CoHeroWandAdapter.DamageEvaluation> wandDamageEvaluations =
                new IdentityHashMap<>();

        void reset(int turn) {
            this.turn = turn;
            projectileLineEvaluated = false;
            projectileCollisionPos = -1;
            highEvasionWandEvaluated = false;
            bestAverageDamageEvaluated = false;
            preferRangedEvaluated = false;
            choiceEvaluated = false;
            bestDamageWandEvaluated = false;
            highEvasionWand = null;
            choice = null;
            bestDamageWand = null;
            bestDamageWandDamage = Float.NEGATIVE_INFINITY;
            bestDamageWandAimCell = -1;
            wandDamageEvaluations.clear();
        }
    }

    CoHeroCombatController(CoHeroAlly owner) {
        this.owner = owner;
        this.targeting = new CoHeroCombatTargeting(owner);
        this.positioning = new CoHeroCombatPositioning(owner);
        this.enemyTactics = new CoHeroEnemyTactics(owner, this, positioning);
    }

    private static final float RANGED_DAMAGE_PREFERENCE_MULTIPLIER = 1.5f;

    void beginTurn() {
        if (Dungeon.level == null) {
            throw new IllegalStateException("CoHero combat turn started without a level");
        }
        if (rangedCacheLevel != Dungeon.level || rangedTurnSerial == Integer.MAX_VALUE) {
            rangedTurnCaches.clear();
            rangedCacheLevel = Dungeon.level;
            rangedTurnSerial = 1;
        } else {
            rangedTurnSerial++;
        }
        meleeDamageTurn = -1;
        wardingPlanningContext = null;
    }

    private CoHeroWardingPlanner.PlanningContext wardingPlanningContext() {
        if (wardingPlanningContext == null) {
            wardingPlanningContext = new CoHeroWardingPlanner.PlanningContext(owner);
        }
        return wardingPlanningContext;
    }

    private RangedTurnCache rangedTurnCache(Mob targetMob) {
        if (targetMob == null
                || rangedTurnSerial == 0
                || rangedCacheLevel != Dungeon.level) {
            throw new IllegalStateException(
                    "CoHero ranged evaluation queried outside the current decision turn");
        }

        RangedTurnCache cache = rangedTurnCaches.get(targetMob);
        if (cache == null) {
            cache = new RangedTurnCache();
            rangedTurnCaches.put(targetMob, cache);
        }
        if (cache.turn != rangedTurnSerial) {
            cache.reset(rangedTurnSerial);
        }
        return cache;
    }

    CoHeroWandAdapter.DamageEvaluation usableDamageEvaluation(
            Mob targetMob, Wand wand) {
        if (targetMob == null || wand == null) {
            return null;
        }

        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (!cache.wandDamageEvaluations.containsKey(wand)) {
            cache.wandDamageEvaluations.put(
                    wand,
                    CoHeroWandAdapter.usableDamageEvaluation(
                            wand, owner, targetMob, wardingPlanningContext()));
        }
        return cache.wandDamageEvaluations.get(wand);
    }

    private int projectileCollisionPos(Mob targetMob) {
        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (!cache.projectileLineEvaluated) {
            cache.projectileCollisionPos =
                    new Ballistica(
                            owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos;
            cache.projectileLineEvaluated = true;
        }
        return cache.projectileCollisionPos;
    }

    private boolean hasProjectileLine(Mob targetMob) {
        return projectileCollisionPos(targetMob) == targetMob.pos;
    }

    Mob nearestThreat(ArrayList<Mob> threats) {
        return targeting.nearestThreat(threats);
    }

    /**
     * Vertigo can randomize every ordinary walking step. Keep available attacks while
     * refusing all positioning, chasing, recall and offensive movement.
     */
    Boolean tryStationaryAttack(ArrayList<Mob> visibleThreats) {
        ArrayList<Mob> candidates = targeting.collectAttackableThreats(visibleThreats);
        if (candidates.isEmpty()) {
            return null;
        }

        Mob target = targeting.selectCombatTarget(candidates, visibleThreats);
        if (target != null && owner.canAttack(target)) {
            return performMeleeAttack(target);
        }
        if (target != null && Dungeon.level.distance(owner.pos, target.pos) > 1) {
            RangedChoice ranged = chooseRangedAttack(target);
            if (ranged != null) {
                return performRangedChoice(target, ranged);
            }
        }
        return null;
    }

    Mob selectCombatTarget(
            ArrayList<Mob> candidates, ArrayList<Mob> activeEnemies) {
        return targeting.selectCombatTarget(candidates, activeEnemies);
    }

    Mob selectSurvivalTarget(ArrayList<Mob> threats) {
        return targeting.selectSurvivalTarget(threats);
    }

    ArrayList<Mob> collectActiveThreats(ArrayList<Mob> threats) {
        return targeting.collectActiveThreats(threats);
    }

    boolean hasRecoveringCrystalGuardian(ArrayList<Mob> threats) {
        return targeting.hasRecoveringCrystalGuardian(threats);
    }

    ArrayList<Mob> collectAttackableThreats(ArrayList<Mob> threats) {
        return targeting.collectAttackableThreats(threats);
    }

    ArrayList<Mob> collectCharmingThreats(ArrayList<Mob> threats) {
        return targeting.collectCharmingThreats(threats);
    }











    Boolean tryAvoidCharmingThreats(
            ArrayList<Mob> charmingThreats, ArrayList<Mob> allThreats) {
        return positioning.tryAvoidCharmingThreats(charmingThreats, allThreats);
    }

    Boolean tryAvoidInvulnerableThreats(ArrayList<Mob> threats) {
        return positioning.tryAvoidInvulnerableThreats(threats);
    }

    int chooseRangedCoverCell(Mob targetMob, ArrayList<Mob> threats) {
        return positioning.chooseRangedCoverCell(targetMob, threats);
    }

    Boolean tryUnseenRangedCover(Mob attacker, ArrayList<Mob> attackers) {
        if (attacker == null || attackers == null || attackers.isEmpty() || owner.rooted) {
            return null;
        }
        int cover = positioning.chooseRangedCoverCell(attacker, attackers);
        if (cover == -1) {
            return null;
        }
        int step = positioning.rangedLureStep(cover);
        if (step == -1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.clearCombatTarget();
        owner.clearExplorationTarget();
        owner.clearNavigationPath();
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("unseen_ranged_cover", cover);
        owner.move(step, true);
        if (owner.pos == oldPos) {
            return null;
        }
        owner.spendActionTime(1 / owner.speed());
        owner.refreshOwnFieldOfView();
        return owner.animateMoveFrom(oldPos);
    }

    Boolean tryEncirclementPositioning(
            Mob targetMob, ArrayList<Mob> threats) {
        return positioning.tryEncirclementPositioning(targetMob, threats);
    }

    int chooseEscapeStep(ArrayList<Mob> threats) {
        return positioning.chooseEscapeStep(threats);
    }

    Boolean tryMonkFocusTactics(
            Mob targetMob, ArrayList<Mob> allThreats, CoHeroCombatRisk risk) {
        return enemyTactics.tryMonkFocusTactics(targetMob, allThreats, risk);
    }

    Boolean tryMonkOpeningTactics(Mob targetMob, ArrayList<Mob> allThreats) {
        return enemyTactics.tryMonkOpeningTactics(targetMob, allThreats);
    }

    Boolean tryShortBruteRageTactics(ArrayList<Mob> allThreats) {
        return enemyTactics.tryShortBruteRageTactics(allThreats);
    }

    Boolean tryArmoredBruteRageTactics(
            Mob targetMob, ArrayList<Mob> allThreats, CoHeroCombatRisk risk) {
        return enemyTactics.tryArmoredBruteRageTactics(targetMob, allThreats, risk);
    }

    Boolean tryScorpioTactics(
            Mob targetMob, ArrayList<Mob> allThreats, CoHeroCombatRisk risk) {
        return enemyTactics.tryScorpioTactics(targetMob, allThreats, risk);
    }

    Boolean tryMeleePositioning(Mob targetMob, ArrayList<Mob> threats) {
        return enemyTactics.tryMeleePositioning(targetMob, threats);
    }

    Boolean tryCombatSurvival(CoHeroCombatRisk risk, ArrayList<Mob> threats) {
        if (risk == null || threats == null || threats.isEmpty()) {
            throw new IllegalArgumentException("Combat survival requires risk and visible threats");
        }
        if (!risk.retreat) {
            return null;
        }

        // Once invisibility has been spent as an escape resource, preserve it: move away instead
        // of immediately breaking it with another attack or offensive utility.
        if (owner.buff(Invisibility.class) != null) {
            int invisibleEscapeStep = owner.rooted ? -1 : chooseEscapeStep(threats);
            if (invisibleEscapeStep != -1) {
                int oldPos = owner.pos;
                owner.allowAnyGuardMovement();
                owner.setMovementDecision("combat_survival_invisible_escape", invisibleEscapeStep);
                owner.move(invisibleEscapeStep, true);
                owner.spendActionTime(1 / owner.speed());
                Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                owner.revealVisibleCells();
                return owner.animateMoveFrom(oldPos);
            }
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        Boolean escapeUtility = tryEscapeUtility(risk, threats);
        if (escapeUtility != null) {
            return escapeUtility;
        }

        if (owner.survival().tryUseCleansingPotion(risk)) {
            return true;
        }

        Boolean retreatPlant = owner.survival().tryKnownRetreatPlant(risk, threats);
        if (retreatPlant != null) {
            return retreatPlant;
        }

        int escapeStep = owner.rooted ? -1 : chooseEscapeStep(threats);
        if (escapeStep != -1) {
            if (owner.controlItems().shouldUseHasteForRetreat(risk, threats, escapeStep)
                    && owner.survival().tryUseHastePotion()) {
                return true;
            }

            if (owner.controlItems().tryUseRetreatFrostPotion(risk, threats)) {
                return true;
            }

            int oldPos = owner.pos;
            owner.allowAnyGuardMovement();
            owner.setMovementDecision("combat_survival_escape", escapeStep);
            owner.move(escapeStep, true);
            owner.spendActionTime(1 / owner.speed());
            Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
            owner.revealVisibleCells();
            return owner.animateMoveFrom(oldPos);
        }

        // No safe movement remains. Controlled Blink is the cleanest displacement option.
        if (owner.controlItems().tryEmergencyBlinkRunestone(threats)) {
            return true;
        }

        // Physical blocking/paralysis/fear are deterministic tactical escape tools and should be
        // tried before giving up position to a random teleport.
        if (owner.controlItems().tryEmergencyRunestone(risk, threats)) {
            return true;
        }

        // Area fear is the emergency answer to being surrounded: keep it before
        // uncontrolled teleport, but only if it can affect unprotected threats.
        if (owner.controlItems().tryEmergencyFearScroll(risk, threats)) {
            return true;
        }

        if (owner.controlItems().tryUseTeleportationScroll()) {
            return true;
        }

        // Invisibility is the remaining emergency escape resource.
        if (owner.controlItems().tryEmergencyEscapeConsumable(risk, threats)) {
            return true;
        }

        // If control resources are unavailable, fall back to immediate shielding/healing.
        if (owner.survival().tryEmergencySurvivalPotion(
                risk.incomingDpt, risk.immediateIncoming)) {
            return true;
        }

        // Trapped with no survival action: fall through to combat rather than waste the turn.
        return null;
    }






















    Boolean tryRangedEngagement(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null || threats == null || threats.isEmpty()) {
            return null;
        }

        // Bosses keep their existing scripted combat path. Generic ranged preference and kiting
        // must not override encounter-specific movement.
        if (targetMob.properties().contains(Char.Property.BOSS)) {
            return null;
        }

        boolean rangedPressure = owner.isCurrentRangedPressure(targetMob);
        boolean rangedAttacker = rangedPressure || owner.hasNonAdjacentAttackCapability(targetMob);
        boolean preferRanged = shouldPreferRangedAttack(targetMob);

        // Avoid a long exposed charge or an expensive ranged exchange when a safe,
        // immediate cover step is available. Keep a quick kill and one-step close.
        if (rangedPressure && !owner.rooted
                && Dungeon.level.distance(owner.pos, targetMob.pos) > 2) {
            float ttk = owner.estimateTargetTtk(targetMob);
            if (ttk > 1.5f
                    && (ttk > 3f
                        || owner.estimateBestRangedDpt(targetMob)
                            < owner.estimatedIncomingDptAtCell(owner.pos, threats))) {
                int coverStep = positioning.chooseImmediateRangedCoverStep(targetMob, threats);
                if (coverStep != -1) {
                    owner.releaseGuardAreaForCombat();
                    return moveForRangedEngagement(coverStep, "ranged_immediate_cover");
                }
            }
        }

        if (preferRanged) {
            int distance = Dungeon.level.distance(owner.pos, targetMob.pos);
            if (distance > 1) {
                // Keep the existing gap. The direct ranged-attack phase will choose the weapon.
                return null;
            }

            // High-evasion enemies are a deliberate wand exception at adjacency. Wand damage does
            // not roll physical accuracy, so forcing melee here defeats the high-evasion policy.
            // Keep the ordinary adjacent restriction for missiles, Spirit Bow, and generic
            // damage-based ranged preference.
            Wand highEvasionWand = preferredDamageWandForHighEvasion(targetMob);
            if (highEvasionWand != null) {
                owner.logBossDecision(
                        "adjacent_high_evasion_wand:" + targetMob.id() + ":"
                                + highEvasionWand.getClass().getSimpleName(),
                        owner.targetDebug(targetMob) + " -> adjacent high-evasion wand");
                return performWandCast(
                        bestUsableDamageWandAimCell(targetMob), highEvasionWand);
            }

            if (canOpenRangedSpacingAgainst(targetMob)) {
                int spacingStep = chooseRangedSpacingStep(targetMob, threats);
                if (spacingStep != -1) {
                    owner.releaseGuardAreaForCombat();
                    return moveForRangedEngagement(spacingStep, "ranged_spacing");
                }
            }

            // Ordinary ranged attacks are forbidden while adjacent. If no legal safe spacing
            // step exists, stop trying to reposition and engage immediately instead of letting
            // later melee-positioning logic spend another movement turn.
            if (owner.canAttack(targetMob)) {
                owner.logBossDecision("melee_fallback:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> no safe ranged spacing, melee");
                return performMeleeAttack(targetMob);
            }
            return null;
        }

        // Never spend turns closing on a pure melee target merely because melee damage is higher.
        // Keep an existing gap; speed-aware ranged preference decides whether it is truly a free shot.
        if (!rangedAttacker && Dungeon.level.distance(owner.pos, targetMob.pos) > 1) {
            return null;
        }

        if (!owner.hasMeleeCombatCapability()) {
            return null;
        }

        if (rangedPressure) {
            // Active ranged fire may leave the outside guard area, but an active Hero-room support
            // lock still owns the doorway. Ordinary combat positioning must not oscillate back
            // through that door; only hazard/survival escape may fully release the lock.
            owner.releaseGuardAreaForCombat();
        }

        if (rangedPressure && !Dungeon.level.adjacent(owner.pos, targetMob.pos)) {
            int closeStep = positioning.chooseRangedTargetClosingStep(targetMob, threats);
            if (closeStep != -1) {
                return moveForRangedEngagement(closeStep, "ranged_close");
            }
        }

        if (owner.canAttack(targetMob)
                && (!rangedPressure || Dungeon.level.adjacent(owner.pos, targetMob.pos))) {
            return null;
        }

        if (!rangedPressure) {
            return null;
        }

        int chargeStep = positioning.chooseOneStepMeleeApproach(targetMob, threats);
        if (chargeStep != -1) {
            return moveForRangedEngagement(chargeStep, "ranged_charge");
        }

        int coverCell = chooseRangedCoverCell(targetMob, threats);
        if (coverCell == -1) {
            return null;
        }

        int step = positioning.rangedLureStep(coverCell);
        return step == -1 ? null : moveForRangedEngagement(step, "ranged_cover");
    }

    private void evaluateBestDamageWand(Mob targetMob) {
        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (cache.bestDamageWandEvaluated) {
            return;
        }

        Wand best = null;
        float bestDamage = Float.NEGATIVE_INFINITY;
        int bestAimCell = -1;
        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)
                    || !CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                continue;
            }

            CoHeroWandAdapter.DamageEvaluation evaluation =
                    usableDamageEvaluation(targetMob, wand);
            if (evaluation == null) {
                continue;
            }
            if (best == null || evaluation.expectedDamage > bestDamage) {
                best = wand;
                bestDamage = evaluation.expectedDamage;
                bestAimCell = evaluation.aimCell;
            }
        }

        cache.bestDamageWand = best;
        cache.bestDamageWandDamage = bestDamage;
        cache.bestDamageWandAimCell = bestAimCell;
        cache.bestDamageWandEvaluated = true;
    }

    private Wand bestUsableDamageWand(Mob targetMob) {
        evaluateBestDamageWand(targetMob);
        return rangedTurnCache(targetMob).bestDamageWand;
    }

    private float bestUsableDamageWandDamage(Mob targetMob) {
        evaluateBestDamageWand(targetMob);
        return rangedTurnCache(targetMob).bestDamageWandDamage;
    }

    private int bestUsableDamageWandAimCell(Mob targetMob) {
        evaluateBestDamageWand(targetMob);
        return rangedTurnCache(targetMob).bestDamageWandAimCell;
    }

    private boolean shouldPreferRangedAttack(Mob targetMob) {
        if (targetMob == null || targetMob.properties().contains(Char.Property.BOSS)) {
            return false;
        }

        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (cache.preferRangedEvaluated) {
            return cache.preferRanged;
        }

        boolean result;
        if (preferredDamageWandForHighEvasion(targetMob) != null) {
            result = true;
        } else {
            float rangedDamage = bestRangedAverageDamage(targetMob);
            if (rangedDamage <= 0f) {
                result = false;
            } else {
                float currentMeleeDamage = averageMeleeDamage();
                if (!owner.hasNonAdjacentAttackCapability(targetMob)) {
                    int distance = Dungeon.level.distance(owner.pos, targetMob.pos);
                    if (distance > 1) {
                        if (!owner.canAttack(targetMob)) {
                            boolean freeRangedWindow =
                                    owner.estimatedTimeToAttackCell(targetMob, owner.pos)
                                            > Actor.TICK + 0.001f;
                            if (freeRangedWindow || !owner.hasMeleeCombatCapability()) {
                                result = true;
                            } else {
                                result = rangedDamage
                                        >= currentMeleeDamage
                                            * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
                            }
                        } else {
                            result = rangedDamage > currentMeleeDamage;
                        }
                    } else {
                        result = currentMeleeDamage
                                < rangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
                    }
                } else {
                    float enemyRangedDamage = owner.averageRangedThreatDamage(targetMob);
                    boolean outdamagesEnemy = enemyRangedDamage >= 0f
                            && rangedDamage
                                >= enemyRangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
                    boolean outdamagesMelee =
                            rangedDamage
                                >= currentMeleeDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
                    result = outdamagesEnemy || outdamagesMelee;
                }
            }
        }

        cache.preferRanged = result;
        cache.preferRangedEvaluated = true;
        return result;
    }

    private Wand preferredDamageWandForHighEvasion(Mob targetMob) {
        if (targetMob == null) {
            return null;
        }

        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (cache.highEvasionWandEvaluated) {
            return cache.highEvasionWand;
        }

        Wand result = null;
        if (targetMob.buff(MagicImmune.class) == null) {
            int rawDefenseSkill = targetMob.defenseSkill(owner);
            boolean infiniteEvasion = rawDefenseSkill >= Char.INFINITE_EVASION;
            if (infiniteEvasion || !targetMob.coHeroSurprisedBy(owner)) {
                float accuracyMultiplier = owner.blessRollMultiplier(owner);
                float bestPhysicalAccuracy = owner.hasMeleeCombatCapability()
                        ? owner.attackSkill(targetMob) * accuracyMultiplier
                        : 0f;

                if (hasProjectileLine(targetMob)) {
                    for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                        if (owner.inventory().canUse(missile)) {
                            bestPhysicalAccuracy = Math.max(
                                    bestPhysicalAccuracy,
                                    owner.attackSkillWith(missile, targetMob)
                                            * accuracyMultiplier);
                        }
                    }

                    SpiritBow spiritBow = owner.inventory().spiritBow();
                    if (owner.inventory().canUse(spiritBow)) {
                        MissileWeapon arrow = spiritBow.knockArrow();
                        bestPhysicalAccuracy = Math.max(
                                bestPhysicalAccuracy,
                                owner.attackSkillWith(arrow, targetMob)
                                    * accuracyMultiplier);
                    }
                }

                float targetEvasion =
                        rawDefenseSkill * owner.blessRollMultiplier(targetMob);
                if (targetEvasion > bestPhysicalAccuracy) {
                    result = bestUsableDamageWand(targetMob);
                }
            }
        }

        cache.highEvasionWand = result;
        cache.highEvasionWandEvaluated = true;
        return result;
    }

    float bestRangedAverageDamage(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (cache.bestAverageDamageEvaluated) {
            return cache.bestAverageDamage;
        }

        float best = 0f;
        if (hasProjectileLine(targetMob)) {
            for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                if (owner.inventory().canUse(missile)) {
                    best = Math.max(best, CoHeroMissileAdapter.expectedDamage(owner, missile));
                }
            }

            SpiritBow spiritBow = owner.inventory().spiritBow();
            if (owner.inventory().canUse(spiritBow)) {
                best = Math.max(
                        best, CoHeroMissileAdapter.expectedSpiritBowDamage(owner, spiritBow));
            }
        }

        best = Math.max(best, bestUsableDamageWandDamage(targetMob));

        cache.bestAverageDamage = best;
        cache.bestAverageDamageEvaluated = true;
        return best;
    }

    private float averageMeleeDamage() {
        if (meleeDamageTurn == rangedTurnSerial) {
            return meleeDamage;
        }

        MeleeWeapon weapon = owner.weapon();
        if (weapon == null) {
            if (!owner.hasMeleeCombatCapability()) {
                meleeDamage = 0f;
            } else {
                meleeDamage = (RingOfForce.coHeroUnarmedMinDamage(owner, owner.STR())
                        + RingOfForce.coHeroUnarmedMaxDamage(owner, owner.STR())) / 2f;
            }
            meleeDamageTurn = rangedTurnSerial;
            return meleeDamage;
        }

        int level = weapon.buffedLvl();
        float average = (weapon.min(level) + weapon.max(level)) / 2f;
        average = weapon.augment.damageFactor(average);
        average += RingOfForce.armedDamageBonus(owner);
        int excessStrength = owner.STR() - weapon.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        meleeDamage = average;
        meleeDamageTurn = rangedTurnSerial;
        return meleeDamage;
    }

    private boolean canOpenRangedSpacingAgainst(Mob targetMob) {
        return !owner.rooted
                && (targetMob.rooted
                    || targetMob.paralysed > 0
                    || targetMob.speed() < owner.speed() - 0.001f);
    }

    private int chooseRangedSpacingStep(Mob targetMob, ArrayList<Mob> threats) {
        int currentAttackers = owner.countCurrentAttackersAtCell(owner.pos, threats);
        float currentIncoming = owner.estimatedIncomingDptAtCell(owner.pos, threats);
        int best = -1;
        int bestAttackers = currentAttackers;
        float bestIncoming = currentIncoming;
        int bestDistance = Dungeon.level.distance(owner.pos, targetMob.pos);

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || Dungeon.level.distance(cell, targetMob.pos) <= 1
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)
                    || Actor.findChar(cell) != null) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            int distance = Dungeon.level.distance(cell, targetMob.pos);

            boolean noWorse = attackers < currentAttackers
                    || (attackers == currentAttackers && incoming <= currentIncoming + 0.01f);
            if (!noWorse) {
                continue;
            }

            boolean better = best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance > bestDistance);
            if (better) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
                bestDistance = distance;
            }
        }
        return best;
    }













    Boolean moveForRangedEngagement(int step, String decision) {
        if (step == -1 || step == owner.pos) {
            return null;
        }

        int oldPos = owner.pos;
        owner.clearNavigationPath();
        owner.setMovementDecision(decision, step);
        owner.move(step, true);
        if (owner.pos == oldPos) {
            // Never consume a turn for a tactical move that execution rejected. Fall through to
            // the rest of combat so CoHero can still shoot, attack, or choose another response.
            return null;
        }
        owner.spendActionTime(1 / owner.speed());
        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
        owner.revealVisibleCells();
        return owner.animateMoveFrom(oldPos);
    }



















    Boolean tryDirectRangedAttack(
            Mob preferredTarget, ArrayList<Mob> visibleThreats) {
        if (preferredTarget == null || visibleThreats == null || visibleThreats.isEmpty()) {
            return null;
        }

        // Boss offense is selected later from all immediately legal attacks by expected DPT.
        // Deferring it here also preserves the existing chance to use one-time combat setup
        // resources before committing to an attack.
        if (preferredTarget.properties().contains(Char.Property.BOSS)) {
            return null;
        }

        // Direct ranged attacks require at least one empty tile of spacing.
        if (Dungeon.level.distance(owner.pos, preferredTarget.pos) <= 1) {
            return null;
        }

        // A pure-melee target with a gap may still be worth shooting, but speed-aware planning
        // decides whether the spacing is actually free. Extended melee is compared separately.
        boolean preferredRanged = shouldPreferRangedAttack(preferredTarget);
        if (meleeEngagementEstablished(preferredTarget, preferredRanged)) {
            return null;
        }

        RangedChoice preferred = chooseRangedAttack(preferredTarget);
        if (preferred != null) {
            return performRangedChoice(preferredTarget, preferred);
        }

        Mob alternateTarget = null;
        RangedChoice alternateChoice = null;
        int alternateDistance = Integer.MAX_VALUE;

        for (Mob threat : visibleThreats) {
            if (threat == preferredTarget
                    || threat == null
                    || !threat.isAlive()
                    || threat.invisible > 0) {
                continue;
            }

            int distance = Dungeon.level.distance(owner.pos, threat.pos);
            boolean alternateRanged = shouldPreferRangedAttack(threat);
            if (meleeEngagementEstablished(threat, alternateRanged)) {
                continue;
            }

            RangedChoice choice = chooseRangedAttack(threat);
            if (choice == null) {
                continue;
            }

            if (distance <= 1) {
                continue;
            }
            if (alternateTarget == null
                    || distance < alternateDistance
                    || (distance == alternateDistance && threat.id() < alternateTarget.id())) {
                alternateTarget = threat;
                alternateChoice = choice;
                alternateDistance = distance;
            }
        }

        return alternateTarget == null
                ? null
                : performRangedChoice(alternateTarget, alternateChoice);
    }

    private boolean meleeEngagementEstablished(
            Mob targetMob, boolean rangedPreferred) {
        return owner.canAttack(targetMob)
                && !rangedPreferred
                && (!owner.isCurrentRangedPressure(targetMob)
                    || Dungeon.level.adjacent(owner.pos, targetMob.pos));
    }

    Boolean tryFriendlyBlockedProjectileReposition(
            Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || owner.rooted
                || targetMob.properties().contains(Char.Property.BOSS)
                || Dungeon.level.distance(owner.pos, targetMob.pos) <= 1) {
            return null;
        }

        boolean rangedPreferred = shouldPreferRangedAttack(targetMob);
        if (meleeEngagementEstablished(targetMob, rangedPreferred)) {
            return null;
        }

        int collisionPos = projectileCollisionPos(targetMob);
        if (collisionPos == targetMob.pos) {
            return null;
        }

        Char blocker = Actor.findChar(collisionPos);
        if (blocker == null || blocker.alignment != Char.Alignment.ALLY) {
            return null;
        }

        boolean usableProjectile = false;
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (owner.inventory().canUse(missile)) {
                usableProjectile = true;
                break;
            }
        }
        if (!usableProjectile) {
            SpiritBow spiritBow = owner.inventory().spiritBow();
            usableProjectile = owner.inventory().canUse(spiritBow);
        }
        if (!usableProjectile) {
            return null;
        }

        int step = positioning.chooseFriendlyBlockedProjectileStep(targetMob, threats);
        if (step == -1) {
            return null;
        }

        owner.releaseGuardAreaForCombat();
        owner.logBossDecision("ranged_friendly_blocker:" + targetMob.id(),
                owner.targetDebug(targetMob) + " -> reposition for clear projectile line");
        return moveForRangedEngagement(step, "ranged_friendly_blocker");
    }

    Boolean tryPiranhaSafeRangedPositioning(Mob targetMob) {
        if (!(targetMob instanceof Piranha)
                || !targetMob.isAlive()
                || targetMob.state == targetMob.SLEEPING
                || targetMob.invisible > 0
                || owner.rooted
                || !hasUsableRangedPotential(targetMob)) {
            return null;
        }

        CoHeroTurnContext context = owner.currentTurnContext();
        if (context == null || !context.isPiranhaSafe(owner.pos)) {
            return null;
        }

        boolean[] safePassable = owner.ordinarySafePassable(false);
        for (int cell = 0; cell < safePassable.length; cell++) {
            if (cell != owner.pos
                    && safePassable[cell]
                    && !owner.fieldOfView[cell]
                    && !owner.isKnown(cell)) {
                safePassable[cell] = false;
            }
        }
        PathFinder.buildDistanceMap(owner.pos, safePassable);

        int firingCell = -1;
        int bestPathDistance = Integer.MAX_VALUE;
        int bestTargetDistance = -1;

        for (int cell = 0; cell < safePassable.length; cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (cell == owner.pos
                    || !safePassable[cell]
                    || pathDistance == Integer.MAX_VALUE
                    || pathDistance > bestPathDistance
                    || Dungeon.level.distance(cell, targetMob.pos) <= 1
                    || Actor.findChar(cell) != null
                    || !canUseRangedAttackFrom(cell, targetMob)) {
                continue;
            }

            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);
            if (firingCell == -1
                    || pathDistance < bestPathDistance
                    || (pathDistance == bestPathDistance
                        && targetDistance > bestTargetDistance)
                    || (pathDistance == bestPathDistance
                        && targetDistance == bestTargetDistance
                        && cell < firingCell)) {
                firingCell = cell;
                bestPathDistance = pathDistance;
                bestTargetDistance = targetDistance;
            }
        }

        if (firingCell == -1) {
            return null;
        }

        int step = Dungeon.findStep(
                owner, firingCell, safePassable, owner.fieldOfView, true);
        if (step == -1
                || step == owner.pos
                || !safePassable[step]
                || Actor.findChar(step) != null) {
            return null;
        }

        owner.releaseGuardAreaForCombat();
        owner.logBossDecision(
                "piranha_safe_ranged:" + targetMob.id(),
                owner.targetDebug(targetMob)
                        + " -> safe firing cell " + firingCell);
        return moveForRangedEngagement(step, "piranha_safe_ranged");
    }

    private boolean hasUsableRangedPotential(Mob targetMob) {
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (owner.inventory().canUse(missile)) {
                return true;
            }
        }

        SpiritBow spiritBow = owner.inventory().spiritBow();
        if (owner.inventory().canUse(spiritBow)) {
            return true;
        }

        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.hasOffensivePotential(wand, owner, targetMob)) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseRangedAttackFrom(int sourceCell, Mob targetMob) {
        if (Dungeon.level.distance(sourceCell, targetMob.pos) <= 1) {
            return false;
        }

        int livePos = owner.pos;
        try {
            owner.pos = sourceCell;

            Ballistica projectile =
                    new Ballistica(sourceCell, targetMob.pos, Ballistica.PROJECTILE);
            if (projectile.collisionPos == targetMob.pos) {
                for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                    if (owner.inventory().canUse(missile)) {
                        return true;
                    }
                }

                SpiritBow spiritBow = owner.inventory().spiritBow();
                if (owner.inventory().canUse(spiritBow)) {
                    return true;
                }
            }

            for (Wand wand : owner.inventory().wands()) {
                if (CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)) {
                    return true;
                }
            }
            return false;
        } finally {
            owner.pos = livePos;
        }
    }


    Boolean tryBestRangedAttack(Mob targetMob) {
        RangedChoice ranged = chooseRangedAttack(targetMob);
        return ranged == null ? null : performRangedChoice(targetMob, ranged);
    }

    private Boolean performRangedChoice(Mob targetMob, RangedChoice ranged) {
        if (targetMob == null || ranged == null) {
            return null;
        }

        if (ranged.missile != null) {
            owner.logBossDecision("missile_attack:" + targetMob.id(),
                    owner.targetDebug(targetMob) + " -> throw "
                            + ranged.missile.getClass().getSimpleName());
            return performMissileAttack(targetMob, ranged.missile);
        }
        if (ranged.spiritBow != null) {
            owner.logBossDecision("spirit_bow:" + targetMob.id(),
                    owner.targetDebug(targetMob) + " -> Spirit Bow");
            return performSpiritBowAttack(targetMob, ranged.spiritBow);
        }
        if (ranged.wand != null) {
            owner.logBossDecision(
                    "wand_attack:" + targetMob.id() + ":"
                            + ranged.wand.getClass().getSimpleName(),
                    owner.targetDebug(targetMob) + " -> "
                            + ranged.wand.getClass().getSimpleName());
            return performWandCast(ranged.wandTargetCell, ranged.wand);
        }

        throw new IllegalStateException("Empty CoHero ranged choice");
    }

    private Boolean tryBossMaximumDamageAttack(Mob targetMob) {
        if (targetMob == null
                || !targetMob.properties().contains(Char.Property.BOSS)
                || owner.isCombatInvulnerable(targetMob)) {
            return null;
        }

        float bestDpt = 0f;
        boolean meleeBest = false;
        RangedChoice rangedBest = null;

        float meleeDpt = owner.estimateMeleeDpt(targetMob);
        if (meleeDpt > bestDpt + 0.001f) {
            bestDpt = meleeDpt;
            meleeBest = true;
            rangedBest = null;
        }

        // On exact ties prefer non-consumable attacks first: melee, then Spirit Bow, then
        // missiles, then wand charges. Higher expected DPT always overrides that tie-break.
        SpiritBow spiritBow = owner.inventory().spiritBow();
        float spiritBowDpt = owner.estimateSpiritBowDpt(targetMob, spiritBow);
        if (spiritBowDpt > bestDpt + 0.001f) {
            bestDpt = spiritBowDpt;
            meleeBest = false;
            rangedBest = RangedChoice.spiritBow(spiritBow);
        }

        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            float dpt = owner.estimateMissileDpt(targetMob, missile);
            if (dpt > bestDpt + 0.001f) {
                bestDpt = dpt;
                meleeBest = false;
                rangedBest = RangedChoice.missile(missile);
            }
        }

        for (Wand wand : owner.inventory().wands()) {
            float dpt = owner.estimateDamageWandDpt(targetMob, wand);
            CoHeroWandAdapter.DamageEvaluation evaluation =
                    dpt > 0f ? usableDamageEvaluation(targetMob, wand) : null;
            if (evaluation != null
                    && evaluation.aimCell >= 0
                    && dpt > bestDpt + 0.001f) {
                bestDpt = dpt;
                meleeBest = false;
                rangedBest = RangedChoice.wand(wand, evaluation.aimCell);
            }
        }

        if (bestDpt <= 0f) {
            return null;
        }

        if (meleeBest) {
            owner.logBossDecision("boss_max_dpt_melee:" + targetMob.id(),
                    owner.targetDebug(targetMob)
                            + " -> max DPT melee "
                            + String.format("%.2f", bestDpt));
            return performMeleeAttack(targetMob);
        }

        owner.logBossDecision("boss_max_dpt_ranged:" + targetMob.id(),
                owner.targetDebug(targetMob)
                        + " -> max DPT ranged "
                        + String.format("%.2f", bestDpt));
        return performRangedChoice(targetMob, rangedBest);
    }

    Boolean tryCombat(Mob targetMob) {
        if (targetMob == null || owner.isCombatInvulnerable(targetMob)) {
            return null;
        }

        Boolean bossMaximumDamage = tryBossMaximumDamageAttack(targetMob);
        if (bossMaximumDamage != null) {
            return bossMaximumDamage;
        }

        // Melee is preferred once the intended engagement distance is actually established.
        // Against a ranged enemy, extended weapon reach is not enough: adjacency is required.
        boolean rangedPressure = owner.isCurrentRangedPressure(targetMob);
        boolean rangedPreferred = Dungeon.level.distance(owner.pos, targetMob.pos) > 1
                && shouldPreferRangedAttack(targetMob);
        if (owner.canAttack(targetMob)
                && !rangedPreferred
                && (!rangedPressure || Dungeon.level.adjacent(owner.pos, targetMob.pos))) {
            owner.logBossDecision("melee_attack:" + targetMob.id(),
                    owner.targetDebug(targetMob) + " -> melee attack");

            return performMeleeAttack(targetMob);
        }

        RangedChoice ranged = chooseRangedAttack(targetMob);
        if (ranged != null) {
            return performRangedChoice(targetMob, ranged);
        }

        Boolean wardRecall = tryWardRecall(targetMob);
        if (wardRecall != null) {
            return wardRecall;
        }

        // If we have a usable combat tool but cannot use it from this cell, close distance.
        if (hasUsableCombatCapability(targetMob)) {
            int oldPos = owner.pos;
            owner.setMovementDecision("combat_close_distance", targetMob.pos);
            if (owner.getCloser(targetMob.pos)) {
                owner.logBossDecision("close_distance:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> close distance");
                owner.spendActionTime(1 / owner.speed());
                return owner.animateMoveFrom(oldPos);
            }
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        return null;
    }

    private RangedChoice chooseRangedAttack(Mob targetMob) {
        if (targetMob == null
                || owner.isCombatInvulnerable(targetMob)
                || Dungeon.level.distance(owner.pos, targetMob.pos) <= 1) {
            return null;
        }

        RangedTurnCache cache = rangedTurnCache(targetMob);
        if (cache.choiceEvaluated) {
            return cache.choice;
        }

        RangedChoice result = null;
        ArrayList<MissileWeapon> missiles = new ArrayList<>();
        if (hasProjectileLine(targetMob)) {
            for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                if (owner.inventory().canUse(missile)) {
                    missiles.add(missile);
                }
            }
        }

        SpiritBow spiritBow = owner.inventory().spiritBow();
        MissileWeapon spiritArrow = hasProjectileLine(targetMob)
                && owner.inventory().canUse(spiritBow)
                ? spiritBow.knockArrow()
                : null;

        Wand guaranteedControl = null;
        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, owner, targetMob)
                    && (guaranteedControl == null
                        || wand.buffedLvl() > guaranteedControl.buffedLvl())) {
                guaranteedControl = wand;
            }
        }

        if (guaranteedControl != null) {
            result = RangedChoice.wand(guaranteedControl, targetMob.pos);
        } else {
            Wand highEvasionWand = preferredDamageWandForHighEvasion(targetMob);
            if (highEvasionWand != null) {
                result = RangedChoice.wand(
                        highEvasionWand,
                        bestUsableDamageWandAimCell(targetMob));
            } else {
                MissileWeapon bestMissile = null;
                float bestMissileDamage = Float.NEGATIVE_INFINITY;
                for (MissileWeapon missile : missiles) {
                    float damage = CoHeroMissileAdapter.expectedDamage(owner, missile);
                    if (bestMissile == null || damage > bestMissileDamage) {
                        bestMissile = missile;
                        bestMissileDamage = damage;
                    }
                }

                float spiritBowDamage = spiritBow == null || spiritArrow == null
                        ? Float.NEGATIVE_INFINITY
                        : CoHeroMissileAdapter.expectedSpiritBowDamage(owner, spiritBow);
                boolean spiritBowBestPhysical = spiritBowDamage > bestMissileDamage;
                float bestPhysicalDamage =
                        spiritBowBestPhysical ? spiritBowDamage : bestMissileDamage;

                Wand bestWand = bestUsableDamageWand(targetMob);
                float bestWandDamage = bestUsableDamageWandDamage(targetMob);

                if (bestPhysicalDamage > Float.NEGATIVE_INFINITY
                        && (bestWand == null || bestPhysicalDamage >= bestWandDamage)) {
                    result = spiritBowBestPhysical
                            ? RangedChoice.spiritBow(spiritBow)
                            : RangedChoice.missile(bestMissile);
                } else if (bestWand != null) {
                    result = RangedChoice.wand(
                            bestWand, bestUsableDamageWandAimCell(targetMob));
                } else {
                    Wand fallbackControl = null;
                    for (Wand wand : owner.inventory().wands()) {
                        if (CoHeroWandAdapter.fallbackControl(wand, owner, targetMob)
                                && (fallbackControl == null
                                    || wand.buffedLvl() > fallbackControl.buffedLvl())) {
                            fallbackControl = wand;
                        }
                    }
                    if (fallbackControl != null) {
                        result = RangedChoice.wand(fallbackControl, targetMob.pos);
                    }
                }
            }
        }

        cache.choice = result;
        cache.choiceEvaluated = true;
        return result;
    }

    private Boolean tryWardRecall(Mob targetMob) {
        if (targetMob == null || owner.rooted) {
            return null;
        }

        ArrayList<Mob> threats = owner.visibleAwakeEnemies();
        if (owner.anyThreatCanAttackNow(threats)) {
            return null;
        }

        CoHeroWardingPlanner.RecallPlan best = null;
        for (Wand candidate : owner.inventory().wands()) {
            if (!(candidate instanceof WandOfWarding)) {
                continue;
            }

            CoHeroWardingPlanner.RecallPlan plan =
                    CoHeroWardingPlanner.chooseRecall(
                            (WandOfWarding) candidate,
                            owner,
                            targetMob,
                            wardingPlanningContext());
            if (plan != null && (best == null || plan.gain > best.gain)) {
                best = plan;
            }
        }

        if (best == null || best.ward == null || !best.ward.isAlive()) {
            return null;
        }

        Ward ward = best.ward;
        if (Dungeon.level.adjacent(owner.pos, ward.pos)) {
            if (ward.coHeroDismiss(owner)) {
                owner.clearNavigationPath();
                owner.spendActionTime(Actor.TICK);
                return true;
            }
            return null;
        }

        int approach = chooseWardRecallApproachCell(ward);
        if (approach == -1) {
            return null;
        }

        PathFinder.Path recallPath =
                Dungeon.findPath(owner, approach, Dungeon.level.passable, owner.fieldOfView, true);
        if (recallPath == null || recallPath.isEmpty()) {
            return null;
        }

        int step = recallPath.getFirst();
        if (!owner.isMovementSafe(step)) {
            return null;
        }

        Char blocker = Actor.findChar(step);
        if (blocker != null && blocker != owner) {
            return null;
        }

        int oldPos = owner.pos;
        owner.setMovementDecision("ward_recall_approach", approach);
        owner.move(step, true);
        owner.spendActionTime(1 / owner.speed());
        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
        owner.revealVisibleCells();
        return owner.animateMoveFrom(oldPos);
    }

    private int chooseWardRecallApproachCell(Ward ward) {
        int bestCell = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = ward.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(ward.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            if (cell == owner.pos) {
                return cell;
            }

            PathFinder.Path recallPath =
                    Dungeon.findPath(owner, cell, Dungeon.level.passable, owner.fieldOfView, true);
            if (recallPath == null) {
                continue;
            }

            int distance = recallPath.size();
            if (bestCell == -1
                    || distance < bestDistance
                    || (distance == bestDistance && cell < bestCell)) {
                bestCell = cell;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    private boolean hasUsableCombatCapability(Mob targetMob) {
        if (owner.hasMeleeCombatCapability()) {
            return true;
        }
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (owner.inventory().canUse(missile)) {
                return true;
            }
        }
        SpiritBow spiritBow = owner.inventory().spiritBow();
        if (owner.inventory().canUse(spiritBow)) {
            return true;
        }
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.hasOffensivePotential(wand, owner, targetMob)) {
                return true;
            }
        }
        return false;
    }

    Boolean tryEscapeUtility(CoHeroCombatRisk risk, ArrayList<Mob> visibleThreats) {
        if (risk == null || visibleThreats == null || visibleThreats.isEmpty()) {
            throw new IllegalArgumentException("Escape utility requires current risk and threats");
        }

        // Blast Wave is a survival tool when a safe blast reduces next-turn attackers,
        // even when another wand would deal more raw damage.
        for (Wand wand : owner.inventory().wands()) {
            int blastAim = CoHeroWandAdapter.blastWaveEscapeAim(wand, owner, visibleThreats);
            if (blastAim != -1) {
                return performWandCast(blastAim, wand);
            }
        }

        // Regrowth is reusable control: rooting a pursuer is valuable for both pursuit and escape.
        for (Mob threat : visibleThreats) {
            for (Wand wand : owner.inventory().wands()) {
                if (CoHeroWandAdapter.regrowthCanSafelyRoot(
                        wand, owner, threat, visibleThreats)) {
                    return performWandCast(threat.pos, wand);
                }
            }
        }

        // Paralytic darts are finite and can miss, so reserve them for severe retreats rather than
        // ordinary ranged damage. A hit grants several turns in which CoHero can disengage.
        boolean severeRetreat =
                risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding()
                || risk.ttd <= 3f
                || risk.attackersNow >= 2;
        if (severeRetreat) {
            ParalyticDart dart = null;
            for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                if (missile instanceof ParalyticDart && owner.inventory().canUse(missile)) {
                    dart = (ParalyticDart) missile;
                    break;
                }
            }

            if (dart != null) {
                Mob best = null;
                float bestThreat = Float.NEGATIVE_INFINITY;
                for (Mob threat : visibleThreats) {
                    if (threat == null
                            || !threat.isAlive()
                            || threat.isImmune(Paralysis.class)
                            || threat.buff(Paralysis.class) != null
                            || Dungeon.level.distance(owner.pos, threat.pos) <= 1
                            || !hasProjectileLine(threat)) {
                        continue;
                    }
                    float score = owner.estimatedThreatDamage(threat, owner.pos)
                            * Math.max(0.1f, owner.threatOpportunity(threat, owner.pos));
                    if (best == null || score > bestThreat) {
                        best = threat;
                        bestThreat = score;
                    }
                }
                if (best != null) {
                    return performMissileAttack(best, dart);
                }
            }
        }

        return null;
    }

    Boolean trySupportAction() {
        if (Dungeon.hero == null
                || Dungeon.hero.pos < 0
                || Dungeon.hero.pos >= owner.fieldOfView.length) {
            return null;
        }

        boolean heroVisible = owner.fieldOfView[Dungeon.hero.pos];
        Wand best = null;
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.transfusionShouldSupportHero(
                    wand, owner, Dungeon.hero, heroVisible)
                    && (best == null || wand.buffedLvl() > best.buffedLvl())) {
                best = wand;
            }
        }
        return best == null ? null : performWandCast(Dungeon.hero.pos, best);
    }

    boolean performSpiritBowAttack(Mob targetMob, SpiritBow bow) {
        MissileWeapon arrow = bow.knockArrow();
        float delay = arrow.castDelay(owner, targetMob.pos);
        arrow.throwSound();

        boolean heroVisible = CoHero.heroCanSee(owner.pos) || CoHero.heroCanSee(targetMob.pos);
        CharSprite sprite = owner.attachedSprite();
        if (heroVisible && sprite != null && sprite.parent != null && targetMob.sprite != null) {
            ((MissileSprite) sprite.parent.recycle(MissileSprite.class)).reset(
                    sprite,
                    targetMob.sprite,
                    arrow,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveSpiritBowAttack(targetMob, arrow);
                            owner.spendActionTime(delay);
                            owner.finishAsyncAction();
                        }
                    });
            return false;
        }

        CoHeroRemoteView.attack(owner, targetMob.pos);
        resolveSpiritBowAttack(targetMob, arrow);
        owner.spendActionTime(delay);
        return true;
    }

    boolean performMeleeAttack(Mob targetMob) {
        float delay = owner.attackDelay();
        boolean heroVisible = CoHero.heroCanSee(owner.pos) || CoHero.heroCanSee(targetMob.pos);
        CharSprite sprite = owner.attachedSprite();

        if (heroVisible && sprite != null && targetMob.sprite != null) {
            long animationStarted = owner.timings().startNanos();
            sprite.attack(targetMob.pos, new Callback() {
                @Override
                public void call() {
                    owner.timings().record(owner, CoHeroTimings.Action.ATTACK_ANIMATION,
                            animationStarted);
                    owner.attackTarget(targetMob);
                    Invisibility.dispel(owner);
                    owner.spendActionTime(delay);
                    owner.finishAsyncAction();
                }
            });
            return false;
        }

        CoHeroRemoteView.attack(owner, targetMob.pos);
        owner.attackTarget(targetMob);
        Invisibility.dispel(owner);
        owner.spendActionTime(delay);
        return true;
    }

    private void resolveSpiritBowAttack(Mob targetMob, MissileWeapon arrow) {
        owner.activeMissileWeapon = arrow;
        try {
            owner.attackTarget(targetMob);
        } finally {
            owner.activeMissileWeapon = null;
        }
        Invisibility.dispel(owner);
    }

    boolean performMissileAttack(Mob targetMob, MissileWeapon source) {
        MissileWeapon thrown = owner.inventory().takeOneMissile(source);
        if (thrown == null) {
            throw new IllegalStateException("CoHero missile source disappeared before attack");
        }
        owner.loot().markThrown(thrown.setID, 1);

        float delay = thrown.castDelay(owner, targetMob.pos);
        boolean heroVisible = CoHero.heroCanSee(owner.pos) || CoHero.heroCanSee(targetMob.pos);
        CharSprite sprite = owner.attachedSprite();
        if (heroVisible && sprite != null && sprite.parent != null && targetMob.sprite != null) {
            ((MissileSprite) sprite.parent.recycle(MissileSprite.class)).reset(
                    sprite,
                    targetMob.sprite,
                    thrown,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveMissileAttack(targetMob, thrown);
                            owner.spendActionTime(delay);
                            owner.finishAsyncAction();
                        }
                    });
            return false;
        }

        CoHeroRemoteView.attack(owner, targetMob.pos);
        resolveMissileAttack(targetMob, thrown);
        owner.spendActionTime(delay);
        return true;
    }

    private void resolveMissileAttack(Mob targetMob, MissileWeapon thrown) {
        boolean hit;
        owner.activeMissileWeapon = thrown;
        try {
            hit = owner.attackTarget(targetMob);
        } finally {
            owner.activeMissileWeapon = null;
        }

        boolean survived = thrown.coHeroResolveThrow(owner, targetMob, hit);
        if (!survived) {
            owner.loot().markRecovered(thrown.setID, 1);
        }
        Invisibility.dispel(owner);
    }

    boolean performWandCast(int targetCell, Wand wand) {
        if (targetCell < 0) {
            throw new IllegalStateException("CoHero wand choice has no legal aim cell");
        }

        boolean heroVisible = CoHero.heroCanSee(owner.pos) || CoHero.heroCanSee(targetCell);
        CharSprite sprite = owner.attachedSprite();
        if (heroVisible && sprite != null && sprite.parent != null) {
            wand.coHeroCast(owner, targetCell, true, new Callback() {
                @Override
                public void call() {
                    owner.finishAsyncAction();
                }
            });
            Invisibility.dispel(owner);
            owner.spendActionTime(Actor.TICK);
            return false;
        }

        CoHeroRemoteView.zap(owner, targetCell);
        wand.coHeroCast(owner, targetCell, false, null);
        Invisibility.dispel(owner);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    private static final class RangedChoice {
        final MissileWeapon missile;
        final Wand wand;
        final SpiritBow spiritBow;
        final int wandTargetCell;

        private RangedChoice(
                MissileWeapon missile, Wand wand, SpiritBow spiritBow, int wandTargetCell) {
            this.missile = missile;
            this.wand = wand;
            this.spiritBow = spiritBow;
            this.wandTargetCell = wandTargetCell;
        }

        static RangedChoice missile(MissileWeapon missile) {
            return new RangedChoice(missile, null, null, -1);
        }

        static RangedChoice wand(Wand wand, int targetCell) {
            if (wand == null || targetCell < 0) {
                return null;
            }
            return new RangedChoice(null, wand, null, targetCell);
        }

        static RangedChoice spiritBow(SpiritBow spiritBow) {
            return new RangedChoice(null, null, spiritBow, -1);
        }
    }

}
