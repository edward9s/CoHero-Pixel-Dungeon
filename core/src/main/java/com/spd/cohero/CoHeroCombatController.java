package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

final class CoHeroCombatController {

    private final CoHeroAlly owner;

    CoHeroCombatController(CoHeroAlly owner) {
        this.owner = owner;
    }

    Boolean tryDirectRangedAttack(
            Mob preferredTarget, ArrayList<Mob> visibleThreats) {
        if (preferredTarget == null || visibleThreats == null || visibleThreats.isEmpty()) {
            return null;
        }

        // Ordinary melee reach still wins when already established. Against active ranged
        // pressure, only physical adjacency counts as established melee; extended reach must not
        // suppress a legal ranged fallback if closing/cover was impossible owner turn.
        boolean preferredMeleeEstablished = owner.canAttack(preferredTarget)
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

            boolean meleeEstablished = owner.canAttack(threat)
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
        if (owner.canAttack(targetMob)
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

        // If we have a usable combat tool but cannot use it from owner cell, close distance.
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
        if (targetMob == null || owner.isCombatInvulnerable(targetMob)) {
            return null;
        }
        ArrayList<MissileWeapon> missiles = new ArrayList<>();
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (CoHeroAlly.supportedMissileWeapon(missile)
                    && !missile.cursed
                    && new Ballistica(owner.pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
                missiles.add(missile);
            }
        }

        SpiritBow spiritBow = owner.inventory().spiritBow();
        MissileWeapon spiritArrow = null;
        if (spiritBow != null
                && !spiritBow.cursed
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

        if ((!missiles.isEmpty() || spiritArrow != null) && !damageWands.isEmpty()) {
            float bestPhysicalAccuracy = 0f;
            float clericAccuracyMultiplier = owner.blessRollMultiplier(owner);
            for (MissileWeapon missile : missiles) {
                bestPhysicalAccuracy = Math.max(
                        bestPhysicalAccuracy,
                        owner.attackSkillWith(missile, targetMob) * clericAccuracyMultiplier);
            }
            if (spiritArrow != null) {
                bestPhysicalAccuracy = Math.max(
                        bestPhysicalAccuracy,
                        owner.attackSkillWith(spiritArrow, targetMob) * clericAccuracyMultiplier);
            }
            float targetEvasion = targetMob.defenseSkill(owner) * owner.blessRollMultiplier(targetMob);
            if (targetEvasion > bestPhysicalAccuracy) {
                Wand best = bestDamageWand(damageWands, targetMob);
                return RangedChoice.wand(
                        best, CoHeroWandAdapter.aimCell(best, owner, targetMob));
            }
        }

        MissileWeapon bestMissile = null;
        float bestMissileDamage = Float.NEGATIVE_INFINITY;
        for (MissileWeapon missile : missiles) {
            float damage = owner.expectedMissileDamage(missile);
            if (bestMissile == null || damage > bestMissileDamage) {
                bestMissile = missile;
                bestMissileDamage = damage;
            }
        }

        float spiritBowDamage = spiritBow == null || spiritArrow == null
                ? Float.NEGATIVE_INFINITY
                : owner.expectedSpiritBowDamage(spiritBow);
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
                path = null;
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
        if (owner.weapon() != null) {
            return true;
        }
        for (MissileWeapon missile : owner.inventory().missileWeapons()) {
            if (CoHeroAlly.supportedMissileWeapon(missile) && !missile.cursed) {
                return true;
            }
        }
        SpiritBow spiritBow = owner.inventory().spiritBow();
        if (spiritBow != null && !spiritBow.cursed) {
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
        if (heroVisible && owner.sprite() != null && owner.sprite().parent != null && targetMob.sprite != null) {
            ((MissileSprite) owner.sprite().parent.recycle(MissileSprite.class)).reset(
                    owner.sprite(),
                    targetMob.sprite,
                    arrow,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveSpiritBowAttack(targetMob, arrow);
                            owner.spendActionTime(delay);
                            owner.next();
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

        if (heroVisible && owner.sprite() != null && targetMob.sprite != null) {
            owner.sprite().attack(targetMob.pos, new Callback() {
                @Override
                public void call() {
                    owner.attackTarget(targetMob);
                    Invisibility.dispel(owner);
                    owner.spendActionTime(delay);
                    owner.next();
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
        if (heroVisible && owner.sprite() != null && owner.sprite().parent != null && targetMob.sprite != null) {
            ((MissileSprite) owner.sprite().parent.recycle(MissileSprite.class)).reset(
                    owner.sprite(),
                    targetMob.sprite,
                    thrown,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveMissileAttack(targetMob, thrown);
                            owner.spendActionTime(delay);
                            owner.next();
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
        if (heroVisible && owner.sprite() != null && owner.sprite().parent != null) {
            wand.coHeroCast(owner, targetCell, true, new Callback() {
                @Override
                public void call() {
                    owner.next();
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
            owner.missile = missile;
            owner.wand = wand;
            owner.spiritBow = spiritBow;
            owner.wandTargetCell = wandTargetCell;
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
