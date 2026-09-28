package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns CoHero loot recovery policy and thrown-missile recovery tracking.
 */
final class CoHeroLoot {

    private static final String THROWN_SET_IDS = "cohero_thrown_set_ids";
    private static final String THROWN_SET_COUNTS = "cohero_thrown_set_counts";

    private final CoHeroAlly owner;
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private int recoveryTarget = -1;
    private long unreachableCandidateSignature = Long.MIN_VALUE;
    private boolean[] unreachablePassableSnapshot;

    CoHeroLoot(CoHeroAlly owner) {
        this.owner = owner;
    }

    void storeInBundle(Bundle bundle) {
        long[] thrownIDs = new long[thrownOutstanding.size()];
        int[] thrownCounts = new int[thrownOutstanding.size()];
        int index = 0;
        for (Map.Entry<Long, Integer> entry : thrownOutstanding.entrySet()) {
            thrownIDs[index] = entry.getKey();
            thrownCounts[index] = entry.getValue();
            index++;
        }
        bundle.put(THROWN_SET_IDS, thrownIDs);
        bundle.put(THROWN_SET_COUNTS, thrownCounts);
    }

    void restoreFromBundle(Bundle bundle) {
        thrownOutstanding.clear();
        if (!bundle.contains(THROWN_SET_IDS) && !bundle.contains(THROWN_SET_COUNTS)) {
            return;
        }

        long[] thrownIDs = bundle.getLongArray(THROWN_SET_IDS);
        int[] thrownCounts = bundle.getIntArray(THROWN_SET_COUNTS);
        if (thrownIDs.length != thrownCounts.length) {
            throw new IllegalStateException("Corrupt CoHero thrown-weapon tracking");
        }

        for (int i = 0; i < thrownIDs.length; i++) {
            if (thrownCounts[i] > 0) {
                thrownOutstanding.put(thrownIDs[i], thrownCounts[i]);
            }
        }
    }

    void resetForLevel() {
        thrownOutstanding.clear();
        recoveryTarget = -1;
        clearUnreachableCache();
    }

    void markThrown(long setID, int amount) {
        thrownOutstanding.put(setID, thrownOutstanding.getOrDefault(setID, 0) + amount);
        recoveryTarget = -1;
        clearUnreachableCache();
    }

    void markRecovered(long setID, int amount) {
        Integer count = thrownOutstanding.get(setID);
        if (count == null) {
            return;
        }

        int remaining = count - amount;
        if (remaining > 0) {
            thrownOutstanding.put(setID, remaining);
        } else {
            thrownOutstanding.remove(setID);
        }
    }

    Boolean actRecovery() {
        long validateStarted = System.nanoTime();
        try {
            if (recoverPreferredLootAtCurrentCell()) {
                recoveryTarget = -1;
                clearUnreachableCache();
                owner.clearNavigationPath();
                owner.spendActionTime(Actor.TICK);
                return true;
            }

            if (recoveryTarget != -1 && preferredLootPriority(recoveryTarget) == 0) {
                recoveryTarget = -1;
                owner.clearNavigationPath();
            }
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.RECOVERY_VALIDATE, validateStarted);
        }

        if (recoveryTarget == -1) {
            long searchStarted = System.nanoTime();
            recoveryTarget = nearestPreferredLootCell();
            owner.timings().record(owner, CoHeroTimings.Action.LOOT_SEARCH, searchStarted);
        }
        if (recoveryTarget == -1 || recoveryTarget == owner.pos) {
            recoveryTarget = -1;
            return null;
        }

        int oldPos = owner.pos;
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("loot_recovery", recoveryTarget);

        long moveStarted = System.nanoTime();
        boolean moved;
        try {
            moved = owner.getCloser(recoveryTarget);
        } finally {
            owner.timings().record(owner, CoHeroTimings.Action.RECOVERY_MOVE, moveStarted);
        }
        if (!moved) {
            return null;
        }

        owner.spendActionTime(1 / owner.speed());

        long animationStarted = System.nanoTime();
        try {
            return owner.finishMovementAnimation(oldPos);
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.RECOVERY_ANIMATION, animationStarted);
        }
    }

    private boolean recoverPreferredLootAtCurrentCell() {
        Heap heap = Dungeon.level.heaps.get(owner.pos);
        if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
            return false;
        }

        Item selected = null;
        boolean selectedOwnedMissile = false;

        for (Item item : new ArrayList<>(heap.items)) {
            if (!(item instanceof MissileWeapon)) {
                continue;
            }

            MissileWeapon missile = (MissileWeapon) item;
            Integer outstanding = thrownOutstanding.get(missile.setID);
            if (outstanding != null
                    && outstanding > 0
                    && CoHeroMissileAdapter.supported(missile)
                    && owner.inventory().canAddToBackpack(missile)) {
                selected = missile;
                selectedOwnedMissile = true;
                break;
            }
        }

        if (selected == null) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (item instanceof Gold) {
                    selected = item;
                    break;
                }
            }
        }

        if (selected == null) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (canAutoPickup(item)) {
                    selected = item;
                    break;
                }
            }
        }

        if (selected == null) {
            return false;
        }

        long pickupStarted = System.nanoTime();
        heap.remove(selected);

        if (selected instanceof Gold) {
            collectGold((Gold) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_GOLD, pickupStarted);
            return true;
        }

        if (!owner.inventory().addToBackpack(selected)) {
            Dungeon.level.drop(selected, owner.pos).sprite.drop();
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_FAILED, pickupStarted);
            return false;
        }

        if (selectedOwnedMissile) {
            MissileWeapon missile = (MissileWeapon) selected;
            markRecovered(missile.setID, missile.quantity());
        } else {
            owner.inventory().autoEquipUpgrade(selected);
        }

        owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
        return true;
    }

    private void collectGold(Gold gold) {
        int amount = gold.quantity();
        Catalog.setSeen(Gold.class);
        Statistics.itemTypesDiscovered.add(Gold.class);
        Dungeon.gold += amount;
        Statistics.goldCollected += amount;
        Badges.validateGoldCollected();

        GameScene.pickUp(gold, owner.pos);
        CharSprite sprite = owner.attachedSprite();
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.NEUTRAL,
                    Integer.toString(amount),
                    FloatingText.GOLD);
        }
        Sample.INSTANCE.play(
                Assets.Sounds.GOLD,
                1,
                1,
                Random.Float(0.9f, 1.1f));
    }

    private int nearestPreferredLootCell() {
        int[] heapCells = Dungeon.level.heaps.keyArray();
        if (heapCells.length == 0) {
            clearUnreachableCache();
            return -1;
        }

        long candidateSignature = 0xcbf29ce484222325L;
        boolean hasCandidate = false;
        for (int cell : heapCells) {
            int priority = preferredLootPriority(cell);
            if (priority == 0) {
                continue;
            }
            hasCandidate = true;
            candidateSignature ^= ((long) cell << 2) ^ priority;
            candidateSignature *= 0x100000001b3L;
        }

        if (!hasCandidate) {
            clearUnreachableCache();
            return -1;
        }

        boolean[] safePassable = owner.ordinarySafePassable(false);
        boolean[] passable = Dungeon.findPassable(owner, safePassable, owner.fieldOfView, true);
        passable[owner.pos] = true;

        if (candidateSignature == unreachableCandidateSignature
                && unreachablePassableSnapshot != null
                && Arrays.equals(unreachablePassableSnapshot, passable)) {
            return -1;
        }

        PathFinder.buildDistanceMap(owner.pos, passable);

        int bestOwnedCell = -1;
        int bestOwnedDistance = Integer.MAX_VALUE;
        int bestLootCell = -1;
        int bestLootDistance = Integer.MAX_VALUE;

        for (int cell : heapCells) {
            int priority = preferredLootPriority(cell);
            if (priority == 0) {
                continue;
            }

            int distance = PathFinder.distance[cell];
            if (distance == Integer.MAX_VALUE) {
                continue;
            }

            if (priority == 2) {
                if (distance < bestOwnedDistance
                        || (distance == bestOwnedDistance
                            && (bestOwnedCell == -1 || cell < bestOwnedCell))) {
                    bestOwnedDistance = distance;
                    bestOwnedCell = cell;
                }
            } else if (distance < bestLootDistance
                    || (distance == bestLootDistance
                        && (bestLootCell == -1 || cell < bestLootCell))) {
                bestLootDistance = distance;
                bestLootCell = cell;
            }
        }

        int result = bestOwnedCell != -1 ? bestOwnedCell : bestLootCell;
        if (result == -1) {
            unreachableCandidateSignature = candidateSignature;
            unreachablePassableSnapshot = passable.clone();
        } else {
            clearUnreachableCache();
        }
        return result;
    }

    private int preferredLootPriority(int cell) {
        if (!Dungeon.level.insideMap(cell)) {
            return 0;
        }

        Heap heap = Dungeon.level.heaps.get(cell);
        if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
            return 0;
        }

        Char occupant = Actor.findChar(cell);
        if (occupant != null && occupant != owner) {
            return 0;
        }

        boolean known = owner.isKnown(cell);
        boolean ordinaryCandidate = false;
        for (Item item : heap.items) {
            if (item instanceof MissileWeapon) {
                MissileWeapon missile = (MissileWeapon) item;
                Integer outstanding = thrownOutstanding.get(missile.setID);
                if (outstanding != null
                        && outstanding > 0
                        && CoHeroMissileAdapter.supported(missile)
                        && owner.inventory().canAddToBackpack(missile)) {
                    return 2;
                }
            }

            if (known && (item instanceof Gold || canAutoPickup(item))) {
                ordinaryCandidate = true;
            }
        }
        return ordinaryCandidate ? 1 : 0;
    }

    private void clearUnreachableCache() {
        unreachableCandidateSignature = Long.MIN_VALUE;
        unreachablePassableSnapshot = null;
    }

    private boolean canAutoPickup(Item item) {
        return owner.inventory().canUse(item)
                && owner.inventory().canAddToBackpack(item);
    }


}
