package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

final class CoHeroCombatController {

    private final CoHeroAlly owner;

    CoHeroCombatController(CoHeroAlly owner) {
        this.owner = owner;
    }

    private static final int MELEE_TACTICAL_SEARCH_RADIUS = 5;
    private static final int RANGED_COVER_SEARCH_RADIUS = 6;
    private static final float RANGED_DAMAGE_PREFERENCE_MULTIPLIER = 1.5f;

    Mob nearestThreat(ArrayList<Mob> threats) {
        Mob result = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            int distance = Dungeon.level.distance(owner.pos, threat.pos);
            if (result == null || distance < bestDistance) {
                result = threat;
                bestDistance = distance;
            }
        }
        return result;
    }

    ArrayList<Mob> collectActiveThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    boolean hasRecoveringCrystalGuardian(ArrayList<Mob> threats) {
        if (threats == null) {
            return false;
        }
        for (Mob threat : threats) {
            if (isTemporarilyInactiveThreat(threat)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTemporarilyInactiveThreat(Mob threat) {
        return threat instanceof CrystalGuardian
                && ((CrystalGuardian) threat).recovering();
    }

    ArrayList<Mob> collectAttackableThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)
                    && !owner.isCombatInvulnerable(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    Boolean tryAvoidInvulnerableThreats(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        ArrayList<Mob> invulnerableThreats = new ArrayList<>();
        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && owner.isCombatInvulnerable(threat)) {
                invulnerableThreats.add(threat);
            }
        }
        if (invulnerableThreats.isEmpty()
                || owner.countCurrentAttackersAtCell(owner.pos, invulnerableThreats) == 0) {
            return null;
        }
        owner.clearCombatTarget();

        owner.logBossDecision("invulnerable_range_retreat",
                "invulnerable enemy can attack current cell -> leave attack range");

        int escapeStep = owner.rooted
                ? -1
                : chooseInvulnerableEscapeStep(invulnerableThreats, threats);
        if (escapeStep != -1) {
            int oldPos = owner.pos;
            owner.allowAnyGuardMovement();
            owner.setMovementDecision("invulnerable_escape", escapeStep);
            owner.move(escapeStep, true);
            owner.spendActionTime(1 / owner.speed());
            Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
            owner.revealVisibleCells();
            return owner.animateMoveFrom(oldPos);
        }

        // No ordinary step improves the invulnerable threat exposure. Escape resources are allowed
        // here even when other damageable enemies are present: staying in an attack range that
        // CoHero cannot answer is the worse failure mode.
        if (owner.controlItems().tryEmergencyBlinkRunestone(invulnerableThreats)) {
            return true;
        }
        if (owner.controlItems().tryUseTeleportationScroll()) {
            return true;
        }
        if (owner.survival().tryUseInvisibilityPotion()) {
            return true;
        }
        if (owner.survival().tryEmergencySurvivalPotion()) {
            return true;
        }

        return null;
    }

    private int chooseInvulnerableEscapeStep(
            ArrayList<Mob> invulnerableThreats, ArrayList<Mob> allThreats) {
        int currentInvulnerableAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, invulnerableThreats);
        float currentInvulnerableIncoming =
                owner.estimatedIncomingDptAtCell(owner.pos, invulnerableThreats);
        int currentAllAttackers = owner.countCurrentAttackersAtCell(owner.pos, allThreats);
        float currentAllIncoming = owner.estimatedIncomingDptAtCell(owner.pos, allThreats);
        int currentDistance = owner.nearestThreatDistance(owner.pos, invulnerableThreats);

        int bestCell = -1;
        int bestInvulnerableAttackers = currentInvulnerableAttackers;
        float bestInvulnerableIncoming = currentInvulnerableIncoming;
        int bestAllAttackers = currentAllAttackers;
        float bestAllIncoming = currentAllIncoming;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int invulnerableAttackers =
                    owner.countCurrentAttackersAtCell(cell, invulnerableThreats);
            float invulnerableIncoming =
                    owner.estimatedIncomingDptAtCell(cell, invulnerableThreats);
            int allAttackers = owner.countCurrentAttackersAtCell(cell, allThreats);
            float allIncoming = owner.estimatedIncomingDptAtCell(cell, allThreats);
            int distance = owner.nearestThreatDistance(cell, invulnerableThreats);

            boolean better =
                    invulnerableAttackers < bestInvulnerableAttackers
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && invulnerableIncoming < bestInvulnerableIncoming - 0.01f)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers < bestAllAttackers)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers == bestAllAttackers
                        && allIncoming < bestAllIncoming - 0.01f)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestInvulnerableAttackers = invulnerableAttackers;
                bestInvulnerableIncoming = invulnerableIncoming;
                bestAllAttackers = allAttackers;
                bestAllIncoming = allIncoming;
                bestDistance = distance;
            }
        }

        return bestCell;
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

        Boolean escapeUtility = tryEscapeUtility(threats);
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

        // No safe movement remains. Controlled Blink is preferred to random teleportation.
        if (owner.controlItems().tryEmergencyBlinkRunestone(threats)) {
            return true;
        }

        if (owner.controlItems().tryUseTeleportationScroll()) {
            return true;
        }

        // Other control runestones remain ahead of consumable fear/invisibility resources.
        if (owner.controlItems().tryEmergencyRunestone(risk, threats)) {
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

            // Ordinary ranged attacks are forbidden while adjacent. If the target cannot be
            // safely kited because it is not slower/immobilized, melee is the legal fallback.
            return null;
        }

        // A pure melee target normally rewards keeping distance, but intentionally melee-focused
        // builds are respected: when melee is at least 1.5x the best legal ranged average damage,
        // start closing instead of spending ranged resources.
        if (!rangedAttacker && shouldCloseForMeleeDamage(targetMob)) {
            if (owner.canAttack(targetMob)) {
                return null;
            }
            int closeStep = chooseOneStepMeleeApproach(targetMob, threats);
            if (closeStep != -1) {
                return moveForRangedEngagement(closeStep, "melee_damage_close");
            }
            // If no safe melee approach exists, do not waste the turn; later combat phases may
            // still use a legal ranged fallback from the current cell.
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
            int closeStep = chooseRangedTargetClosingStep(targetMob, threats);
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

        int chargeStep = chooseOneStepMeleeApproach(targetMob, threats);
        if (chargeStep != -1) {
            return moveForRangedEngagement(chargeStep, "ranged_charge");
        }

        int coverCell = chooseRangedCoverCell(targetMob, threats);
        if (coverCell == -1) {
            return null;
        }

        int step = rangedLureStep(coverCell);
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
            // Pure melee targets cannot answer ranged pressure. Keep that safety advantage unless
            // the build is clearly melee-focused.
            return meleeDamage < rangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        }

        float enemyRangedDamage = owner.averageRangedThreatDamage(targetMob);
        boolean outdamagesEnemy = enemyRangedDamage >= 0f
                && rangedDamage >= enemyRangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        boolean outdamagesMelee =
                rangedDamage >= meleeDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
        return outdamagesEnemy || outdamagesMelee;
    }

    private boolean shouldCloseForMeleeDamage(Mob targetMob) {
        if (targetMob == null
                || owner.hasNonAdjacentAttackCapability(targetMob)
                || preferredDamageWandForHighEvasion(targetMob) != null) {
            return false;
        }
        float rangedDamage = bestRangedAverageDamage(targetMob);
        return rangedDamage > 0f
                && averageMeleeDamage()
                    >= rangedDamage * RANGED_DAMAGE_PREFERENCE_MULTIPLIER;
    }

    private Wand preferredDamageWandForHighEvasion(Mob targetMob) {
        if (targetMob == null
                || targetMob.buff(MagicImmune.class) != null
                || targetMob.coHeroSurprisedBy(owner)) {
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
        float bestPhysicalAccuracy = owner.attackSkill(targetMob) * accuracyMultiplier;

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
                targetMob.defenseSkill(owner) * owner.blessRollMultiplier(targetMob);
        return targetEvasion > bestPhysicalAccuracy
                ? bestDamageWand(damageWands, targetMob)
                : null;
    }

    private float bestRangedAverageDamage(Mob targetMob) {
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

    private int chooseRangedTargetClosingStep(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted || targetMob == null) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        int bestStep = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int destination = targetMob.pos + offset;
            if (!Dungeon.level.insideMap(destination)
                    || Dungeon.level.distance(destination, targetMob.pos) != 1
                    || !passable[destination]
                    || !owner.isMovementSafe(destination)) {
                continue;
            }

            Char occupant = Actor.findChar(destination);
            if (occupant != null && occupant != owner) {
                continue;
            }

            PathFinder.Path route =
                    Dungeon.findPath(owner, destination, passable, owner.fieldOfView, true);
            if (route == null || route.isEmpty()) {
                continue;
            }

            int exposedSteps = 0;
            if (targetMob.fieldOfView != null
                    && targetMob.fieldOfView.length == Dungeon.level.length()) {
                for (int routeCell : route) {
                    if (targetMob.fieldOfView[routeCell]) {
                        exposedSteps++;
                    }
                }
            }

            int attackers = owner.countCurrentAttackersAtCell(destination, threats);
            int score = route.size() * 24
                    + exposedSteps * 18
                    + attackers * 90;

            int firstStep = route.getFirst();
            if (bestStep == -1
                    || score < bestScore
                    || (score == bestScore && firstStep < bestStep)) {
                bestStep = firstStep;
                bestScore = score;
            }
        }

        return bestStep;
    }

    private int chooseOneStepMeleeApproach(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted || targetMob == null) {
            return -1;
        }

        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)
                    || (!owner.fieldOfView[cell] && !owner.isKnown(cell))
                    || Actor.findChar(cell) != null
                    || !Dungeon.level.adjacent(cell, targetMob.pos)) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            int distance = Dungeon.level.distance(cell, targetMob.pos);

            if (best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance < bestDistance)) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
                bestDistance = distance;
            }
        }

        return best;
    }

    int chooseRangedCoverCell(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        PathFinder.buildDistanceMap(owner.pos, passable);

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
                    || PathFinder.distance[cell] == Integer.MAX_VALUE
                    || PathFinder.distance[cell] > RANGED_COVER_SEARCH_RADIUS
                    || !isRangedCoverCell(cell, targetMob)) {
                continue;
            }
            candidates.add(cell);
        }

        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int cell : candidates) {
            PathFinder.Path route =
                    Dungeon.findPath(owner, cell, passable, owner.fieldOfView, true);
            if (route == null
                    || route.isEmpty()
                    || route.size() > RANGED_COVER_SEARCH_RADIUS) {
                continue;
            }

            int exposedSteps = 0;
            for (int routeCell : route) {
                if (targetMob.fieldOfView[routeCell]) {
                    exposedSteps++;
                }
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            // Reaching cover quickly matters most. Remaining exposed to the shooter while moving
            // and choosing cover that is still attackable by other threats are both expensive.
            int score = route.size() * 24
                    + exposedSteps * 80
                    + attackers * 120
                    + targetDistance * 4;

            if (best == -1 || score < bestScore || (score == bestScore && cell < best)) {
                best = cell;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isRangedCoverCell(int cell, Mob targetMob) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()
                || !Dungeon.level.insideMap(cell)
                || !Dungeon.level.passable[cell]
                || !owner.isKnown(cell)
                || !owner.isMovementSafe(cell)
                || targetMob.fieldOfView[cell]) {
            return false;
        }

        if (!owner.fieldOfView[cell]) {
            return true;
        }

        Char occupant = Actor.findChar(cell);
        return occupant == null || occupant == owner;
    }

    private boolean[] rangedLurePassable() {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell == owner.pos) {
                result[cell] = true;
                continue;
            }

            if (!result[cell] || !owner.isKnown(cell) || !owner.isMovementSafe(cell)) {
                result[cell] = false;
                continue;
            }

            // Only use currently visible occupancy information. Do not inspect actors hidden
            // behind cover merely to improve pathfinding.
            if (owner.fieldOfView[cell]) {
                Char occupant = Actor.findChar(cell);
                if (occupant != null && occupant != owner) {
                    result[cell] = false;
                }
            }
        }
        return result;
    }

    private int rangedLureStep(int destination) {
        if (owner.rooted || destination == owner.pos || !Dungeon.level.insideMap(destination)) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        int step = Dungeon.findStep(owner, destination, passable, owner.fieldOfView, true);
        return step != -1 && owner.isMovementSafe(step) ? step : -1;
    }

    private Boolean moveForRangedEngagement(int step, String decision) {
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

    Boolean tryMeleePositioning(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.weapon() == null || targetMob == null || threats == null || threats.isEmpty()) {
            return null;
        }

        boolean greatCrab = targetMob instanceof GreatCrab;
        boolean swarmPressure = false;
        for (Mob threat : threats) {
            if (threat instanceof Swarm) {
                swarmPressure = true;
                break;
            }
        }

        boolean crowdedMelee = threats.size() >= 2 && !owner.hasRangedPressure(threats);
        if (!greatCrab && !swarmPressure && !crowdedMelee) {
            return null;
        }

        if (greatCrab
                && targetMob.coHeroSurprisedBy(owner)
                && owner.canAttack(targetMob)) {
            return null;
        }

        if (!greatCrab && meleeFrontage(owner.pos) <= 2) {
            return null;
        }

        int tacticalCell = chooseMeleeTacticalCell(targetMob, greatCrab);
        if (tacticalCell == -1) {
            if (greatCrab
                    && owner.canAttack(targetMob)
                    && !targetMob.coHeroSurprisedBy(owner)) {
                int escape = chooseEscapeStep(threats);
                if (escape != -1) {
                    int oldPos = owner.pos;
                    owner.setMovementDecision("melee_tactical_escape", escape);
                    owner.move(escape, true);
                    owner.spendActionTime(1 / owner.speed());
                    return owner.animateMoveFrom(oldPos);
                }
            }
            return null;
        }

        if (owner.pos != tacticalCell) {
            int oldPos = owner.pos;
            owner.setMovementDecision("melee_positioning", tacticalCell);
            if (owner.getCloser(tacticalCell)) {
                owner.spendActionTime(1 / owner.speed());
                Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                owner.revealVisibleCells();
                return owner.animateMoveFrom(oldPos);
            }
            return null;
        }

        if (owner.canAttack(targetMob)
                && (!greatCrab || targetMob.coHeroSurprisedBy(owner))) {
            return null;
        }

        if (!owner.anyThreatCanAttackNow(threats)) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        return null;
    }

    private int chooseMeleeTacticalCell(Mob targetMob, boolean greatCrab) {
        PathFinder.buildDistanceMap(
                owner.pos, Dungeon.level.passable, MELEE_TACTICAL_SEARCH_RADIUS);

        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (pathDistance == Integer.MAX_VALUE
                    || pathDistance > MELEE_TACTICAL_SEARCH_RADIUS
                    || !owner.fieldOfView[cell]
                    || !owner.isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int frontage = meleeFrontage(cell);
            if (frontage < 2) {
                continue;
            }

            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            if (greatCrab) {
                if (targetMob.fieldOfView == null
                        || targetMob.fieldOfView.length != Dungeon.level.length()
                        || targetMob.fieldOfView[cell]
                        || targetDistance < 2
                        || targetDistance > MELEE_TACTICAL_SEARCH_RADIUS) {
                    continue;
                }
            } else if (frontage > 3) {
                continue;
            }

            int score = pathDistance * 12 + frontage * 40;
            if (greatCrab) {
                score += Math.abs(targetDistance - 3) * 10;
            } else {
                score += Math.abs(targetDistance - 2) * 4;
                if (frontage == 2) {
                    score -= 80;
                }
            }

            if (best == -1 || score < bestScore || (score == bestScore && cell < best)) {
                best = cell;
                bestScore = score;
            }
        }

        return best;
    }

    private int meleeFrontage(int cell) {
        int result = 0;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent >= 0
                    && adjacent < Dungeon.level.length()
                    && Dungeon.level.distance(cell, adjacent) == 1
                    && Dungeon.level.passable[adjacent]) {
                result++;
            }
        }
        return result;
    }

    int chooseEscapeStep(ArrayList<Mob> threats) {
        float currentIncoming = owner.estimatedIncomingDptAtCell(owner.pos, threats);
        int currentAttackers = owner.countCurrentAttackersAtCell(owner.pos, threats);
        int currentDistance = owner.nearestThreatDistance(owner.pos, threats);

        int bestCell = -1;
        float bestIncoming = currentIncoming;
        int bestAttackers = currentAttackers;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            int distance = owner.nearestThreatDistance(cell, threats);

            boolean better = attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestIncoming = incoming;
                bestAttackers = attackers;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    Boolean tryDirectRangedAttack(
            Mob preferredTarget, ArrayList<Mob> visibleThreats) {
        if (preferredTarget == null || visibleThreats == null || visibleThreats.isEmpty()) {
            return null;
        }

        // Direct ranged attacks require at least one empty tile of spacing.
        if (Dungeon.level.distance(owner.pos, preferredTarget.pos) <= 1) {
            return null;
        }

        // Ordinary melee reach still wins when already established, except when the target-level
        // strategy explicitly prefers ranged combat (including melee-target kiting or wand use).
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

            int distance = Dungeon.level.distance(owner.pos, threat.pos);
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

    Boolean tryCombat(Mob targetMob) {
        if (targetMob == null || owner.isCombatInvulnerable(targetMob)) {
            return null;
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

    private Wand bestDamageWand(ArrayList<Wand> wands, Mob targetMob) {
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

    Boolean tryEscapeUtility(ArrayList<Mob> visibleThreats) {
        // Blast Wave is a survival tool when a safe blast reduces next-turn attackers,
        // even when another wand would deal more raw damage.
        for (Wand wand : owner.inventory().wands()) {
            int blastAim = CoHeroWandAdapter.blastWaveEscapeAim(wand, owner, visibleThreats);
            if (blastAim != -1) {
                return performWandCast(blastAim, wand);
            }
        }

        for (Mob threat : visibleThreats) {
            for (Wand wand : owner.inventory().wands()) {
                if (CoHeroWandAdapter.regrowthUsefulForEscape(wand, owner, threat, visibleThreats)) {
                    return performWandCast(threat.pos, wand);
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

    private boolean performSpiritBowAttack(Mob targetMob, SpiritBow bow) {
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

    private boolean performMeleeAttack(Mob targetMob) {
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

    private boolean performMissileAttack(Mob targetMob, MissileWeapon source) {
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

    private boolean performWandCast(int targetCell, Wand wand) {
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
