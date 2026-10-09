package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import java.util.ArrayList;

/**
 * Brief defensive response to enemy damage when CoHero cannot currently see its attacker.
 * An attack's damage source may be a Mob or a nested spell type (e.g. Shaman.EarthenBolt).
 * This never makes an unseen enemy eligible for offensive targeting.
 */
final class CoHeroUnderFireController {

    private static final float ALERT_DURATION = 4f;

    private final CoHeroAlly owner;
    private Class<?> attackerType;
    private Mob identifiedAttacker;
    private float lastAttackTime = Float.NEGATIVE_INFINITY;

    CoHeroUnderFireController(CoHeroAlly owner) {
        this.owner = owner;
    }

    void reset() {
        attackerType = null;
        identifiedAttacker = null;
        lastAttackTime = Float.NEGATIVE_INFINITY;
    }

    void observeDamage(Object source) {
        if (source == null) {
            return;
        }

        Class<?> sourceType = source instanceof Class ? (Class<?>) source : source.getClass();
        for (Class<?> type = sourceType; type != null; type = type.getEnclosingClass()) {
            if (Mob.class.isAssignableFrom(type)) {
                attackerType = type;
                identifiedAttacker = source instanceof Mob ? (Mob) source : null;
                lastAttackTime = Actor.now();
                return;
            }
        }
    }

    Boolean tryRespond() {
        if (attackerType == null || Actor.now() - lastAttackTime > ALERT_DURATION) {
            return null;
        }

        ArrayList<Mob> attackers = new ArrayList<>();
        Mob closestAttacker = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive()
                    || mob.alignment != Char.Alignment.ENEMY
                    || mob.state == mob.SLEEPING
                    || !attackerType.isInstance(mob)
                    || (identifiedAttacker != null && mob != identifiedAttacker)
                    || !mob.coHeroCanAttackFrom(mob.pos, owner)) {
                continue;
            }

            attackers.add(mob);
            int distance = Dungeon.level.distance(owner.pos, mob.pos);
            if (closestAttacker == null || distance < closestDistance) {
                closestAttacker = mob;
                closestDistance = distance;
            }
        }

        // The shooter can no longer attack this cell. Do not prolong an alert on stale evidence.
        if (closestAttacker == null) {
            return null;
        }

        if (!owner.rooted) {
            Boolean cover = owner.tryUnseenRangedCover(closestAttacker, attackers);
            if (cover != null) {
                return cover;
            }

            // If cover is unavailable, stop scavenging and return toward Hero for support.
            owner.clearExplorationTarget();
            owner.clearNavigationPath();
            owner.allowAnyGuardMovement();
            owner.setMovementDecision("unseen_fire_rally", Dungeon.hero.pos);
            return owner.followHeroDirectiveForGuard();
        }

        // A rooted companion still must not use its turn to pick up another item under fire.
        if (owner.survival().tryAutoSurvivalPotion()) {
            return true;
        }
        owner.setMovementDecision("unseen_fire_rooted", owner.pos);
        owner.spendActionTime(Actor.TICK);
        return true;
    }
}
