package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.utils.PathFinder;

/**
 * Owns Hero-follow support and the low-health rally state.
 */
final class CoHeroSupportController {

    private static final int LOW_HEALTH_ENTER_PERCENT = 35;
    private static final int LOW_HEALTH_EXIT_PERCENT = 60;
    private static final int RALLY_MIN_DISTANCE = 2;
    private static final int RALLY_MAX_DISTANCE = 3;
    private static final int MELEE_SUPPORT_RADIUS = 4;
    private static final int RANGED_SUPPORT_RADIUS = 8;

    private final CoHeroAlly owner;
    private boolean lowHealthRally;

    CoHeroSupportController(CoHeroAlly owner) {
        this.owner = owner;
    }

    boolean isLowHealthRally() {
        return lowHealthRally;
    }

    boolean isBelowLowHealthThreshold() {
        return owner.HT > 0
                && owner.HP * 100 < owner.HT * LOW_HEALTH_ENTER_PERCENT;
    }

    void restoreLowHealthRally(boolean active) {
        lowHealthRally = active;
    }

    void clearLowHealthRally() {
        lowHealthRally = false;
    }

    void updateLowHealthRallyState() {
        if (owner.HT <= 0) {
            lowHealthRally = false;
            return;
        }

        if (lowHealthRally) {
            if (owner.HP * 100 >= owner.HT * LOW_HEALTH_EXIT_PERCENT) {
                lowHealthRally = false;
                owner.clearExplorationTarget();
            }
        } else if (owner.HP * 100 < owner.HT * LOW_HEALTH_ENTER_PERCENT) {
            lowHealthRally = true;
            owner.clearExplorationTarget();
        }
    }

    Boolean tryFollowHeroForNearbyEnemy() {
        Mob threat = heroSupportThreat();
        if (threat == null) {
            return null;
        }

        owner.clearExplorationTarget();
        owner.prepareGuardHeroSupportMovement();
        owner.setMovementDecision(
                "hero_support threat=" + threat.getClass().getSimpleName()
                        + " threatPos=" + threat.pos,
                Dungeon.hero.pos);
        return followHeroDirective();
    }

    Mob heroSupportThreat() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive() || Dungeon.level == null) {
            return null;
        }

        for (Mob mob : Dungeon.level.mobs) {
            if (mob == null
                    || !mob.isAlive()
                    || (mob.alignment != Char.Alignment.ENEMY && !(mob instanceof Mimic))) {
                continue;
            }

            int distance = Dungeon.level.distance(Dungeon.hero.pos, mob.pos);
            if (distance <= MELEE_SUPPORT_RADIUS) {
                return mob;
            }
            if (distance <= RANGED_SUPPORT_RADIUS
                    && mob.coHeroCanAttackFrom(mob.pos, Dungeon.hero)) {
                return mob;
            }
        }
        return null;
    }

    boolean followHeroDirective() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            return false;
        }

        owner.followHero();
        boolean result = owner.actCurrentState();
        owner.refreshOwnFieldOfView();
        return result;
    }

    boolean actLowHealthRally() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        int distance = Dungeon.level.distance(owner.pos, Dungeon.hero.pos);

        if (distance > RALLY_MAX_DISTANCE) {
            int oldPos = owner.pos;
            if (owner.getCloser(Dungeon.hero.pos)) {
                owner.spendActionTime(1 / owner.speed());
                return owner.finishMovementAnimation(oldPos);
            }
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        if (!owner.rooted && distance < RALLY_MIN_DISTANCE) {
            int spacingStep = chooseHeroSpacingStep();
            if (spacingStep != -1) {
                int oldPos = owner.pos;
                owner.setMovementDecision("low_health_spacing", spacingStep);
                owner.move(spacingStep, true);
                owner.spendActionTime(1 / owner.speed());
                return owner.finishMovementAnimation(oldPos);
            }
        }

        owner.spendActionTime(Actor.TICK);
        return true;
    }

    private int chooseHeroSpacingStep() {
        int fallback = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int distance = Dungeon.level.distance(cell, Dungeon.hero.pos);
            if (distance == RALLY_MIN_DISTANCE) {
                return cell;
            }
            if (fallback == -1 && distance <= RALLY_MAX_DISTANCE) {
                fallback = cell;
            }
        }
        return fallback;
    }
}
