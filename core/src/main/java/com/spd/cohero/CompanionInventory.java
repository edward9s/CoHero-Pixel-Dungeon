package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CoHero-owned inventory model.
 *
 * The companion has a normal 20-slot backpack plus explicit equipment slots. Supported combat
 * equipment is weapon, armor, rings and wands. Consumables, artifacts, trinkets, bags and unknown
 * items are rejected.
 * This intentionally does not reuse Hero/Belongings, whose owner is hard-wired to Hero.
 */
public final class CompanionInventory {

    public static final int BACKPACK_CAPACITY = 20;

    private static final int FORMAT_VERSION = 3;
    private static final String FORMAT = "cohero_inventory_format";
    private static final String WEAPON = "cohero_weapon";
    private static final String ARMOR = "cohero_armor";
    private static final String RING_ONE = "cohero_ring_one";
    private static final String RING_TWO = "cohero_ring_two";
    private static final String BACKPACK = "cohero_backpack";

    private final CoHeroAlly owner;

    private MeleeWeapon weapon;
    private Armor armor;
    private Ring ringOne;
    private Ring ringTwo;
    private final ArrayList<Item> backpack = new ArrayList<>();

    CompanionInventory(CoHeroAlly owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        this.owner = owner;
    }

    public MeleeWeapon weapon() {
        return weapon;
    }

    public Armor armor() {
        return armor;
    }

    public Ring ringOne() {
        return ringOne;
    }

    public Ring ringTwo() {
        return ringTwo;
    }

    public List<Item> backpack() {
        return Collections.unmodifiableList(backpack);
    }

    boolean containsInBackpack(Item item) {
        return item != null && backpack.contains(item);
    }

    public boolean canAddToBackpack(Item item) {
        if (!supported(item)) {
            return false;
        }
        if (backpack.contains(item)) {
            return true;
        }
        if (item.stackable) {
            for (Item existing : backpack) {
                if (item.isSimilar(existing)) {
                    return true;
                }
            }
        }
        return backpack.size() < BACKPACK_CAPACITY;
    }

    public boolean addToBackpack(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (!supported(item)) {
            throw new IllegalArgumentException("Unsupported CoHero item: " + item.getClass().getName());
        }
        if (backpack.contains(item)) {
            return true;
        }

        if (item.stackable) {
            for (Item existing : backpack) {
                if (item.isSimilar(existing)) {
                    existing.merge(item);
                    return true;
                }
            }
        }

        if (backpack.size() >= BACKPACK_CAPACITY) {
            return false;
        }

        backpack.add(item);
        if (item instanceof Wand) {
            ((Wand) item).charge(owner);
        }
        return true;
    }

    public Item removeFromBackpack(Item item) {
        if (item == null || !backpack.remove(item)) {
            return null;
        }
        if (item instanceof Wand) {
            ((Wand) item).stopCharging();
        }
        return item;
    }

    public boolean equipWeapon(MeleeWeapon value) {
        if (value == null || !backpack.contains(value)) {
            throw new IllegalArgumentException("Weapon must be in the CoHero backpack before equipping");
        }
        if (equipFailure(value) != EquipFailure.NONE) {
            return false;
        }
        if (weapon != null && cannotUnequip(weapon)) {
            return false;
        }

        removeFromBackpack(value);
        MeleeWeapon previous = weapon;
        weapon = value;
        if (previous != null && !addToBackpack(previous)) {
            throw new IllegalStateException("Weapon swap could not return previous weapon to backpack");
        }
        rebuildWandCharging();
        return true;
    }

    public boolean equipArmor(Armor value) {
        if (value == null || !backpack.contains(value)) {
            throw new IllegalArgumentException("Armor must be in the CoHero backpack before equipping");
        }
        if (equipFailure(value) != EquipFailure.NONE) {
            return false;
        }
        if (armor != null && cannotUnequip(armor)) {
            return false;
        }

        removeFromBackpack(value);
        Armor previous = armor;
        armor = value;
        if (previous != null && !addToBackpack(previous)) {
            throw new IllegalStateException("Armor swap could not return previous armor to backpack");
        }
        rebuildArmorEffects();
        return true;
    }

    public boolean equipRing(Ring value, int slot) {
        if (slot != 1 && slot != 2) {
            throw new IllegalArgumentException("Ring slot must be 1 or 2");
        }
        if (value == null || !backpack.contains(value)) {
            throw new IllegalArgumentException("Ring must be in the CoHero backpack before equipping");
        }

        if (equipFailure(value) != EquipFailure.NONE) {
            return false;
        }

        Ring previous = slot == 1 ? ringOne : ringTwo;
        if (previous != null && cannotUnequip(previous)) {
            return false;
        }

        removeFromBackpack(value);
        if (slot == 1) {
            ringOne = value;
        } else {
            ringTwo = value;
        }
        if (previous != null && !addToBackpack(previous)) {
            throw new IllegalStateException("Ring swap could not return previous ring to backpack");
        }
        rebuildRingBuffs();
        return true;
    }

    public boolean unequipWeaponToBackpack() {
        if (weapon == null) {
            return true;
        }
        if (cannotUnequip(weapon) || !canAddToBackpack(weapon)) {
            return false;
        }
        MeleeWeapon previous = weapon;
        weapon = null;
        if (!addToBackpack(previous)) {
            throw new IllegalStateException("Backpack capacity changed during weapon unequip");
        }
        rebuildWandCharging();
        return true;
    }

    public boolean unequipArmorToBackpack() {
        if (armor == null) {
            return true;
        }
        if (cannotUnequip(armor) || !canAddToBackpack(armor)) {
            return false;
        }
        Armor previous = armor;
        armor = null;
        if (!addToBackpack(previous)) {
            throw new IllegalStateException("Backpack capacity changed during armor unequip");
        }
        rebuildArmorEffects();
        return true;
    }

    public boolean unequipRingToBackpack(int slot) {
        if (slot != 1 && slot != 2) {
            throw new IllegalArgumentException("Ring slot must be 1 or 2");
        }
        Ring previous = slot == 1 ? ringOne : ringTwo;
        if (previous == null) {
            return true;
        }
        if (cannotUnequip(previous) || !canAddToBackpack(previous)) {
            return false;
        }

        if (slot == 1) {
            ringOne = null;
        } else {
            ringTwo = null;
        }
        if (!addToBackpack(previous)) {
            throw new IllegalStateException("Backpack capacity changed during ring unequip");
        }
        rebuildRingBuffs();
        return true;
    }

    public EquipFailure equipFailure(Item item) {
        if (!(item instanceof MeleeWeapon) && !(item instanceof Armor) && !(item instanceof Ring)) {
            throw new IllegalArgumentException("Item cannot be equipped by CoHero: "
                    + (item == null ? "null" : item.getClass().getName()));
        }

        // Match DriedRose.GhostHero outfitting: the item must be known to be uncursed.
        if (item.cursed || !item.cursedKnown) {
            return EquipFailure.CURSED_OR_UNKNOWN;
        }

        // When the upgrade level is unknown, use the +0 requirement so this check cannot leak
        // the item's hidden level. This is the same rule used by DriedRose.GhostHero.
        if (item instanceof MeleeWeapon) {
            MeleeWeapon weapon = (MeleeWeapon) item;
            int requirement = item.levelKnown ? weapon.STRReq() : weapon.STRReq(0);
            if (requirement > owner.STR()) {
                return item.levelKnown ? EquipFailure.TOO_HEAVY : EquipFailure.TOO_HEAVY_UNKNOWN;
            }
        } else if (item instanceof Armor) {
            Armor armor = (Armor) item;
            int requirement = item.levelKnown ? armor.STRReq() : armor.STRReq(0);
            if (requirement > owner.STR()) {
                return item.levelKnown ? EquipFailure.TOO_HEAVY : EquipFailure.TOO_HEAVY_UNKNOWN;
            }
        }

        return EquipFailure.NONE;
    }

    public boolean cannotUnequip(Item item) {
        return item != null && item.cursed && owner.buff(MagicImmune.class) == null;
    }

    public enum EquipFailure {
        NONE,
        CURSED_OR_UNKNOWN,
        TOO_HEAVY_UNKNOWN,
        TOO_HEAVY
    }

    void storeInBundle(Bundle bundle) {
        bundle.put(FORMAT, FORMAT_VERSION);
        bundle.put(WEAPON, weapon);
        bundle.put(ARMOR, armor);
        bundle.put(RING_ONE, ringOne);
        bundle.put(RING_TWO, ringTwo);
        bundle.put(BACKPACK, backpack);
    }

    void restoreFromBundle(Bundle bundle) {
        if (!bundle.contains(FORMAT) || bundle.getInt(FORMAT) != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported CoHero inventory save format");
        }

        weapon = (MeleeWeapon) bundle.get(WEAPON);
        armor = (Armor) bundle.get(ARMOR);
        ringOne = (Ring) bundle.get(RING_ONE);
        ringTwo = (Ring) bundle.get(RING_TWO);

        backpack.clear();
        for (Bundlable value : bundle.getCollection(BACKPACK)) {
            if (!(value instanceof Item) || !supported((Item) value)) {
                throw new IllegalStateException("CoHero save contains an unsupported backpack item");
            }
            backpack.add((Item) value);
        }
        if (backpack.size() > BACKPACK_CAPACITY) {
            throw new IllegalStateException("CoHero save contains too many backpack slots: " + backpack.size());
        }
        owner.updateArmorSprite();
    }

    void rebuildPassiveEffects() {
        rebuildArmorEffects();
        rebuildRingBuffs();
        rebuildWandCharging();
    }

    private void rebuildArmorEffects() {
        Buff.detach(owner, BrokenSeal.WarriorShield.class);
        if (armor != null && armor.checkSeal() != null) {
            armor.activate(owner);
        }
        owner.updateArmorSprite();
    }

    private void rebuildRingBuffs() {
        ArrayList<Buff> existing = new ArrayList<>(owner.buffs());
        for (Buff buff : existing) {
            if (buff instanceof Ring.RingBuff) {
                buff.detach();
            }
        }

        if (ringOne != null) {
            ringOne.activate(owner);
        }
        if (ringTwo != null) {
            ringTwo.activate(owner);
        }
        owner.updateHT(false);
    }

    private void rebuildWandCharging() {
        ArrayList<Buff> existing = new ArrayList<>(owner.buffs());
        for (Buff buff : existing) {
            if (buff instanceof Wand.Charger) {
                buff.detach();
            }
        }

        for (Item item : backpack) {
            if (item instanceof Wand) {
                Wand wand = (Wand) item;
                wand.stopCharging();
                wand.charge(owner);
            }
        }
        if (weapon instanceof MagesStaff) {
            ((MagesStaff) weapon).applyWandChargeBuff(owner);
        }
    }

    static boolean supported(Item item) {
        return item instanceof Weapon
                || item instanceof Armor
                || item instanceof Ring
                || item instanceof Wand;
    }
}
