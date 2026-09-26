package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
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
    }

    void markThrown(long setID, int amount) {
        thrownOutstanding.put(setID, thrownOutstanding.getOrDefault(setID, 0) + amount);
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
        if (recoverPreferredLootAtCurrentCell()) {
            owner.spendActionTime(Actor.TICK);
            return true;
        }

        long searchStarted = System.nanoTime();
        int recoveryCell = nearestPreferredLootCell();
        owner.timings().record(owner, CoHeroTimings.Action.LOOT_SEARCH, searchStarted);
        if (recoveryCell == -1 || recoveryCell == owner.pos) {
            return null;
        }

        int recoveryStep = lootRecoveryStep(recoveryCell);
        if (recoveryStep == -1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("loot_recovery", recoveryCell);
        owner.move(recoveryStep, true);
        owner.spendActionTime(1 / owner.speed());
        return owner.finishMovementAnimation(oldPos);
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
        int bestOwnedCell = -1;
        int bestOwnedDistance = Integer.MAX_VALUE;
        int bestLootCell = -1;
        int bestLootDistance = Integer.MAX_VALUE;

        int[] heapCells = Dungeon.level.heaps.keyArray();
        if (heapCells.length == 0) {
            return -1;
        }

        ArrayList<Integer> ownedCells = new ArrayList<>();
        ArrayList<Integer> lootCells = new ArrayList<>();

        for (int cell : heapCells) {
            Heap heap = Dungeon.level.heaps.get(cell);
            if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
                continue;
            }

            if (Actor.findChar(cell) != null && Actor.findChar(cell) != owner) {
                continue;
            }

            boolean ownedCandidate = false;
            boolean lootCandidate = false;

            for (Item item : heap.items) {
                if (item instanceof MissileWeapon) {
                    MissileWeapon missile = (MissileWeapon) item;
                    Integer outstanding = thrownOutstanding.get(missile.setID);
                    if (outstanding != null
                            && outstanding > 0
                            && CoHeroMissileAdapter.supported(missile)
                            && owner.inventory().canAddToBackpack(missile)) {
                        ownedCandidate = true;
                        break;
                    }
                }

                if (owner.isKnown(cell)
                        && (item instanceof Gold || canAutoPickup(item))) {
                    lootCandidate = true;
                }
            }

            if (!ownedCandidate && !lootCandidate) {
                continue;
            }

            if (ownedCandidate) {
                ownedCells.add(cell);
            } else {
                lootCells.add(cell);
            }
        }

        if (ownedCells.isEmpty() && lootCells.isEmpty()) {
            return -1;
        }

        boolean[] safePassable = owner.ordinarySafePassable(false);
        boolean[] passable = Dungeon.findPassable(owner, safePassable, owner.fieldOfView, true);
        passable[owner.pos] = true;
        PathFinder.buildDistanceMap(owner.pos, passable);

        for (int cell : ownedCells) {
            int distance = PathFinder.distance[cell];
            if (distance != Integer.MAX_VALUE && (distance < bestOwnedDistance
                    || (distance == bestOwnedDistance && (bestOwnedCell == -1 || cell < bestOwnedCell)))) {
                bestOwnedDistance = distance;
                bestOwnedCell = cell;
            }
        }
        for (int cell : lootCells) {
            int distance = PathFinder.distance[cell];
            if (distance != Integer.MAX_VALUE && (distance < bestLootDistance
                    || (distance == bestLootDistance && (bestLootCell == -1 || cell < bestLootCell)))) {
                bestLootDistance = distance;
                bestLootCell = cell;
            }
        }

        return bestOwnedCell != -1 ? bestOwnedCell : bestLootCell;
    }

    private boolean canAutoPickup(Item item) {
        return CompanionInventory.usableByCoHero(item)
                && owner.inventory().canAddToBackpack(item);
    }

    private int lootRecoveryStep(int cell) {
        if (owner.rooted || cell == owner.pos || !Dungeon.level.insideMap(cell)) {
            return -1;
        }

        boolean[] safePassable = owner.ordinarySafePassable(false);
        int step = Dungeon.findStep(owner, cell, safePassable, owner.fieldOfView, true);
        return step != -1 && owner.isMovementSafe(step) ? step : -1;
    }
}
