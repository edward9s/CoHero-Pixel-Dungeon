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
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Light;
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

        int recoveryCell = nearestPreferredLootCell();
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

        if (selected == null && Dungeon.level.viewDistance < Light.DISTANCE) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (item instanceof Torch && owner.inventory().canAddToBackpack(item)) {
                    selected = item;
                    break;
                }
            }
        }

        if (selected == null) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (item instanceof MissileWeapon) {
                    MissileWeapon missile = (MissileWeapon) item;
                    if (CoHeroMissileAdapter.supported(missile)
                            && owner.inventory().canAddToBackpack(missile)) {
                        selected = missile;
                        break;
                    }
                } else if (item instanceof Wand) {
                    Wand wand = (Wand) item;
                    if (CoHeroWandAdapter.supported(wand)
                            && owner.inventory().canAddToBackpack(wand)) {
                        selected = wand;
                        break;
                    }
                }
            }
        }

        if (selected == null) {
            return false;
        }

        heap.remove(selected);

        if (selected instanceof Gold) {
            collectGold((Gold) selected);
            return true;
        }

        if (!owner.inventory().addToBackpack(selected)) {
            Dungeon.level.drop(selected, owner.pos).sprite.drop();
            return false;
        }

        if (selectedOwnedMissile) {
            MissileWeapon missile = (MissileWeapon) selected;
            markRecovered(missile.setID, missile.quantity());
        }
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

        boolean[] safePassable = owner.ordinarySafePassable(false);

        for (int cell : Dungeon.level.heaps.keyArray()) {
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

                    if (owner.isKnown(cell)
                            && CoHeroMissileAdapter.supported(missile)
                            && owner.inventory().canAddToBackpack(missile)) {
                        lootCandidate = true;
                    }
                } else if (item instanceof Wand) {
                    Wand wand = (Wand) item;
                    if (owner.isKnown(cell)
                            && CoHeroWandAdapter.supported(wand)
                            && owner.inventory().canAddToBackpack(wand)) {
                        lootCandidate = true;
                    }
                } else if (item instanceof Torch) {
                    if (Dungeon.level.viewDistance < Light.DISTANCE
                            && owner.isKnown(cell)
                            && owner.inventory().canAddToBackpack(item)) {
                        lootCandidate = true;
                    }
                } else if (item instanceof Gold && owner.isKnown(cell)) {
                    lootCandidate = true;
                }
            }

            if (!ownedCandidate && !lootCandidate) {
                continue;
            }

            int distance = lootRecoveryPathDistance(cell, safePassable);
            if (distance == Integer.MAX_VALUE) {
                continue;
            }

            if (ownedCandidate) {
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

        return bestOwnedCell != -1 ? bestOwnedCell : bestLootCell;
    }

    private int lootRecoveryPathDistance(int cell, boolean[] safePassable) {
        if (cell == owner.pos) {
            return 0;
        }

        PathFinder.Path recoveryPath =
                Dungeon.findPath(owner, cell, safePassable, owner.fieldOfView, true);
        return recoveryPath == null ? Integer.MAX_VALUE : recoveryPath.size();
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
