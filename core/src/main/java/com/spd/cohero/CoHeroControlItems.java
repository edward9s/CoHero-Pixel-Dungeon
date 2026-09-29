package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Freezing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Chill;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Dread;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Frost;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicalSleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Sleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Stamina;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Terror;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfFrost;
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
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfShock;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
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


    boolean tryUseCombatFrostPotion(
            Mob targetMob, ArrayList<Mob> threats, CoHeroCombatRisk risk) {
        if (risk == null) {
            throw new IllegalArgumentException("Combat frost potion use requires current combat risk");
        }
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || risk.retreat
                || !owner.inventory().hasAutoFrostPotion()) {
            return false;
        }

        boolean pressured = risk.attackersNow >= 2
                || threats.size() >= 3
                || risk.ttd <= 6f;
        boolean dangerousSingleBoss =
                (Char.hasProp(targetMob, Char.Property.BOSS)
                        || Char.hasProp(targetMob, Char.Property.MINIBOSS))
                && risk.ttd <= 6f;

        if (!pressured && !dangerousSingleBoss) {
            return false;
        }

        int frostCell = chooseSafeFrostCell(threats, dangerousSingleBoss ? 1 : 2);
        return frostCell != -1 && useFrostPotion(frostCell);
    }

    boolean tryUseRetreatFrostPotion(
            CoHeroCombatRisk risk, ArrayList<Mob> threats) {
        if (risk == null) {
            throw new IllegalArgumentException("Retreat frost potion use requires current combat risk");
        }
        if (!risk.retreat
                || threats == null
                || threats.isEmpty()
                || risk.attackersNow != 0
                || !owner.inventory().hasAutoFrostPotion()) {
            return false;
        }

        // Frost is delayed control. Never spend the current escape turn on it when the incoming
        // volley is already lethal; immediate movement/control remains the correct response.
        if (risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding()) {
            return false;
        }

        int minTargets = threats.size() >= 2 ? 2 : 1;
        if (minTargets == 1 && risk.ttd > 5f) {
            return false;
        }

        int frostCell = chooseSafeFrostCell(threats, minTargets);
        return frostCell != -1 && useFrostPotion(frostCell);
    }

    private int chooseSafeFrostCell(ArrayList<Mob> threats, int minTargets) {
        if (owner.fieldOfView == null || minTargets <= 0) {
            return -1;
        }

        int bestCell = -1;
        int bestTargets = minTargets - 1;
        float bestThreatScore = Float.NEGATIVE_INFINITY;

        for (Mob anchor : threats) {
            if (anchor == null
                    || !anchor.isAlive()
                    || anchor.state == anchor.SLEEPING
                    || !owner.fieldOfView[anchor.pos]) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS9) {
                int candidate = anchor.pos + offset;
                if (!Dungeon.level.insideMap(candidate)
                        || Dungeon.level.distance(anchor.pos, candidate) > 1
                        || !owner.fieldOfView[candidate]
                        || !owner.isKnown(candidate)
                        || !Dungeon.level.passable[candidate]
                        || Dungeon.level.pit[candidate]
                        || Dungeon.level.secret[candidate]
                        || Dungeon.level.map[candidate] == Terrain.WELL) {
                    continue;
                }

                Ballistica shot = new Ballistica(owner.pos, candidate, Ballistica.PROJECTILE);
                if (shot.collisionPos != candidate) {
                    continue;
                }

                int affected = 0;
                float threatScore = 0f;
                boolean unsafe = false;

                for (int areaOffset : PathFinder.NEIGHBOURS9) {
                    int cell = candidate + areaOffset;
                    if (!Dungeon.level.insideMap(cell)
                            || Dungeon.level.distance(candidate, cell) > 1
                            || Dungeon.level.solid[cell]) {
                        continue;
                    }

                    // Freezing a heap can shatter potions or otherwise mutate its contents.
                    if (Dungeon.level.heaps.get(cell) != null) {
                        unsafe = true;
                        break;
                    }

                    Char ch = Actor.findChar(cell);
                    if (ch == null) {
                        continue;
                    }

                    if (ch.alignment != Char.Alignment.ENEMY) {
                        if (!ch.isImmune(Freezing.class)) {
                            unsafe = true;
                            break;
                        }
                        continue;
                    }

                    if (!(ch instanceof Mob)) {
                        unsafe = true;
                        break;
                    }

                    Mob mob = (Mob) ch;
                    if (mob.state == mob.SLEEPING || !threats.contains(mob)) {
                        unsafe = true;
                        break;
                    }

                    if (mob.isImmune(Freezing.class)
                            || mob.isImmune(Chill.class)
                            || mob.buff(Frost.class) != null
                            || mob.buff(Chill.class) != null) {
                        continue;
                    }

                    affected++;
                    threatScore += owner.estimatedThreatDamage(mob, owner.pos)
                            * owner.estimatedHitChance(mob, owner.pos)
                            * Math.max(0.1f, owner.threatOpportunity(mob, owner.pos));
                }

                if (unsafe || affected < minTargets) {
                    continue;
                }

                if (affected > bestTargets
                        || (affected == bestTargets && threatScore > bestThreatScore)) {
                    bestCell = candidate;
                    bestTargets = affected;
                    bestThreatScore = threatScore;
                }
            }
        }

        return bestCell;
    }

    private boolean useFrostPotion(int cell) {
        Potion potion = owner.inventory().takeOneAutoFrostPotion();
        if (!(potion instanceof PotionOfFrost)) {
            return false;
        }

        // Match stock thrown-potion gameplay semantics without routing through Hero-only Item.cast().
        Dungeon.level.pressCell(cell);
        ((PotionOfFrost) potion).shatter(cell);
        Catalog.countUse(PotionOfFrost.class);
        Invisibility.dispel(owner);
        owner.spendActionTime(Actor.TICK);
        return true;
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

        // Shock is also an offensive setup when CoHero can convert the stun/recharge into
        // damaging wand pressure. A single target is enough when a damaging wand needs charge;
        // otherwise require multi-target control so the runestone is not spent for trivial tempo.
        int shockCell = chooseOffensiveShockCell(targetMob, threats);
        if (shockCell != -1 && useShockStone(shockCell)) {
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

        // Flock is an escape barrier, not a pursuit tool. Sheep cannot be damaged and have
        // effectively infinite evasion, so a safe cast can physically deny pursuit for several turns.
        int flockCell = chooseEmergencyFlockCell(threats);
        if (flockCell != -1 && useFlockStone(flockCell)) {
            return true;
        }

        // Shock is short-lived but can buy the one clean movement turn needed to disengage.
        int shockCell = chooseEmergencyShockCell(threats);
        if (shockCell != -1 && useShockStone(shockCell)) {
            return true;
        }

        boolean immediateLethal = risk.immediateIncoming * 1.35f >= owner.HP + owner.shielding();
        Mob fearTarget = chooseFearTarget(threats);
        if (fearTarget != null
                && (immediateLethal || risk.ttd <= 2.5f || risk.attackersNow >= 2)
                && useFearStone(fearTarget)) {
            return true;
        }

        // Deep sleep removes a threat from the current fight and is therefore escape/control,
        // not a pursuit setup. Sleeping enemies intentionally leave CoHero's active combat set.
        Mob sleepTarget = chooseEmergencySleepTarget(threats);
        if (sleepTarget != null
                && (immediateLethal || risk.attackersNow >= 2)
                && useDeepSleepStone(sleepTarget)) {
            return true;
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

        float horizon = Math.max(Actor.TICK, 1f / owner.speed());
        CoHeroThreatTiming before =
                owner.assessThreatTimingAtCell(owner.pos, threats, horizon);

        int best = -1;
        float bestScore = 0f;
        for (Mob mob : threats) {
            if (mob == null || !mob.isAlive() || !canUseFlockAt(mob.pos)) {
                continue;
            }

            boolean[] blocked = predictedFlockCells(mob.pos);
            if (!hasPostFlockMovementOption(blocked)) {
                continue;
            }

            CoHeroThreatTiming after =
                    owner.assessThreatTimingAtCellWithBlockedCells(
                            owner.pos, threats, horizon, blocked);

            int attackersReduced =
                    before.attackersWithinHorizon - after.attackersWithinHorizon;
            float incomingReduced =
                    before.incomingDptWithinHorizon - after.incomingDptWithinHorizon;
            float timeGained =
                    finiteTimeGain(before.nearestAttackTime, after.nearestAttackTime);

            if (attackersReduced <= 0
                    && incomingReduced <= 0.01f
                    && timeGained <= 0.25f) {
                continue;
            }

            float score = attackersReduced * 1000f
                    + Math.max(0f, incomingReduced) * 20f
                    + timeGained * 100f;
            if (best == -1 || score > bestScore) {
                best = mob.pos;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean[] predictedFlockCells(int center) {
        boolean[] blocked = new boolean[Dungeon.level.length()];
        boolean[] open = BArray.not(Dungeon.level.solid, null);
        PathFinder.buildDistanceMap(center, open, 2);
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] != Integer.MAX_VALUE
                    && Dungeon.level.insideMap(cell)
                    && Actor.findChar(cell) == null
                    && !Dungeon.level.pit[cell]) {
                blocked[cell] = true;
            }
        }
        return blocked;
    }

    private boolean hasPostFlockMovementOption(boolean[] blocked) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (Dungeon.level.insideMap(cell)
                    && Dungeon.level.distance(owner.pos, cell) == 1
                    && Dungeon.level.passable[cell]
                    && !blocked[cell]
                    && Actor.findChar(cell) == null
                    && owner.isMovementSafe(cell)) {
                return true;
            }
        }
        return false;
    }

    private float finiteTimeGain(float before, float after) {
        if (Float.isInfinite(after)) {
            return Float.isInfinite(before) ? 0f : 8f;
        }
        if (Float.isInfinite(before)) {
            return 0f;
        }
        return Math.max(0f, Math.min(8f, after - before));
    }

    private int chooseOffensiveShockCell(Mob targetMob, ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfShock.class)
                || !allRelevantDamagingWandsLowCharge(targetMob)) {
            return -1;
        }

        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (Mob candidate : threats) {
            if (candidate == null || !candidate.isAlive() || !owner.fieldOfView[candidate.pos]) {
                continue;
            }

            PathFinder.buildDistanceMap(
                    candidate.pos, BArray.not(Dungeon.level.solid, null), 2);
            if (PathFinder.distance[targetMob.pos] == Integer.MAX_VALUE) {
                continue;
            }

            int hits = 0;
            int newlyParalysed = 0;
            boolean unsafe = false;
            for (int cell = 0; cell < PathFinder.distance.length; cell++) {
                if (PathFinder.distance[cell] == Integer.MAX_VALUE) {
                    continue;
                }

                Char ch = Actor.findChar(cell);
                if (ch == null) {
                    continue;
                }
                if (ch.alignment != Char.Alignment.ENEMY
                        || (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING)) {
                    unsafe = true;
                    break;
                }

                hits++;
                if (!ch.isImmune(Paralysis.class) && ch.buff(Paralysis.class) == null) {
                    newlyParalysed++;
                }
            }

            // Offensive Shock should buy both wand charge and actual tempo. Pure recharge against
            // only paralysis-immune targets is not enough reason to spend the runestone.
            if (unsafe || hits == 0 || newlyParalysed == 0) {
                continue;
            }

            int score = hits * 100 + newlyParalysed * 50;
            if (candidate.pos == targetMob.pos) {
                score += 25;
            }
            if (best == -1 || score > bestScore) {
                best = candidate.pos;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean allRelevantDamagingWandsLowCharge(Mob targetMob) {
        if (targetMob == null || !targetMob.isAlive()) {
            return false;
        }

        boolean foundRelevantWand = false;
        for (Wand wand : owner.inventory().wands()) {
            if (!CoHeroWandAdapter.supported(wand)
                    || !CoHeroWandAdapter.damagingPotential(wand, targetMob)) {
                continue;
            }

            if (wand.curCharges == 0) {
                // An empty damaging wand is exactly the resource Shock is meant to recover.
                foundRelevantWand = true;
                continue;
            }

            // With charge available, reuse the normal attack legality/safety decision. A full wand
            // that cannot currently hit this target safely must not block Shock use.
            if (!CoHeroWandAdapter.canAffectEnemy(wand, owner, targetMob)) {
                continue;
            }

            foundRelevantWand = true;
            if (wand.curCharges > 1) {
                return false;
            }
        }

        return foundRelevantWand;
    }

    private int chooseEmergencyShockCell(ArrayList<Mob> threats) {
        if (!owner.inventory().hasCombatRunestone(StoneOfShock.class)) {
            return -1;
        }

        float horizon = Math.max(Actor.TICK, 1f / owner.speed());
        CoHeroThreatTiming before =
                owner.assessThreatTimingAtCell(owner.pos, threats, horizon);

        int best = -1;
        float bestScore = 0f;
        for (Mob candidate : threats) {
            if (candidate == null || !candidate.isAlive() || !owner.fieldOfView[candidate.pos]) {
                continue;
            }

            PathFinder.buildDistanceMap(
                    candidate.pos, BArray.not(Dungeon.level.solid, null), 2);
            ArrayList<Mob> remaining = new ArrayList<>();
            boolean unsafe = false;
            for (Mob threat : threats) {
                if (threat == null || !threat.isAlive()) {
                    continue;
                }

                boolean inArea = PathFinder.distance[threat.pos] != Integer.MAX_VALUE;
                if (!inArea
                        || threat.isImmune(Paralysis.class)
                        || threat.buff(Paralysis.class) != null) {
                    remaining.add(threat);
                }
            }

            for (int cell = 0; cell < PathFinder.distance.length; cell++) {
                if (PathFinder.distance[cell] == Integer.MAX_VALUE) {
                    continue;
                }
                Char ch = Actor.findChar(cell);
                if (ch != null
                        && (ch.alignment != Char.Alignment.ENEMY
                            || (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING))) {
                    unsafe = true;
                    break;
                }
            }
            if (unsafe || remaining.size() == threats.size()) {
                continue;
            }

            CoHeroThreatTiming after =
                    owner.assessThreatTimingAtCell(owner.pos, remaining, horizon);
            int attackersReduced =
                    before.attackersWithinHorizon - after.attackersWithinHorizon;
            float incomingReduced =
                    before.incomingDptWithinHorizon - after.incomingDptWithinHorizon;

            if (attackersReduced <= 0 && incomingReduced <= 0.01f) {
                continue;
            }

            float score = attackersReduced * 1000f
                    + Math.max(0f, incomingReduced) * 20f;
            if (best == -1 || score > bestScore) {
                best = candidate.pos;
                bestScore = score;
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

    private boolean useShockStone(int center) {
        Runestone stone = owner.inventory().takeOneCombatRunestone(StoneOfShock.class);
        if (!(stone instanceof StoneOfShock)) {
            return false;
        }

        PathFinder.buildDistanceMap(center, BArray.not(Dungeon.level.solid, null), 2);
        int hits = 0;
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            Char ch = Actor.findChar(cell);
            if (ch == null || ch.alignment != Char.Alignment.ENEMY) {
                continue;
            }

            Buff.prolong(ch, Paralysis.class, 1f);
            hits++;
        }

        if (hits == 0) {
            owner.inventory().addToBackpack(stone);
            return false;
        }

        // Stock StoneOfShock grants recharge for every target hit, even if Paralysis is immune.
        owner.inventory().gainWandCharge(1f + hits);
        return finishRunestoneUse(stone, Assets.Sounds.LIGHTNING);
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
