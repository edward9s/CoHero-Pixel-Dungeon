package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredBrute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
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
import java.util.Arrays;

final class CoHeroCombatController {

    private final CoHeroAlly owner;

    CoHeroCombatController(CoHeroAlly owner) {
        this.owner = owner;
    }

    private static final int ENCIRCLEMENT_SEARCH_RADIUS = 5;
    private static final int CHOKE_REAR_SCAN_RADIUS = 6;
    private static final int GREAT_CRAB_TACTICAL_SEARCH_RADIUS = 5;
    private static final int RANGED_COVER_SEARCH_RADIUS = 6;
    private static final float RANGED_DAMAGE_PREFERENCE_MULTIPLIER = 1.5f;
    private static final float SCORPIO_MAX_RANGED_HEALTH_LOSS = 0.40f;
    private static final float SCORPIO_MIN_POST_FIGHT_HEALTH = 0.50f;

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
                    && !owner.isCombatInvulnerable(threat)
                    && !owner.isCharmedBy(threat)) {
                result.add(threat);
            }
        }
        return result;
    }


    ArrayList<Mob> collectCharmingThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)
                    && owner.isCharmedBy(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    Boolean tryAvoidCharmingThreats(
            ArrayList<Mob> charmingThreats, ArrayList<Mob> allThreats) {
        if (charmingThreats == null || charmingThreats.isEmpty()) {
            return null;
        }
        if (allThreats == null || allThreats.isEmpty()) {
            throw new IllegalArgumentException(
                    "Charm avoidance requires the current visible threat set");
        }

        int escapeStep = owner.rooted
                ? -1
                : chooseCharmedEscapeStep(charmingThreats, allThreats);
        if (escapeStep == -1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.clearCombatTarget();
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("charm_escape", escapeStep);
        owner.move(escapeStep, true);
        owner.spendActionTime(1 / owner.speed());
        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
        owner.revealVisibleCells();
        return owner.animateMoveFrom(oldPos);
    }

    private int chooseCharmedEscapeStep(
            ArrayList<Mob> charmingThreats, ArrayList<Mob> allThreats) {
        int bestCell = -1;
        int bestCharmerAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, charmingThreats);
        int bestAllAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, allThreats);
        float bestAllIncoming =
                owner.estimatedIncomingDptAtCell(owner.pos, allThreats);
        int bestVisibleCharmers =
                charmersSeeingCell(owner.pos, charmingThreats);
        int bestDistance =
                owner.nearestThreatDistance(owner.pos, charmingThreats);

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int charmerAttackers =
                    owner.countCurrentAttackersAtCell(cell, charmingThreats);
            int allAttackers =
                    owner.countCurrentAttackersAtCell(cell, allThreats);
            float allIncoming =
                    owner.estimatedIncomingDptAtCell(cell, allThreats);
            int visibleCharmers =
                    charmersSeeingCell(cell, charmingThreats);
            int distance =
                    owner.nearestThreatDistance(cell, charmingThreats);

            boolean better = charmerAttackers < bestCharmerAttackers
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers < bestAllAttackers)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && allIncoming < bestAllIncoming - 0.01f)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && visibleCharmers < bestVisibleCharmers)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && visibleCharmers == bestVisibleCharmers
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestCharmerAttackers = charmerAttackers;
                bestAllAttackers = allAttackers;
                bestAllIncoming = allIncoming;
                bestVisibleCharmers = visibleCharmers;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    private int charmersSeeingCell(int cell, ArrayList<Mob> charmingThreats) {
        int result = 0;
        for (Mob charmer : charmingThreats) {
            boolean[] fov = charmer.fieldOfView;
            if (fov == null || fov.length != Dungeon.level.length()) {
                fov = new boolean[Dungeon.level.length()];
                Dungeon.level.updateFieldOfView(charmer, fov);
            }
            if (fov[cell]) {
                result++;
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

    Boolean tryShortBruteRageTactics(
            ArrayList<Mob> allThreats,
            CoHeroCombatRisk risk) {
        if (allThreats == null || allThreats.isEmpty() || risk == null) {
            throw new IllegalArgumentException(
                    "Short Brute rage tactics require current combat threats and risk");
        }

        Mob shortRageThreat = nearestShortBruteRageThreat(allThreats);
        return shortRageThreat == null
                ? null
                : tryShortBruteRageSurvival(shortRageThreat, allThreats);
    }

    Boolean tryArmoredBruteRageTactics(
            Mob targetMob,
            ArrayList<Mob> allThreats,
            CoHeroCombatRisk risk) {
        if (targetMob == null
                || allThreats == null
                || allThreats.isEmpty()
                || risk == null) {
            throw new IllegalArgumentException(
                    "Armored Brute rage tactics require a target, threats, and risk");
        }

        Brute.BruteRage targetRage = activeBruteRage(targetMob);
        if (!(targetRage instanceof ArmoredBrute.ArmoredRage)) {
            return null;
        }
        return tryArmoredBruteRageCombat(targetMob, allThreats, risk);
    }

    private Mob nearestShortBruteRageThreat(ArrayList<Mob> threats) {
        Mob best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            Brute.BruteRage rage = activeBruteRage(threat);
            if (rage == null || rage instanceof ArmoredBrute.ArmoredRage) {
                continue;
            }

            int distance = Dungeon.level.distance(owner.pos, threat.pos);
            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && threat.id() < best.id())) {
                best = threat;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Brute.BruteRage activeBruteRage(Mob mob) {
        if (!(mob instanceof Brute)) {
            return null;
        }

        Brute.BruteRage result = null;
        for (Brute.BruteRage rage : mob.buffs(Brute.BruteRage.class)) {
            if (rage.shielding() <= 0) {
                continue;
            }
            if (result != null) {
                throw new IllegalStateException(
                        "Brute has multiple active rage shields: "
                                + mob.getClass().getSimpleName());
            }
            result = rage;
        }
        return result;
    }

    private Boolean tryShortBruteRageSurvival(
            Mob brute, ArrayList<Mob> allThreats) {
        // Ordinary BruteRage is a short self-destruct phase. Spending health to break the shield
        // is usually worse than surviving until its automatic shield decay kills the Brute.
        if (owner.buff(Invisibility.class) != null) {
            int invisibleStep = owner.rooted ? -1 : chooseEscapeStep(allThreats);
            if (invisibleStep != -1) {
                owner.allowAnyGuardMovement();
                return moveForRangedEngagement(invisibleStep, "brute_rage_invisible_escape");
            }
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        int escapeStep = owner.rooted ? -1 : chooseEscapeStep(allThreats);
        if (escapeStep != -1) {
            owner.allowAnyGuardMovement();
            return moveForRangedEngagement(escapeStep, "brute_rage_escape");
        }

        float attackTime = owner.estimatedTimeToAttackCell(brute, owner.pos);
        CoHeroThreatTiming currentTiming =
                owner.assessThreatTimingAtCell(owner.pos, allThreats, Actor.TICK);
        if (attackTime > Actor.TICK + 0.001f
                && currentTiming.attackersWithinHorizon == 0) {
            // Already safe enough for the next turn from every visible threat: wait for the rage
            // shield to decay instead of spending ammunition, wand charges, or movement to re-engage.
            owner.clearCombatTarget();
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        // Movement/waiting cannot safely solve the turn. Hand control back to the ordinary
        // survival layer, which already owns consumable control, teleport, shielding, and healing.
        return null;
    }

    private Boolean tryArmoredBruteRageCombat(
            Mob brute, ArrayList<Mob> allThreats, CoHeroCombatRisk risk) {
        // With no damaging ranged option, creating distance only forces a melee-only CoHero to
        // close it again later. In that case leave the fight to the normal melee/risk logic.
        if (bestRangedAverageDamage(brute) <= 0f) {
            return null;
        }

        if (Dungeon.level.distance(owner.pos, brute.pos) > 1) {
            RangedChoice ranged = chooseRangedAttack(brute);
            if (ranged != null) {
                return performRangedChoice(brute, ranged);
            }
            return null;
        }

        // ArmoredRage lasts far too long to wait out. When already in melee range, first try to
        // create a genuinely better position; speed-aware escape planning rejects fake +1 spacing.
        if (!owner.rooted) {
            int escapeStep = chooseEscapeStep(allThreats);
            if (escapeStep != -1) {
                owner.allowAnyGuardMovement();
                return moveForRangedEngagement(escapeStep, "armored_brute_rage_spacing");
            }
        }

        // Renewable displacement/rooting is worthwhile here because it creates time to damage a
        // long-lived rage shield from range. If none exists, fall through to ordinary combat.
        Boolean escapeUtility = tryEscapeUtility(risk, allThreats);
        if (escapeUtility != null) {
            return escapeUtility;
        }

        return null;
    }

    Boolean tryScorpioTactics(
            Mob targetMob,
            ArrayList<Mob> allThreats,
            CoHeroCombatRisk risk) {
        if (!(targetMob instanceof Scorpio)) {
            return null;
        }
        if (allThreats == null
                || allThreats.isEmpty()
                || risk == null) {
            throw new IllegalArgumentException(
                    "Scorpio tactics require current combat threats and risk");
        }

        Scorpio scorpio = (Scorpio) targetMob;
        boolean meleeCapable = owner.hasMeleeCombatCapability();

        // Adjacency is the actual safe range against a Scorpio. Extended-reach melee at distance
        // two still leaves CoHero exposed to the Scorpio's ranged attack.
        if (meleeCapable
                && Dungeon.level.adjacent(owner.pos, scorpio.pos)
                && owner.canAttack(scorpio)) {
            return performMeleeAttack(scorpio);
        }

        boolean canSustainChase = false;
        if (meleeCapable && !owner.rooted) {
            int captureStep = chooseImmediateScorpioCaptureStep(scorpio, allThreats);
            if (captureStep != -1) {
                owner.allowAnyGuardMovement();
                return moveForRangedEngagement(captureStep, "scorpio_capture");
            }

            canSustainChase = canSustainScorpioChase(scorpio);
            if (canSustainChase) {
                int closeStep = chooseRangedTargetClosingStep(scorpio, allThreats);
                if (closeStep != -1) {
                    owner.allowAnyGuardMovement();
                    return moveForRangedEngagement(closeStep, "scorpio_chase");
                }
            }
        }

        float rangedDpt = owner.estimateBestRangedDpt(scorpio);
        boolean costlyExchange = isCostlyScorpioRangedExchange(scorpio, allThreats, rangedDpt);

        // When an ordinary chase cannot gain ground and a ranged duel is expensive, first try a
        // renewable pursuit control. Regrowth roots the Scorpio in place; Frost slows it enough
        // to turn an equal-speed chase into a real closing opportunity.
        if (meleeCapable
                && !owner.rooted
                && !canSustainChase
                && (costlyExchange || rangedDpt <= 0.01f)) {
            Boolean pursuitControl = tryPursuitControl(scorpio, allThreats);
            if (pursuitControl != null) {
                return pursuitControl;
            }
        }

        // If CoHero still cannot realistically catch the Scorpio, do not stand in the open and pay
        // a large health bill just because the TTD calculation says the duel is technically winnable.
        if (costlyExchange || rangedDpt <= 0.01f) {
            int coverCell = chooseRangedCoverCell(scorpio, allThreats);
            if (coverCell != -1) {
                int coverStep = rangedLureStep(coverCell);
                if (coverStep != -1) {
                    owner.allowAnyGuardMovement();
                    return moveForRangedEngagement(coverStep, "scorpio_cover");
                }
            }
        }

        // A reasonable ranged exchange is better than an equal-speed chase that never closes.
        RangedChoice ranged = chooseRangedAttack(scorpio);
        if (ranged != null) {
            return performRangedChoice(scorpio, ranged);
        }

        // No ranged answer and no useful cover/control remain. Chasing is the least-bad fallback;
        // terrain may still eventually deny the Scorpio another retreat step.
        if (meleeCapable && !owner.rooted) {
            int closeStep = chooseRangedTargetClosingStep(scorpio, allThreats);
            if (closeStep != -1) {
                owner.allowAnyGuardMovement();
                return moveForRangedEngagement(closeStep, "scorpio_forced_close");
            }
        }

        return null;
    }

    private Boolean tryPursuitControl(Mob targetMob, ArrayList<Mob> visibleThreats) {
        if (targetMob == null
                || !targetMob.isAlive()
                || targetMob.rooted
                || targetMob.paralysed > 0) {
            return null;
        }

        // Rooting is preferred: it keeps the target awake and in combat while removing its ability
        // to kite. The shared adapter rejects casts that would root Hero/CoHero/neutral actors.
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.regrowthCanSafelyRoot(
                    wand, owner, targetMob, visibleThreats)) {
                return performWandCast(targetMob.pos, wand);
            }
        }

        // Frost remains a normal damaging wand, but here its speed reduction has tactical value.
        // Only spend the charge when the target is currently too fast to gain on.
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.frostUsefulForPursuit(wand, owner, targetMob)) {
                return performWandCast(targetMob.pos, wand);
            }
        }

        return null;
    }

    private int chooseImmediateScorpioCaptureStep(
            Scorpio scorpio, ArrayList<Mob> threats) {
        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.adjacent(cell, scorpio.pos)
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            if (best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && cell < best)) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
            }
        }

        return best;
    }

    private boolean canSustainScorpioChase(Scorpio scorpio) {
        if (scorpio.rooted || scorpio.paralysed > 0) {
            return true;
        }
        if (owner.speed() > scorpio.speed() + 0.001f) {
            return true;
        }

        boolean[] scorpioFov = scorpio.fieldOfView;
        if (scorpioFov == null || scorpioFov.length != Dungeon.level.length()) {
            scorpioFov = new boolean[Dungeon.level.length()];
            Dungeon.level.updateFieldOfView(scorpio, scorpioFov);
        }

        // Scorpio.getCloser() delegates to Dungeon.flee() while hunting. If that same flee planner
        // has no legal retreat step, terrain has actually denied the Scorpio room to kite.
        return Dungeon.flee(
                scorpio,
                owner.pos,
                Dungeon.level.passable,
                scorpioFov,
                true) == -1;
    }

    private boolean isCostlyScorpioRangedExchange(
            Scorpio scorpio, ArrayList<Mob> threats, float rangedDpt) {
        if (rangedDpt <= 0.01f) {
            return true;
        }

        float incomingDpt = owner.estimatedIncomingDptAtCell(owner.pos, threats);
        if (incomingDpt <= 0.01f) {
            return false;
        }

        float scorpioEffectiveHp = Math.max(0, scorpio.HP) + Math.max(0, scorpio.shielding());
        float turnsToKill = Math.max(0.25f, scorpioEffectiveHp / rangedDpt);
        float projectedLoss = incomingDpt * turnsToKill;
        float effectiveHp = owner.HP + owner.shielding();
        float projectedRemaining = effectiveHp - projectedLoss;

        return projectedLoss >= effectiveHp * SCORPIO_MAX_RANGED_HEALTH_LOSS
                || projectedRemaining <= owner.HT * SCORPIO_MIN_POST_FIGHT_HEALTH;
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

    Boolean tryEncirclementPositioning(
            Mob targetMob, ArrayList<Mob> threats, CoHeroCombatRisk risk) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || risk == null) {
            return null;
        }

        // Swarm is the only single-enemy special case here. Convert melee DPT back to
        // expected damage per swing before applying Swarm.defenseProc's HP >= damage + 2 split
        // threshold. Ranged estimates are already expressed per offensive action.
        float expectedNextDamage = owner.canAttack(targetMob)
                ? risk.outgoingDpt * Math.max(0.25f, owner.attackDelay())
                : risk.outgoingDpt;
        boolean swarmSplitPressure =
                targetMob instanceof Swarm && targetMob.HP >= expectedNextDamage + 2f;
        ArrayList<Mob> meleeThreats = collectEncirclementMeleeThreats(threats);
        boolean crowdedMelee = meleeThreats.size() >= 2;
        if (!swarmSplitPressure && !crowdedMelee) {
            return null;
        }

        // Ranged pressure does not disable anti-encirclement positioning. Melee threats define
        // whether a choke actually limits frontage; every threat still contributes to incoming
        // DPT when choosing between otherwise valid positions.
        int tacticalCell = chooseEncirclementCell(targetMob, meleeThreats, threats);
        if (tacticalCell != -1 && tacticalCell != owner.pos) {
            int oldPos = owner.pos;
            owner.allowAnyGuardMovement();
            owner.setMovementDecision("encirclement_positioning", tacticalCell);
            if (owner.getCloser(tacticalCell)) {
                owner.spendActionTime(1 / owner.speed());
                Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                owner.revealVisibleCells();
                return owner.animateMoveFrom(oldPos);
            }
        } else if (tacticalCell == owner.pos) {
            return null;
        }

        // With several melee threats and no usable choke nearby, prefer a step that already
        // improves current exposure against the whole threat set, including ranged enemies.
        // chooseEscapeStep refuses neutral/worse moves, so this does not make CoHero run forever
        // from a lone swarm in an open room.
        if (crowdedMelee && !owner.rooted) {
            int escape = chooseEscapeStep(threats);
            if (escape != -1) {
                int oldPos = owner.pos;
                owner.allowAnyGuardMovement();
                owner.setMovementDecision("encirclement_escape", escape);
                owner.move(escape, true);
                if (owner.pos != oldPos) {
                    owner.spendActionTime(1 / owner.speed());
                    Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                    owner.revealVisibleCells();
                    return owner.animateMoveFrom(oldPos);
                }
            }
        }

        return null;
    }

    private ArrayList<Mob> collectEncirclementMeleeThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        for (Mob threat : threats) {
            if (threat == null
                    || !threat.isAlive()
                    || owner.isCombatInvulnerable(threat)
                    || owner.hasNonAdjacentAttackCapability(threat)) {
                continue;
            }
            result.add(threat);
        }
        return result;
    }

    private int chooseEncirclementCell(
            Mob targetMob, ArrayList<Mob> meleeThreats, ArrayList<Mob> allThreats) {
        if (meleeThreats.isEmpty()) {
            return -1;
        }

        PathFinder.buildDistanceMap(
                owner.pos, Dungeon.level.passable, ENCIRCLEMENT_SEARCH_RADIUS);

        int best = -1;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestPathDistance = Integer.MAX_VALUE;
        int bestTargetDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (pathDistance == Integer.MAX_VALUE
                    || pathDistance > ENCIRCLEMENT_SEARCH_RADIUS
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
            if (!isDefensibleChoke(cell, meleeThreats)) {
                continue;
            }

            float incoming = owner.estimatedIncomingDptAtCell(cell, allThreats);
            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            boolean better = best == -1
                    || incoming < bestIncoming - 0.01f
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance < bestPathDistance)
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance == bestPathDistance
                        && targetDistance < bestTargetDistance)
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance == bestPathDistance
                        && targetDistance == bestTargetDistance
                        && cell < best);
            if (better) {
                best = cell;
                bestIncoming = incoming;
                bestPathDistance = pathDistance;
                bestTargetDistance = targetDistance;
            }
        }

        return best;
    }

    private boolean isDefensibleChoke(int cell, ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return false;
        }

        int[] exits = new int[2];
        int exitCount = 0;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent < 0
                    || adjacent >= Dungeon.level.length()
                    || Dungeon.level.distance(cell, adjacent) != 1
                    || !Dungeon.level.passable[adjacent]) {
                continue;
            }
            if (exitCount == exits.length) {
                return false;
            }
            exits[exitCount++] = adjacent;
        }
        if (exitCount != 2) {
            return false;
        }

        // If multiple enemies can already attack this cell, its geometry is not protecting us.
        if (owner.countCurrentAttackersAtCell(cell, threats) > 1) {
            return false;
        }

        int[] side0Distance =
                localPathDistances(exits[0], cell, CHOKE_REAR_SCAN_RADIUS);
        int[] side1Distance =
                localPathDistances(exits[1], cell, CHOKE_REAR_SCAN_RADIUS);

        boolean pressure0 = false;
        boolean pressure1 = false;
        for (Mob threat : threats) {
            if (!Dungeon.level.insideMap(threat.pos)) {
                continue;
            }
            boolean side0 = side0Distance[threat.pos] >= 0;
            boolean side1 = side1Distance[threat.pos] >= 0;

            // Both exits are locally reachable without crossing the candidate cell: enemies can
            // flank this position in the near term, so it is not a real defensive choke.
            if (side0 && side1) {
                return false;
            }
            pressure0 |= side0;
            pressure1 |= side1;
        }

        if (pressure0 == pressure1) {
            return false;
        }

        int rear = pressure0 ? exits[1] : exits[0];
        return owner.isKnown(rear)
                && owner.isMovementSafe(rear)
                && Actor.findChar(rear) == null;
    }

    private int[] localPathDistances(int start, int blockedCell, int maxDistance) {
        int[] distance = new int[Dungeon.level.length()];
        Arrays.fill(distance, -1);
        if (!Dungeon.level.insideMap(start) || start == blockedCell) {
            return distance;
        }

        int[] queue = new int[Dungeon.level.length()];
        int head = 0;
        int tail = 0;
        distance[start] = 0;
        queue[tail++] = start;

        while (head < tail) {
            int current = queue[head++];
            int nextDistance = distance[current] + 1;
            if (nextDistance > maxDistance) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = current + offset;
                if (!Dungeon.level.insideMap(next)
                        || next == blockedCell
                        || Dungeon.level.distance(current, next) != 1
                        || distance[next] != -1
                        || !Dungeon.level.passable[next]) {
                    continue;
                }
                distance[next] = nextDistance;
                queue[tail++] = next;
            }
        }

        return distance;
    }

    Boolean tryMeleePositioning(Mob targetMob, ArrayList<Mob> threats) {
        if (!owner.hasMeleeCombatCapability()
                || !(targetMob instanceof GreatCrab)
                || threats == null
                || threats.isEmpty()) {
            return null;
        }

        if (targetMob.coHeroSurprisedBy(owner) && owner.canAttack(targetMob)) {
            return null;
        }

        int tacticalCell = chooseGreatCrabTacticalCell(targetMob);
        if (tacticalCell == -1) {
            if (owner.canAttack(targetMob) && !targetMob.coHeroSurprisedBy(owner)) {
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

        if (owner.canAttack(targetMob) && targetMob.coHeroSurprisedBy(owner)) {
            return null;
        }

        if (!owner.anyThreatCanAttackNow(threats)) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        return null;
    }

    private int chooseGreatCrabTacticalCell(Mob targetMob) {
        PathFinder.buildDistanceMap(
                owner.pos, Dungeon.level.passable, GREAT_CRAB_TACTICAL_SEARCH_RADIUS);

        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (pathDistance == Integer.MAX_VALUE
                    || pathDistance > GREAT_CRAB_TACTICAL_SEARCH_RADIUS
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

            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);
            if (targetMob.fieldOfView == null
                    || targetMob.fieldOfView.length != Dungeon.level.length()
                    || targetMob.fieldOfView[cell]
                    || targetDistance < 2
                    || targetDistance > GREAT_CRAB_TACTICAL_SEARCH_RADIUS) {
                continue;
            }

            int frontage = meleeFrontage(cell);
            if (frontage < 2) {
                continue;
            }

            int score = pathDistance * 12
                    + frontage * 40
                    + Math.abs(targetDistance - 3) * 10;
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
        float moveTime = Math.max(0.25f, 1f / owner.speed());
        CoHeroThreatTiming current =
                owner.assessThreatTimingAtCell(owner.pos, threats, moveTime);

        int bestCell = -1;
        CoHeroThreatTiming best = current;

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

            CoHeroThreatTiming candidate =
                    owner.assessThreatTimingAtCell(cell, threats, moveTime);

            boolean gainsBreathingRoom =
                    best.nearestAttackTime <= moveTime + 0.001f
                    && candidate.nearestAttackTime > moveTime + 0.001f;
            boolean extendsExistingWindow =
                    best.nearestAttackTime > moveTime + 0.001f
                    && candidate.nearestAttackTime > best.nearestAttackTime + 0.01f;

            boolean better =
                    candidate.attackersWithinHorizon < best.attackersWithinHorizon
                    || (candidate.attackersWithinHorizon == best.attackersWithinHorizon
                        && candidate.incomingDptWithinHorizon
                                < best.incomingDptWithinHorizon - 0.01f)
                    || (candidate.attackersWithinHorizon == best.attackersWithinHorizon
                        && Math.abs(
                                candidate.incomingDptWithinHorizon
                                        - best.incomingDptWithinHorizon) <= 0.01f
                        && (gainsBreathingRoom || extendsExistingWindow));

            if (better) {
                bestCell = cell;
                best = candidate;
            }
        }

        return bestCell;
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
