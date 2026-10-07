package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.ArcaneResin;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.LiquidMetal;
import com.shatteredpixel.shatteredpixeldungeon.items.Stylus;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.SkeletonKey;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.GooBlob;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.MetalShard;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournal;
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

    private enum PickupDestination {
        NONE,
        COHERO,
        HERO,
        KEYRING,
        ENERGY_POOL,
        COHERO_DEW_HEAL,
        HERO_WATERSKIN
    }

    private final CoHeroAlly owner;
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private int recoveryTarget = -1;
    private long unreachableCandidateSignature = Long.MIN_VALUE;
    private int unreachableFromCell = -1;
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
        long validateStarted = owner.timings().startNanos();
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
            long searchStarted = owner.timings().startNanos();
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

        long moveStarted = owner.timings().startNanos();
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

        long animationStarted = owner.timings().startNanos();
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

        long pickupStarted = owner.timings().startNanos();
        heap.remove(selected);

        if (selected instanceof Gold) {
            collectGold((Gold) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_GOLD, pickupStarted);
            return true;
        }

        PickupDestination pickupDestination = selectedOwnedMissile
                ? PickupDestination.COHERO
                : autoPickupDestination(selected);
        if (pickupDestination == PickupDestination.HERO) {
            routeToHero(selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
            return true;
        }
        if (pickupDestination == PickupDestination.KEYRING) {
            collectKey((Key) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
            return true;
        }
        if (pickupDestination == PickupDestination.ENERGY_POOL) {
            collectEnergyCrystal((EnergyCrystal) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
            return true;
        }
        if (pickupDestination == PickupDestination.COHERO_DEW_HEAL) {
            consumeDewForCoHero((Dewdrop) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
            return true;
        }
        if (pickupDestination == PickupDestination.HERO_WATERSKIN) {
            collectDewForHero((Dewdrop) selected);
            owner.timings().record(owner, CoHeroTimings.Action.PICKUP_ITEM, pickupStarted);
            return true;
        }
        if (pickupDestination != PickupDestination.COHERO) {
            throw new IllegalStateException(
                    "Selected CoHero loot has no pickup destination: "
                            + selected.getClass().getName());
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

    private void collectKey(Key key) {
        Catalog.setSeen(key.getClass());
        Statistics.itemTypesDiscovered.add(key.getClass());
        GameScene.pickUpJournal(key, owner.pos);
        WndJournal.last_index = 0;
        Notes.add(key);
        Sample.INSTANCE.play(Assets.Sounds.ITEM);
        GameScene.updateKeyDisplay();

        SkeletonKey.KeyReplacementTracker tracker =
                Dungeon.hero.buff(SkeletonKey.KeyReplacementTracker.class);
        if (tracker != null) {
            tracker.processExcessKeys();
        }
    }

    private void collectEnergyCrystal(EnergyCrystal crystal) {
        int amount = crystal.quantity();
        Catalog.setSeen(crystal.getClass());
        Statistics.itemTypesDiscovered.add(crystal.getClass());
        Dungeon.energy += amount;

        GameScene.pickUp(crystal, owner.pos);
        CharSprite sprite = owner.attachedSprite();
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.NEUTRAL,
                    Integer.toString(amount),
                    FloatingText.ENERGY);
        }
        Sample.INSTANCE.play(Assets.Sounds.ITEM);
        Item.updateQuickslot();
    }

    private void consumeDewForCoHero(Dewdrop dew) {
        if (owner.HT <= 0 || owner.HP >= owner.HT) {
            throw new IllegalStateException("CoHero cannot consume dew without missing HP");
        }

        Catalog.setSeen(Dewdrop.class);
        Statistics.itemTypesDiscovered.add(Dewdrop.class);

        int effect = Math.round(owner.HT * 0.05f * dew.quantity());
        int heal = Math.min(owner.HT - owner.HP, effect);
        if (heal <= 0) {
            throw new IllegalStateException("CoHero dew healing resolved to zero");
        }

        owner.HP += heal;
        Catalog.countUse(Dewdrop.class);

        CharSprite sprite = owner.attachedSprite();
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.POSITIVE,
                    Integer.toString(heal),
                    FloatingText.HEALING);
        }
        Sample.INSTANCE.play(Assets.Sounds.DEWDROP);
    }

    private void collectDewForHero(Dewdrop dew) {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            throw new IllegalStateException("Cannot route dew without a live Hero");
        }

        Waterskin waterskin = Dungeon.hero.belongings.getItem(Waterskin.class);
        if (waterskin == null || waterskin.isFull()) {
            throw new IllegalStateException("Hero Waterskin cannot accept dew");
        }

        Catalog.setSeen(Dewdrop.class);
        Statistics.itemTypesDiscovered.add(Dewdrop.class);
        waterskin.collectDew(dew);
        GameScene.pickUp(dew, owner.pos);
        Sample.INSTANCE.play(Assets.Sounds.DEWDROP);
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
                && unreachableFromCell == owner.pos
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
            unreachableFromCell = owner.pos;
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
        unreachableFromCell = -1;
        unreachablePassableSnapshot = null;
    }

    private boolean canAutoPickup(Item item) {
        return autoPickupDestination(item) != PickupDestination.NONE;
    }

    private PickupDestination autoPickupDestination(Item item) {
        if (item == null) {
            return PickupDestination.NONE;
        }

        if (owner.inventory().canUse(item)
                && owner.inventory().canAddToBackpack(item)) {
            return PickupDestination.COHERO;
        }

        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            if (item instanceof Key) {
                return PickupDestination.KEYRING;
            }
            if (item instanceof EnergyCrystal) {
                return PickupDestination.ENERGY_POOL;
            }
            if (item instanceof Dewdrop) {
                return dewdropDestination();
            }
            if (isAutoLootResource(item)) {
                return PickupDestination.HERO;
            }
        }

        return PickupDestination.NONE;
    }

    private PickupDestination dewdropDestination() {
        if (owner.HT <= 0) {
            return PickupDestination.NONE;
        }

        boolean injured = owner.HP < owner.HT;
        if (injured && owner.HP * 100 < owner.HT * 60) {
            return PickupDestination.COHERO_DEW_HEAL;
        }

        Waterskin waterskin = Dungeon.hero.belongings.getItem(Waterskin.class);
        if (waterskin != null && !waterskin.isFull()) {
            return PickupDestination.HERO_WATERSKIN;
        }

        if (injured) {
            return PickupDestination.COHERO_DEW_HEAL;
        }

        return PickupDestination.NONE;
    }

    private static boolean isAutoLootResource(Item item) {
        return item instanceof Runestone
                || item instanceof Plant.Seed
                || item instanceof Potion
                || item instanceof Scroll
                || item instanceof Food
                || item instanceof Stylus
                || item instanceof ArcaneResin
                || item instanceof LiquidMetal
                || item instanceof GooBlob
                || item instanceof MetalShard;
    }

    private void routeToHero(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            throw new IllegalStateException("Cannot route CoHero loot without a live Hero");
        }

        if (!item.collect(Dungeon.hero.belongings.backpack)) {
            Dungeon.level.drop(item, Dungeon.hero.pos).sprite.drop();
        }
    }


}
