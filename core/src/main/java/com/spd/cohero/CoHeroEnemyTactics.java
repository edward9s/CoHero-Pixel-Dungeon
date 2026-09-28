package com.spd.cohero;



import com.shatteredpixel.shatteredpixeldungeon.Dungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredBrute;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Monk;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;

import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;

import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;

import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;

import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;

import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;



import java.util.ArrayList;



/** Enemy-specific combat mechanics. No persistent tactical state is stored here. */

final class CoHeroEnemyTactics {



    private static final float SCORPIO_MAX_RANGED_HEALTH_LOSS = 0.40f;

    private static final float SCORPIO_MIN_POST_FIGHT_HEALTH = 0.50f;



    private final CoHeroAlly owner;

    private final CoHeroCombatController combat;

    private final CoHeroCombatPositioning positioning;



    CoHeroEnemyTactics(

            CoHeroAlly owner,

            CoHeroCombatController combat,

            CoHeroCombatPositioning positioning) {

        this.owner = owner;

        this.combat = combat;

        this.positioning = positioning;

    }



    Boolean tryMonkFocusTactics(
            Mob targetMob,
            ArrayList<Mob> allThreats,
            CoHeroCombatRisk risk) {
        if (!(targetMob instanceof Monk)
                || targetMob.buff(Monk.Focus.class) == null) {
            return null;
        }
        if (allThreats == null || allThreats.isEmpty() || risk == null) {
            throw new IllegalArgumentException(
                    "Monk Focus tactics require current combat threats and risk");
        }

        float effectiveHp = owner.HP + owner.shielding();
        boolean emergency =
                risk.immediateIncoming * 1.35f >= effectiveHp
                || risk.ttd <= 3f
                || risk.attackersNow >= 2;
        if (emergency) {
            // Do not let the one-shot Focus mechanic override genuine survival pressure.
            return null;
        }

        Wand focusBypass = bestMonkFocusBypassWand(targetMob);
        if (focusBypass != null) {
            int aim = CoHeroWandAdapter.aimCell(focusBypass, owner, targetMob);
            if (aim >= 0) {
                owner.logBossDecision("monk_focus_wand:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> bypass Focus with "
                                + focusBypass.getClass().getSimpleName());
                return combat.performWandCast(aim, focusBypass);
            }
        }

        // Focus is a one-use physical parry. Prefer a free attack to consume it before spending
        // ammunition: current melee/reach first, then the reusable Spirit Bow.
        if (owner.canAttack(targetMob)) {
            owner.logBossDecision("monk_focus_melee_break:" + targetMob.id(),
                    owner.targetDebug(targetMob) + " -> consume Focus with melee");
            return combat.performMeleeAttack(targetMob);
        }

        int distance = Dungeon.level.distance(owner.pos, targetMob.pos);
        if (distance > 1
                && new Ballistica(
                        owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                        == targetMob.pos) {
            SpiritBow bow = owner.inventory().spiritBow();
            if (owner.inventory().canUse(bow)) {
                owner.logBossDecision("monk_focus_bow_break:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> consume Focus with Spirit Bow");
                return combat.performSpiritBowAttack(targetMob, bow);
            }

            MissileWeapon cheapest = cheapestMonkFocusBreaker();
            if (cheapest != null) {
                owner.logBossDecision("monk_focus_missile_break:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> consume Focus with "
                                + cheapest.getClass().getSimpleName());
                return combat.performMissileAttack(targetMob, cheapest);
            }
        }

        // If melee is the only answer and one safe step establishes adjacency, close now rather
        // than retreating because the generic TTK sees Focus as infinite evasion.
        if (owner.hasMeleeCombatCapability() && !owner.rooted) {
            int closeStep = positioning.chooseOneStepMeleeApproach(targetMob, allThreats);
            if (closeStep != -1) {
                owner.allowAnyGuardMovement();
                return combat.moveForRangedEngagement(closeStep, "monk_focus_close");
            }
        }

        return null;
    }

    Boolean tryMonkOpeningTactics(Mob targetMob, ArrayList<Mob> allThreats) {
        if (!(targetMob instanceof Monk)
                || targetMob.buff(Monk.Focus.class) != null) {
            return null;
        }
        if (allThreats == null || allThreats.isEmpty()) {
            throw new IllegalArgumentException(
                    "Monk opening tactics require current combat threats");
        }

        // Once Focus is gone, movement helps a Monk rebuild it; Senior rebuilds it especially fast.
        // Use the live opening immediately instead of ordinary spacing/encirclement positioning.
        if (owner.canAttack(targetMob)) {
            owner.logBossDecision("monk_opening_melee:" + targetMob.id(),
                    owner.targetDebug(targetMob) + " -> exploit Focus cooldown");
            return combat.performMeleeAttack(targetMob);
        }

        if (Dungeon.level.distance(owner.pos, targetMob.pos) > 1) {
            Boolean ranged = combat.tryBestRangedAttack(targetMob);
            if (ranged != null) {
                owner.logBossDecision("monk_opening_ranged:" + targetMob.id(),
                        owner.targetDebug(targetMob) + " -> exploit Focus cooldown at range");
                return ranged;
            }
        }

        if (owner.hasMeleeCombatCapability() && !owner.rooted) {
            int closeStep = positioning.chooseOneStepMeleeApproach(targetMob, allThreats);
            if (closeStep != -1) {
                owner.allowAnyGuardMovement();
                return combat.moveForRangedEngagement(closeStep, "monk_opening_close");
            }
        }

        return null;
    }

    private Wand bestMonkFocusBypassWand(Mob targetMob) {
        ArrayList<Wand> candidates = new ArrayList<>();
        for (Wand wand : owner.inventory().wands()) {
            if (wand instanceof WandOfWarding
                    || !CoHeroWandAdapter.supported(wand)
                    || !CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)
                    || !CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                continue;
            }

            int aim = CoHeroWandAdapter.aimCell(wand, owner, targetMob);
            float damage = CoHeroWandAdapter.expectedDamage(wand, owner, targetMob);
            if (aim >= 0 && damage > 0f) {
                candidates.add(wand);
            }
        }
        return combat.bestDamageWand(candidates, targetMob);
    }

    private MissileWeapon cheapestMonkFocusBreaker() {
        MissileWeapon cheapest = null;
        int cheapestValue = Integer.MAX_VALUE;
        float cheapestDamage = Float.POSITIVE_INFINITY;
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (!CoHeroMissileAdapter.supported(missile)
                    || !owner.inventory().canUse(missile)) {
                continue;
            }

            // Focus will force this throw to miss, so preserve upgraded/enchanted/high-tier
            // projectiles before comparing their otherwise irrelevant expected damage.
            int preservationValue = Math.max(0, missile.trueLevel()) * 100
                    + (missile.hasGoodEnchant() ? 50 : 0)
                    + missile.tier * 10;
            float damage = CoHeroMissileAdapter.expectedDamage(owner, missile);
            if (cheapest == null
                    || preservationValue < cheapestValue
                    || (preservationValue == cheapestValue
                        && damage < cheapestDamage - 0.001f)
                    || (preservationValue == cheapestValue
                        && Math.abs(damage - cheapestDamage) <= 0.001f
                        && missile.getClass().getName()
                                .compareTo(cheapest.getClass().getName()) < 0)) {
                cheapest = missile;
                cheapestValue = preservationValue;
                cheapestDamage = damage;
            }
        }
        return cheapest;
    }

    Boolean tryShortBruteRageTactics(ArrayList<Mob> allThreats) {
        if (allThreats == null || allThreats.isEmpty()) {
            throw new IllegalArgumentException(
                    "Short Brute rage tactics require current combat threats");
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
            int invisibleStep = owner.rooted ? -1 : positioning.chooseEscapeStep(allThreats);
            if (invisibleStep != -1) {
                owner.allowAnyGuardMovement();
                return combat.moveForRangedEngagement(invisibleStep, "brute_rage_invisible_escape");
            }
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        int escapeStep = owner.rooted ? -1 : positioning.chooseEscapeStep(allThreats);
        if (escapeStep != -1) {
            owner.allowAnyGuardMovement();
            return combat.moveForRangedEngagement(escapeStep, "brute_rage_escape");
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
            return combat.performMeleeAttack(scorpio);
        }

        boolean canSustainChase = false;
        if (meleeCapable && !owner.rooted) {
            int captureStep = positioning.chooseImmediateScorpioCaptureStep(scorpio, allThreats);
            if (captureStep != -1) {
                owner.allowAnyGuardMovement();
                return combat.moveForRangedEngagement(captureStep, "scorpio_capture");
            }

            canSustainChase = canSustainScorpioChase(scorpio);
            if (canSustainChase) {
                int closeStep = positioning.chooseRangedTargetClosingStep(scorpio, allThreats);
                if (closeStep != -1) {
                    owner.allowAnyGuardMovement();
                    return combat.moveForRangedEngagement(closeStep, "scorpio_chase");
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
            int coverCell = positioning.chooseRangedCoverCell(scorpio, allThreats);
            if (coverCell != -1) {
                int coverStep = positioning.rangedLureStep(coverCell);
                if (coverStep != -1) {
                    owner.allowAnyGuardMovement();
                    return combat.moveForRangedEngagement(coverStep, "scorpio_cover");
                }
            }
        }

        // A reasonable ranged exchange is better than an equal-speed chase that never closes.
        Boolean ranged = combat.tryBestRangedAttack(scorpio);
        if (ranged != null) {
            return ranged;
        }

        // No ranged answer and no useful cover/control remain. Chasing is the least-bad fallback;
        // terrain may still eventually deny the Scorpio another retreat step.
        if (meleeCapable && !owner.rooted) {
            int closeStep = positioning.chooseRangedTargetClosingStep(scorpio, allThreats);
            if (closeStep != -1) {
                owner.allowAnyGuardMovement();
                return combat.moveForRangedEngagement(closeStep, "scorpio_forced_close");
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
                return combat.performWandCast(targetMob.pos, wand);
            }
        }

        // Frost remains a normal damaging wand, but here its speed reduction has tactical value.
        // Only spend the charge when the target is currently too fast to gain on.
        for (Wand wand : owner.inventory().wands()) {
            if (CoHeroWandAdapter.frostUsefulForPursuit(wand, owner, targetMob)) {
                return combat.performWandCast(targetMob.pos, wand);
            }
        }

        return null;
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
        }
        // This is a predictive query made between Scorpio turns, so its cached FOV may describe
        // CoHero's previous position. Refresh it before asking the same flee planner Scorpio uses.
        Dungeon.level.updateFieldOfView(scorpio, scorpioFov);

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

        int tacticalCell = positioning.chooseGreatCrabTacticalCell(targetMob);
        if (tacticalCell == -1) {
            if (owner.canAttack(targetMob) && !targetMob.coHeroSurprisedBy(owner)) {
                int escape = positioning.chooseEscapeStep(threats);
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

}

