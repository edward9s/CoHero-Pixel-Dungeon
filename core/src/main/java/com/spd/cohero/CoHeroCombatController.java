package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
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

final class CoHeroCombatController {

    private final CoHeroAlly owner;
    private final CoHeroCombatTargeting targeting;
    private final CoHeroCombatPositioning positioning;
    private final CoHeroEnemyTactics enemyTactics;

    CoHeroCombatController(CoHeroAlly owner) {
        this.owner = owner;
        this.targeting = new CoHeroCombatTargeting(owner);
        this.positioning = new CoHeroCombatPositioning(owner);
        this.enemyTactics = new CoHeroEnemyTactics(owner, this, positioning);
    }

    private static final float RANGED_DAMAGE_PREFERENCE_MULTIPLIER = 1.5f;

    Mob nearestThreat(ArrayList<Mob> threats) {
        return targeting.nearestThreat(threats);
    }

    Mob selectCombatTarget(ArrayList<Mob> threats) {
        return targeting.selectCombatTarget(threats);
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

        if (owner.controlItems().tryUseTeleportationScroll()) {
            return true;
        }

        // Potions/scrolls remain the next emergency layer.
        if (owner.controlItems().tryEmergencyEscapeConsumable(risk, threats)) {
            return true;
        }

        // If control resources are unavailable, fall back to immediate shielding/healing.
        if (owner.survival().tryEmergencySurvivalPotion()) {
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

        if (preferRanged) {
            int distance = Dungeon.level.distance(owner.pos, targetMob.pos);
            if (distance > 1) {
                // Keep the existing gap. The direct ranged-attack phase will choose the weapon.
                return null;
            }

            if (canOpenRangedSpacingAgainst(targetMob)) {
                int spacingStep = chooseRangedSpacingStep(targetMob, threats);
                if (spacingStep != -1) {
                    owner.allowAnyGuardMovement();
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
            // Active ranged fire is combat territory, not guard-roaming territory. The ranged
            // planner already evaluates live safety/occupancy, so guard scope must not reject the
            // step after planning has selected a close-in or LOS-cover move.
            owner.allowAnyGuardMovement();
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

    private boolean shouldPreferRangedAttack(Mob targetMob) {
        if (targetMob == null || targetMob.properties().contains(Char.Property.BOSS)) {
            return false;
        }

        if (preferredDamageWandForHighEvasion(targetMob) != null) {
            return true;
        }

        float rangedDamage = bestRangedAverageDamage(targetMob);
        if (rangedDamage <= 0f) {
            return false;
        }

        float meleeDamage = averageMeleeDamage();
        if (!owner.hasNonAdjacentAttackCapability(targetMob)) {
            int distance = Dungeon.level.distance(owner.pos, targetMob.pos);
            if (distance > 1) {
                // A gap is only a truly free ranged turn when the target cannot enter attack range
                // before a normal CoHero action finishes. Fast melee enemies such as bats and crabs
                // can consume several cells of distance inside that same time window.
                if (!owner.canAttack(targetMob)) {
                    boolean freeRangedWindow =
                            owner.estimatedTimeToAttackCell(targetMob, owner.pos)
                                    > Actor.TICK + 0.001f;
                    if (freeRangedWindow || !owner.hasMeleeCombatCapability()) {
                        return true;
                    }

                    // Shooting can still be the best currently legal action, but do not treat the
                    // spacing itself as strategically valuable unless ranged damage is compelling.
                    return rangedDamage
                            >= meleeDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
                }

                // Extended melee already reaches the target, so neither option costs movement.
                return rangedDamage > meleeDamage;
            }

            // Once a pure-melee target has reached adjacency, reopening distance costs a turn.
            // Only kite again unless the melee build is clearly stronger.
            return meleeDamage < rangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        }

        float enemyRangedDamage = owner.averageRangedThreatDamage(targetMob);
        boolean outdamagesEnemy = enemyRangedDamage >= 0f
                && rangedDamage >= enemyRangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        boolean outdamagesMelee =
                rangedDamage >= meleeDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        return outdamagesEnemy || outdamagesMelee;
    }

    private Wand preferredDamageWandForHighEvasion(Mob targetMob) {
        if (targetMob == null || targetMob.buff(MagicImmune.class) != null) {
            return null;
        }

        int rawDefenseSkill = targetMob.defenseSkill(owner);
        boolean infiniteEvasion = rawDefenseSkill >= Char.INFINITE_EVASION;
        if (!infiniteEvasion && targetMob.coHeroSurprisedBy(owner)) {
            return null;
        }

        ArrayList<Wand> damageWands = new ArrayList<>();
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.supported(wand)
                    && CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                damageWands.add(wand);
            }
        }
        if (damageWands.isEmpty()) {
            return null;
        }

        float accuracyMultiplier = owner.blessRollMultiplier(owner);
        float bestPhysicalAccuracy = owner.hasMeleeCombatCapability()
                ? owner.attackSkill(targetMob) * accuracyMultiplier
                : 0f;

        Ballistica shot = new Ballistica(owner.pos, targetMob.pos, Ballistica.PROJECTILE);
        if (shot.collisionPos == targetMob.pos) {
            for (MissileWeapon missile : owner.inventory().missileWeapons()) {
                if (owner.inventory().canUse(missile)) {
                    bestPhysicalAccuracy = Math.max(
                            bestPhysicalAccuracy,
                            owner.attackSkillWith(missile, targetMob) * accuracyMultiplier);
                }
            }

            SpiritBow spiritBow = owner.inventory().spiritBow();
            if (owner.inventory().canUse(spiritBow)) {
                MissileWeapon arrow = spiritBow.knockArrow();
                bestPhysicalAccuracy = Math.max(
                        bestPhysicalAccuracy,
                        owner.attackSkillWith(arrow, targetMob) * accuracyMultiplier);
            }
        }

        float targetEvasion =
                rawDefenseSkill * owner.blessRollMultiplier(targetMob);
        return targetEvasion > bestPhysicalAccuracy
                ? bestDamageWand(damageWands, targetMob)
                : null;
    }

    float bestRangedAverageDamage(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        float best = 0f;
        Ballistica shot = new Ballistica(owner.pos, targetMob.pos, Ballistica.PROJECTILE);
        if (shot.collisionPos == targetMob.pos) {
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

        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.supported(wand)
                    && CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                best = Math.max(best, CoHeroWandAdapter.expectedDamage(wand, owner, targetMob));
            }
        }
        return best;
    }

    private float averageMeleeDamage() {
        MeleeWeapon weapon = owner.weapon();
        if (weapon == null) {
            if (!owner.hasMeleeCombatCapability()) {
                return 0f;
            }
            return (RingOfForce.coHeroUnarmedMinDamage(owner, owner.STR())
                    + RingOfForce.coHeroUnarmedMaxDamage(owner, owner.STR())) / 2f;
        }

        int level = weapon.buffedLvl();
        float average = (weapon.min(level) + weapon.max(level)) / 2f;
        average = weapon.augment.damageFactor(average);
        average += RingOfForce.armedDamageBonus(owner);
        int excessStrength = owner.STR() - weapon.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
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
        boolean preferredMeleeEstablished = owner.canAttack(preferredTarget)
                && !preferredRanged
                && (!owner.isCurrentRangedPressure(preferredTarget)
                    || Dungeon.level.adjacent(owner.pos, preferredTarget.pos));
        if (preferredMeleeEstablished) {
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
            boolean meleeEstablished = owner.canAttack(threat)
                    && !alternateRanged
                    && (!owner.isCurrentRangedPressure(threat)
                        || Dungeon.level.adjacent(owner.pos, threat.pos));
            if (meleeEstablished) {
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
            int aimCell = dpt > 0f ? CoHeroWandAdapter.aimCell(wand, owner, targetMob) : -1;
            if (aimCell >= 0 && dpt > bestDpt + 0.001f) {
                bestDpt = dpt;
                meleeBest = false;
                rangedBest = RangedChoice.wand(wand, aimCell);
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
        ArrayList<MissileWeapon> missiles = new ArrayList<>();
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (owner.inventory().canUse(missile)
                    && new Ballistica(owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
                missiles.add(missile);
            }
        }

        SpiritBow spiritBow = owner.inventory().spiritBow();
        MissileWeapon spiritArrow = null;
        if (owner.inventory().canUse(spiritBow)
                && new Ballistica(owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
            spiritArrow = spiritBow.knockArrow();
        }

        Wand guaranteedControl = null;
        ArrayList<Wand> damageWands = new ArrayList<>();
        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, owner, targetMob)) {
                if (guaranteedControl == null || wand.buffedLvl() > guaranteedControl.buffedLvl()) {
                    guaranteedControl = wand;
                }
            } else if (CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                damageWands.add(wand);
            }
        }

        // A guaranteed corruption/doom conversion is treated as higher-value control than damage.
        if (guaranteedControl != null) {
            return RangedChoice.wand(guaranteedControl, targetMob.pos);
        }

        Wand highEvasionWand = preferredDamageWandForHighEvasion(targetMob);
        if (highEvasionWand != null) {
            return RangedChoice.wand(
                    highEvasionWand,
                    CoHeroWandAdapter.aimCell(highEvasionWand, owner, targetMob));
        }

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
        float bestPhysicalDamage = spiritBowBestPhysical ? spiritBowDamage : bestMissileDamage;

        Wand bestWand = bestDamageWand(damageWands, targetMob);
        float bestWandDamage = bestWand == null
                ? Float.NEGATIVE_INFINITY
                : CoHeroWandAdapter.expectedDamage(bestWand, owner, targetMob);

        // Stable tie-break: preserve wand charges when physical expected damage is equal.
        if (bestPhysicalDamage > Float.NEGATIVE_INFINITY
                && (bestWand == null || bestPhysicalDamage >= bestWandDamage)) {
            return spiritBowBestPhysical
                    ? RangedChoice.spiritBow(spiritBow)
                    : RangedChoice.missile(bestMissile);
        }
        if (bestWand != null) {
            return RangedChoice.wand(
                    bestWand, CoHeroWandAdapter.aimCell(bestWand, owner, targetMob));
        }

        // Control-only wands are fallbacks when no direct ranged damage is currently available.
        Wand fallbackControl = null;
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.fallbackControl(wand, owner, targetMob)
                    && (fallbackControl == null || wand.buffedLvl() > fallbackControl.buffedLvl())) {
                fallbackControl = wand;
            }
        }
        return fallbackControl == null
                ? null
                : RangedChoice.wand(fallbackControl, targetMob.pos);
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
                            (WandOfWarding) candidate, owner, targetMob);
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

    Wand bestDamageWand(ArrayList<Wand> wands, Mob targetMob) {
        Wand best = null;
        float bestDamage = Float.NEGATIVE_INFINITY;
        for (Wand wand : wands) {
            float damage = CoHeroWandAdapter.expectedDamage(wand, owner, targetMob);
            if (best == null || damage > bestDamage) {
                best = wand;
                bestDamage = damage;
            }
        }
        return best;
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
                            || new Ballistica(
                                    owner.pos, threat.pos, Ballistica.PROJECTILE).collisionPos
                                    != threat.pos) {
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
            long animationStarted = System.nanoTime();
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
