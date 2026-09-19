package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

/**
 * Tactical placement planner for CoHero-owned wards.
 *
 * Wards are persistent autonomous attackers, so this planner values safe placement and refuses
 * positions that would immediately bring an unrelated sleeping enemy into the ward's attack range.
 */
final class CoHeroWardingPlanner {

    private CoHeroWardingPlanner() {
    }

    static Plan choose(WandOfWarding wand, CoHeroAlly owner, Mob target) {
        if (wand == null || owner == null || target == null || !wand.coHeroCanZap(owner)) {
            return null;
        }

        Plan best = null;

        // Prefer improving an existing CoHero ward that can already contribute to this fight.
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

            int projectedViewDistance = ward.viewDistance + (ward.tier < 6 ? 1 : 0);
            if (!canEngage(ward.pos, projectedViewDistance, target)
                    || wouldWakeSleepingEnemy(ward.pos, projectedViewDistance, target)) {
                continue;
            }

            Plan candidate = new Plan(
                    ward.pos,
                    expectedNextZapDamage(wand),
                    true,
                    ward.tier,
                    CoHeroHazards.nearbyDangerCount(owner, ward.pos),
                    Dungeon.level.distance(ward.pos, target.pos),
                    Dungeon.level.distance(owner.pos, ward.pos));
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        // A fresh tier-1 ward sees four cells. Search the visible map for a safe firing position.
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

            Plan candidate = new Plan(
                    cell,
                    expectedNextZapDamage(wand),
                    false,
                    0,
                    CoHeroHazards.nearbyDangerCount(owner, cell),
                    Dungeon.level.distance(cell, target.pos),
                    Dungeon.level.distance(owner.pos, cell));
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }

        return best;
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

    static final class Plan {
        final int aimCell;
        final float expectedDamage;

        private final boolean upgradesExisting;
        private final int existingTier;
        private final int nearbyDanger;
        private final int targetDistance;
        private final int ownerDistance;

        private Plan(
                int aimCell,
                float expectedDamage,
                boolean upgradesExisting,
                int existingTier,
                int nearbyDanger,
                int targetDistance,
                int ownerDistance) {
            this.aimCell = aimCell;
            this.expectedDamage = expectedDamage;
            this.upgradesExisting = upgradesExisting;
            this.existingTier = existingTier;
            this.nearbyDanger = nearbyDanger;
            this.targetDistance = targetDistance;
            this.ownerDistance = ownerDistance;
        }

        private boolean betterThan(Plan other) {
            if (upgradesExisting != other.upgradesExisting) {
                return upgradesExisting;
            }
            if (upgradesExisting && existingTier != other.existingTier) {
                return existingTier > other.existingTier;
            }
            if (nearbyDanger != other.nearbyDanger) {
                return nearbyDanger < other.nearbyDanger;
            }
            if (targetDistance != other.targetDistance) {
                return targetDistance > other.targetDistance;
            }
            if (ownerDistance != other.ownerDistance) {
                return ownerDistance < other.ownerDistance;
            }
            return aimCell < other.aimCell;
        }
    }
}
