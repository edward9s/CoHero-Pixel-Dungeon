package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Dread;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicalSleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Sleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Stamina;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Terror;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfDread;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAggression;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlast;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlink;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfDeepSleep;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFear;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFlock;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.BArray;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

final class CoHeroControlItems {

    private final CoHeroAlly owner;

    CoHeroControlItems(CoHeroAlly owner) {
        this.owner = owner;
    }

    boolean tryUseCombatRunestone(
            Mob targetMob, ArrayList<Mob> threats, CoHeroCombatRisk risk) {
        if (risk == null) {
            throw new IllegalArgumentException("Combat runestone use requires current combat risk");
        }
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || owner.buff(MagicImmune.class) != null
                || risk.retreat) {
            return false;
        }

        // Safe clustered damage: only when at least two awake enemies are caught and no
        // ally/neutral/sleeping enemy or heap would be hit.
        int blastCell = chooseSafeBlastCell(threats);
        if (blastCell != -1 && useBlastStone(blastCell)) {
            return true;
        }

        // With 3+ visible threats, redirect the pack onto one non-boss enemy.
        if (threats.size() >= 3) {
            Mob aggressionTarget = chooseAggressionTarget(threats);
            if (aggressionTarget != null && useAggressionStone(aggressionTarget)) {
                return true;
            }
        }

        // With exactly two threats, remove one from the fight rather than spending a stronger
        // area-control resource. Prefer the non-current target when possible.
        if (threats.size() == 2
                && (owner.hasRangedPressure(threats)
                    || Char.hasProp(targetMob, Char.Property.BOSS)
                    || Char.hasProp(targetMob, Char.Property.MINIBOSS))) {
            Mob sleepTarget = chooseDeepSleepTarget(targetMob, threats);
            if (sleepTarget != null && useDeepSleepStone(sleepTarget)) {
                return true;
            }
        }

        // A lone ranged attacker can be boxed in with sheep while CoHero closes the distance.
        if (threats.size() == 1
                && owner.isCurrentRangedPressure(targetMob)
                && Dungeon.level.distance(owner.pos, targetMob.pos) >= 3
                && owner.chooseRangedCoverCell(targetMob, threats) == -1
                && canUseFlockAt(targetMob.pos)
                && useFlockStone(targetMob.pos)) {
            return true;
        }

        return false;
    }

    boolean tryEmergencyBlinkRunestone(ArrayList<Mob> threats) {
        if (threats == null
                || threats.isEmpty()
                || owner.buff(MagicImmune.class) != null) {
            return false;
        }

        int blinkCell = chooseBlinkEscapeCell(threats);
        return blinkCell != -1 && useBlinkStone(blinkCell);
    }

    boolean tryEmergencyRunestone(CoHeroCombatRisk risk, ArrayList<Mob> threats) {
        if (risk == null
                || threats == null
                || threats.isEmpty()
                || owner.buff(MagicImmune.class) != null) {
            return false;
        }

        boolean immediateLethal = risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding();
        Mob fearTarget = chooseFearTarget(threats);
        if (fearTarget != null
                && (immediateLethal || risk.ttd <= 2.5f || risk.attackersNow >= 2)
                && useFearStone(fearTarget)) {
            return true;
        }

        // Deep sleep is a fallback single-target control when fear is unavailable or ineffective.
        Mob sleepTarget = chooseEmergencySleepTarget(threats);
        if (sleepTarget != null
                && (immediateLethal || risk.attackersNow >= 2)
                && useDeepSleepStone(sleepTarget)) {
            return true;
        }

        // Flock is only used defensively here when it can be centered far enough away not to box
        // the Hero or CoHero in with the summoned sheep.
        if (threats.size() >= 2) {
            int flockCell = chooseEmergencyFlockCell(threats);
            if (flockCell != -1 && useFlockStone(flockCell)) {
                return true;
            }
        }

        return false;
    }

    private int chooseSafeBlastCell(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfBlast.class)) {
            return -1;
        }

        int bestCell = -1;
        int bestEnemies = 1;
        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive() || !owner.fieldOfView[threat.pos]) {
                continue;
            }

            boolean[] explodable = new boolean[Dungeon.level.length()];
            BArray.not(Dungeon.level.solid, explodable);
            BArray.or(Dungeon.level.flamable, explodable, explodable);
            PathFinder.buildDistanceMap(threat.pos, explodable, 1);

            int enemies = 0;
            boolean unsafe = false;
            for (int cell = 0; cell < PathFinder.distance.length; cell++) {
                if (PathFinder.distance[cell] == Integer.MAX_VALUE) {
                    continue;
                }

                if (Dungeon.level.heaps.get(cell) != null) {
                    unsafe = true;
                    break;
                }

                Char ch = Actor.findChar(cell);
                if (ch == null) {
                    continue;
                }
                if (ch.alignment != Char.Alignment.ENEMY) {
                    unsafe = true;
                    break;
                }
                if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    unsafe = true;
                    break;
                }
                enemies++;
            }

            if (!unsafe && enemies >= 2 && enemies > bestEnemies) {
                bestEnemies = enemies;
                bestCell = threat.pos;
            }
        }
        return bestCell;
    }

    private Mob chooseAggressionTarget(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfAggression.class)) {
            return null;
        }

        Mob best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || Char.hasProp(mob, Char.Property.BOSS)
                    || Char.hasProp(mob, Char.Property.MINIBOSS)
                    || mob.buff(StoneOfAggression.Aggression.class) != null) {
                continue;
            }

            int nearbyEnemies = 0;
            for (Mob other : threats) {
                if (other != mob
                        && other != null
                        && other.isAlive()
                        && Dungeon.level.distance(other.pos, mob.pos) <= 5) {
                    nearbyEnemies++;
                }
            }

            int score = nearbyEnemies * 100 + mob.HP;
            if (best == null || score > bestScore) {
                best = mob;
                bestScore = score;
            }
        }
        return best;
    }

    private Mob chooseDeepSleepTarget(Mob combatTarget, ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfDeepSleep.class)) {
            return null;
        }

        Mob fallback = null;
        for (Mob mob : threats) {
            if (!canDeepSleep(mob)) {
                continue;
            }
            if (mob != combatTarget) {
                return mob;
            }
            fallback = mob;
        }
        return fallback;
    }

    private Mob chooseEmergencySleepTarget(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfDeepSleep.class)) {
            return null;
        }

        Mob best = null;
        float bestThreat = Float.NEGATIVE_INFINITY;
        for (Mob mob : threats) {
            if (!canDeepSleep(mob)) {
                continue;
            }
            float score = owner.estimatedThreatDamage(mob, owner.pos)
                    * owner.estimatedHitChance(mob, owner.pos)
                    * Math.max(0.1f, owner.threatOpportunity(mob, owner.pos));
            if (best == null || score > bestThreat) {
                best = mob;
                bestThreat = score;
            }
        }
        return best;
    }

    private boolean canDeepSleep(Mob mob) {
        return mob != null
                && mob.isAlive()
                && mob.state != mob.SLEEPING
                && !mob.isImmune(Sleep.class)
                && mob.buff(MagicalSleep.class) == null;
    }

    private Mob chooseFearTarget(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfFear.class)) {
            return null;
        }

        Mob best = null;
        float bestThreat = Float.NEGATIVE_INFINITY;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.isImmune(Terror.class)
                    || mob.buff(Terror.class) != null) {
                continue;
            }

            float score = owner.estimatedThreatDamage(mob, owner.pos)
                    * owner.estimatedHitChance(mob, owner.pos)
                    * Math.max(0.1f, owner.threatOpportunity(mob, owner.pos));
            if (best == null || score > bestThreat) {
                best = mob;
                bestThreat = score;
            }
        }
        return best;
    }

    private int chooseBlinkEscapeCell(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfBlink.class)) {
            return -1;
        }

        int currentDistance = owner.nearestThreatDistance(owner.pos, threats);
        int best = -1;
        int bestDistance = currentDistance;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!owner.fieldOfView[cell]
                    || !owner.isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || Dungeon.level.pit[cell]
                    || Dungeon.level.secret[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)
                    || Dungeon.level.distance(owner.pos, cell) < 3) {
                continue;
            }

            Ballistica path = new Ballistica(owner.pos, cell, Ballistica.PROJECTILE);
            if (path.collisionPos != cell) {
                continue;
            }

            int distance = owner.nearestThreatDistance(cell, threats);
            if (distance < currentDistance + 2) {
                continue;
            }

            if (best == -1 || distance > bestDistance
                    || (distance == bestDistance
                        && Dungeon.level.distance(owner.pos, cell) < Dungeon.level.distance(owner.pos, best))) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canUseFlockAt(int center) {
        if (!owner.inventory().hasCombatRunestone(StoneOfFlock.class)
                || !Dungeon.level.insideMap(center)
                || !owner.fieldOfView[center]
                || Dungeon.level.distance(owner.pos, center) <= 2
                || (Dungeon.hero != null && Dungeon.level.distance(Dungeon.hero.pos, center) <= 2)) {
            return false;
        }

        boolean[] open = BArray.not(Dungeon.level.solid, null);
        PathFinder.buildDistanceMap(center, open, 2);
        int spawnable = 0;
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] != Integer.MAX_VALUE
                    && Dungeon.level.insideMap(cell)
                    && Actor.findChar(cell) == null
                    && !Dungeon.level.pit[cell]) {
                spawnable++;
            }
        }
        return spawnable >= 3;
    }

    private int chooseEmergencyFlockCell(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfFlock.class)) {
            return -1;
        }

        int best = -1;
        int bestNearbyThreats = 0;
        for (Mob mob : threats) {
            if (mob == null || !mob.isAlive() || !canUseFlockAt(mob.pos)) {
                continue;
            }

            int nearby = 0;
            for (Mob other : threats) {
                if (other != null
                        && other.isAlive()
                        && Dungeon.level.distance(other.pos, mob.pos) <= 2) {
                    nearby++;
                }
            }
            if (best == -1 || nearby > bestNearbyThreats) {
                best = mob.pos;
                bestNearbyThreats = nearby;
            }
        }
        return best;
    }

    private boolean useAggressionStone(Mob targetMob) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfAggression.class);
        if (!(stone instanceof StoneOfAggression)) {
            return false;
        }

        Buff.prolong(targetMob,
                StoneOfAggression.Aggression.class,
                StoneOfAggression.Aggression.DURATION);
        CellEmitter.center(targetMob.pos).start(Speck.factory(Speck.SCREAM), 0.3f, 3);
        return finishRunestoneUse(stone, Assets.Sounds.READ);
    }

    private boolean useBlastStone(int cell) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfBlast.class);
        if (!(stone instanceof StoneOfBlast)) {
            return false;
        }

        new Bomb.ConjuredBomb().explode(cell);
        return finishRunestoneUse(stone, null);
    }

    private boolean useFearStone(Mob targetMob) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfFear.class);
        if (!(stone instanceof StoneOfFear)) {
            return false;
        }

        Terror terror = Buff.affect(targetMob, Terror.class, Terror.DURATION);
        terror.object = owner.id();
        return finishRunestoneUse(stone, Assets.Sounds.READ);
    }

    private boolean useDeepSleepStone(Mob targetMob) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfDeepSleep.class);
        if (!(stone instanceof StoneOfDeepSleep)) {
            return false;
        }

        Buff.affect(targetMob, MagicalSleep.class);
        if (targetMob.sprite != null) {
            targetMob.sprite.centerEmitter().start(Speck.factory(Speck.NOTE), 0.3f, 5);
        }
        return finishRunestoneUse(stone, Assets.Sounds.LULLABY);
    }

    private boolean useBlinkStone(int cell) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfBlink.class);
        if (!(stone instanceof StoneOfBlink)) {
            return false;
        }

        if (!ScrollOfTeleportation.teleportToLocation(owner, cell)) {
            owner.inventory().addToBackpack(stone);
            return false;
        }

        owner.refreshOwnFieldOfView();
        owner.clearNavigationPath();
        return finishRunestoneUse(stone, null);
    }

    private boolean useFlockStone(int center) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfFlock.class);
        if (!(stone instanceof StoneOfFlock)) {
            return false;
        }

        boolean[] open = BArray.not(Dungeon.level.solid, null);
        PathFinder.buildDistanceMap(center, open, 2);
        int spawned = 0;
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] == Integer.MAX_VALUE
                    || !Dungeon.level.insideMap(cell)
                    || Actor.findChar(cell) != null
                    || Dungeon.level.pit[cell]) {
                continue;
            }

            Sheep sheep = new Sheep();
            sheep.initialize(8);
            sheep.pos = cell;
            GameScene.add(sheep);
            Dungeon.level.occupyCell(sheep);
            CellEmitter.get(cell).burst(Speck.factory(Speck.WOOL), 4);
            spawned++;
        }

        if (spawned == 0) {
            owner.inventory().addToBackpack(stone);
            return false;
        }

        CellEmitter.get(center).burst(Speck.factory(Speck.WOOL), 4);
        Sample.INSTANCE.play(Assets.Sounds.PUFF);
        Sample.INSTANCE.play(Assets.Sounds.SHEEP);
        return finishRunestoneUse(stone, null);
    }

    private boolean finishRunestoneUse(Runestone stone, String sound) {
        if (stone == null) {
            return false;
        }
        Catalog.countUse(stone.getClass());
        Invisibility.dispel(owner);
        if (sound != null) {
            Sample.INSTANCE.play(sound);
        }
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryUseTeleportationScroll() {
        Scroll scroll = owner.inventory().takeOneAutoTeleportationScroll();
        if (!(scroll instanceof ScrollOfTeleportation)) {
            return false;
        }

        if (!ScrollOfTeleportation.teleportChar(owner)) {
            owner.inventory().addToBackpack(scroll);
            return false;
        }

        Catalog.countUse(ScrollOfTeleportation.class);
        Invisibility.dispel(owner);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        owner.clearNavigationPath();
        owner.refreshOwnFieldOfView();
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    private int usableDreadTargetCount(ArrayList<Mob> threats) {
        if (owner.buff(MagicImmune.class) != null || owner.buff(Blindness.class) != null) {
            return 0;
        }

        int count = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING
                    || (mob.isImmune(Dread.class) && mob.isImmune(Terror.class))) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean tryUseDreadScroll(ArrayList<Mob> threats) {
        if (usableDreadTargetCount(threats) == 0) {
            return false;
        }

        Scroll scroll = owner.inventory().takeOneAutoDreadScroll();
        if (!(scroll instanceof ScrollOfDread)) {
            return false;
        }

        int affected = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING) {
                continue;
            }

            if (!mob.isImmune(Dread.class)) {
                Dread dread = Buff.affect(mob, Dread.class);
                if (dread != null) {
                    dread.object = owner.id();
                    affected++;
                }
            } else if (!mob.isImmune(Terror.class)) {
                Terror terror = Buff.affect(mob, Terror.class, Terror.DURATION);
                if (terror != null) {
                    terror.object = owner.id();
                    affected++;
                }
            }
        }

        if (affected == 0) {
            owner.inventory().addToBackpack(scroll);
            return false;
        }

        Catalog.countUse(ScrollOfDread.class);
        Invisibility.dispel(owner);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean tryEmergencyEscapeConsumable(
            CoHeroCombatRisk risk, ArrayList<Mob> threats) {
        boolean immediateLethal = risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding();

        int terrorTargets = usableTerrorTargetCount(threats);
        if (terrorTargets >= 2 || (terrorTargets >= 1 && immediateLethal)) {
            if (tryUseTerrorScroll(threats)) {
                return true;
            }
        }

        int dreadTargets = usableDreadTargetCount(threats);
        boolean criticallyShortTtd = risk.ttd <= 2f;
        if (dreadTargets >= 2
                && (risk.attackersNow >= 2 || immediateLethal || criticallyShortTtd)
                && tryUseDreadScroll(threats)) {
            return true;
        }

        boolean lowHealthDanger = owner.isBelowLowHealthThreshold();
        if (risk.attackersNow >= 3 || immediateLethal || lowHealthDanger || criticallyShortTtd) {
            if (owner.trySurvivalInvisibility()) {
                return true;
            }
        }

        return false;
    }

    private int usableTerrorTargetCount(ArrayList<Mob> threats) {
        if (owner.buff(MagicImmune.class) != null || owner.buff(Blindness.class) != null) {
            return 0;
        }

        int count = 0;
        for (Mob mob : threats) {
            if (mob != null
                    && mob.isAlive()
                    && mob.alignment == Char.Alignment.ENEMY
                    && mob.invisible <= 0
                    && owner.fieldOfView != null
                    && owner.fieldOfView[mob.pos]
                    && mob.state != mob.SLEEPING
                    && !mob.isImmune(Terror.class)) {
                count++;
            }
        }
        return count;
    }

    private boolean tryUseTerrorScroll(ArrayList<Mob> threats) {
        if (usableTerrorTargetCount(threats) == 0) {
            return false;
        }

        Scroll scroll = owner.inventory().takeOneAutoTerrorScroll();
        if (!(scroll instanceof ScrollOfTerror)) {
            return false;
        }

        int affected = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0
                    || owner.fieldOfView == null
                    || !owner.fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING
                    || mob.isImmune(Terror.class)) {
                continue;
            }

            Terror terror = Buff.affect(mob, Terror.class, Terror.DURATION);
            if (terror != null) {
                terror.object = owner.id();
                affected++;
            }
        }

        if (affected == 0) {
            // The pre-check should prevent owner, but do not consume a known scroll for no effect.
            owner.inventory().addToBackpack(scroll);
            return false;
        }

        Invisibility.dispel(owner);
        Catalog.countUse(ScrollOfTerror.class);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        owner.spendActionTime(Actor.TICK);
        return true;
    }

    boolean shouldUseHasteForRetreat(
            CoHeroCombatRisk risk, ArrayList<Mob> threats, int escapeStep) {
        if (risk == null
                || threats == null
                || escapeStep == -1
                || owner.buff(Haste.class) != null
                || owner.buff(Stamina.class) != null
                || owner.buff(Invisibility.class) != null) {
            return false;
        }

        // Do not spend a turn drinking when the current incoming volley is already near-lethal.
        // In that case the immediate movement/control path remains safer.
        if (risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding()) {
            return false;
        }

        int attackersAfterStep = owner.countCurrentAttackersAtCell(escapeStep, threats);
        float incomingAfterStep = owner.estimatedIncomingDptAtCell(escapeStep, threats);

        boolean fastPursuer = false;
        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive()) {
                continue;
            }
            if (owner.threatOpportunity(threat, escapeStep) >= 0.55f
                    && threat.speed() >= owner.speed() * 0.95f) {
                fastPursuer = true;
                break;
            }
        }

        return attackersAfterStep > 0
                || (incomingAfterStep > 0.01f && fastPursuer)
                || risk.ttd <= 3.5f;
    }
}
