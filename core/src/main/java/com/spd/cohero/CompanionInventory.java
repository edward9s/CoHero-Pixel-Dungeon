package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfHoneyedHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCleansing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfEarthenArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShielding;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStamina;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
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
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CoHero-owned inventory model.
 *
 * The companion has a normal 20-slot backpack plus explicit equipment slots. Supported combat
 * equipment is weapon, armor, rings and wands. Potions and scrolls may be stored so unidentified
 * identities are never leaked by the transfer UI, but only explicitly supported survival
 * consumables are used autonomously. Artifacts, trinkets, bags and unknown items are rejected.
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

    public List<MissileWeapon> missileWeapons() {
        ArrayList<MissileWeapon> result = new ArrayList<>();
        for (Item item : backpack) {
            if (item instanceof MissileWeapon) {
                result.add((MissileWeapon) item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<Wand> wands() {
        ArrayList<Wand> result = new ArrayList<>();
        for (Item item : backpack) {
            if (item instanceof Wand) {
                result.add((Wand) item);
            }
        }

        // Mage's Staff owns its wand internally rather than as a backpack item. Expose that same
        // stock wand to the CoHero AI without creating a duplicate weapon or duplicate charge pool.
        if (weapon instanceof MagesStaff) {
            Wand embedded = ((MagesStaff) weapon).coHeroWand();
            if (embedded != null) {
                result.add(embedded);
            }
        }
        return Collections.unmodifiableList(result);
    }

    SpiritBow spiritBow() {
        for (Item item : backpack) {
            if (item instanceof SpiritBow) {
                return (SpiritBow) item;
            }
        }
        return null;
    }

    Ankh takeAnkhForRevive() {
        Ankh selected = null;
        for (Item item : backpack) {
            if (item instanceof Ankh) {
                Ankh ankh = (Ankh) item;
                if (selected == null || ankh.isBlessed()) {
                    selected = ankh;
                }
                if (ankh.isBlessed()) {
                    break;
                }
            }
        }

        if (selected == null) {
            return null;
        }

        Item removed = removeFromBackpack(selected);
        if (!(removed instanceof Ankh)) {
            throw new IllegalStateException("CoHero ankh disappeared before revival");
        }
        return (Ankh) removed;
    }

    Torch takeOneAutoTorch() {
        Torch source = null;
        for (Item item : backpack) {
            if (item instanceof Torch) {
                source = (Torch) item;
                break;
            }
        }
        if (source == null) {
            return null;
        }

        if (source.quantity() > 1) {
            Item split = source.split(1);
            if (!(split instanceof Torch)) {
                throw new IllegalStateException("CoHero torch stack could not split");
            }
            return (Torch) split;
        }

        Item removed = removeFromBackpack(source);
        if (!(removed instanceof Torch)) {
            throw new IllegalStateException("CoHero torch disappeared before use");
        }
        return (Torch) removed;
    }

    Potion takeOneAutoHealingPotion() {
        return takeOneKnownPotion(PotionOfHealing.class, ElixirOfHoneyedHealing.class);
    }

    Potion takeOneAutoShieldingPotion() {
        return takeOneKnownPotion(PotionOfShielding.class);
    }

    Potion takeOneAutoInvisibilityPotion() {
        return takeOneKnownPotion(PotionOfInvisibility.class);
    }

    Potion takeOneAutoHastePotion() {
        return takeOneKnownPotion(PotionOfHaste.class);
    }

    Potion takeOneAutoStaminaPotion() {
        return takeOneKnownPotion(PotionOfStamina.class);
    }

    Potion takeOneAutoCleansingPotion() {
        return takeOneKnownPotion(PotionOfCleansing.class);
    }

    Potion takeOneAutoEarthenArmorPotion() {
        return takeOneKnownPotion(PotionOfEarthenArmor.class);
    }

    Scroll takeOneAutoTeleportationScroll() {
        return takeOneKnownScroll(ScrollOfTeleportation.class);
    }

    Scroll takeOneAutoTerrorScroll() {
        return takeOneKnownScroll(ScrollOfTerror.class);
    }

    Scroll takeOneAutoDreadScroll() {
        return takeOneKnownScroll(ScrollOfDread.class);
    }

    boolean hasCombatRunestone(Class<? extends Runestone> type) {
        if (type == null) {
            return false;
        }
        for (Item item : backpack) {
            if (type.isInstance(item)) {
                return true;
            }
        }
        return false;
    }

    Runestone takeOneCombatRunestone(Class<? extends Runestone> type) {
        if (type == null) {
            return null;
        }

        Runestone source = null;
        for (Item item : backpack) {
            if (type.isInstance(item)) {
                source = (Runestone) item;
                break;
            }
        }
        if (source == null) {
            return null;
        }

        if (source.quantity() > 1) {
            Item split = source.split(1);
            if (!(split instanceof Runestone)) {
                throw new IllegalStateException("CoHero runestone stack could not split");
            }
            return (Runestone) split;
        }

        Item removed = removeFromBackpack(source);
        if (!(removed instanceof Runestone)) {
            throw new IllegalStateException("CoHero runestone disappeared before use");
        }
        return (Runestone) removed;
    }

    int autoHealingPotionCount() {
        return countKnownPotions(PotionOfHealing.class, ElixirOfHoneyedHealing.class);
    }

    int autoShieldingPotionCount() {
        return countKnownPotions(PotionOfShielding.class);
    }

    @SafeVarargs
    private final int countKnownPotions(Class<? extends Potion>... types) {
        int count = 0;
        for (Item item : backpack) {
            if (!(item instanceof Potion)) {
                continue;
            }
            Potion potion = (Potion) item;
            if (!potion.isKnown()) {
                continue;
            }
            for (Class<? extends Potion> type : types) {
                if (type.isInstance(potion)) {
                    count += Math.max(1, potion.quantity());
                    break;
                }
            }
        }
        return count;
    }

    @SafeVarargs
    private final Potion takeOneKnownPotion(Class<? extends Potion>... types) {
        Potion source = null;
        for (Item item : backpack) {
            if (!(item instanceof Potion)) {
                continue;
            }
            Potion potion = (Potion) item;
            if (!potion.isKnown()) {
                continue;
            }
            for (Class<? extends Potion> type : types) {
                if (type.isInstance(potion)) {
                    source = potion;
                    break;
                }
            }
            if (source != null) {
                break;
            }
        }

        if (source == null) {
            return null;
        }

        if (source.quantity() > 1) {
            Item split = source.split(1);
            if (!(split instanceof Potion)) {
                throw new IllegalStateException("CoHero potion stack could not split");
            }
            return (Potion) split;
        }

        Item removed = removeFromBackpack(source);
        if (!(removed instanceof Potion)) {
            throw new IllegalStateException("CoHero potion disappeared before use");
        }
        return (Potion) removed;
    }

    @SafeVarargs
    private final Scroll takeOneKnownScroll(Class<? extends Scroll>... types) {
        Scroll source = null;
        for (Item item : backpack) {
            if (!(item instanceof Scroll)) {
                continue;
            }
            Scroll scroll = (Scroll) item;
            if (!scroll.isKnown()) {
                continue;
            }
            for (Class<? extends Scroll> type : types) {
                if (type.isInstance(scroll)) {
                    source = scroll;
                    break;
                }
            }
            if (source != null) {
                break;
            }
        }

        if (source == null) {
            return null;
        }

        if (source.quantity() > 1) {
            Item split = source.split(1);
            if (!(split instanceof Scroll)) {
                throw new IllegalStateException("CoHero scroll stack could not split");
            }
            return (Scroll) split;
        }

        Item removed = removeFromBackpack(source);
        if (!(removed instanceof Scroll)) {
            throw new IllegalStateException("CoHero scroll disappeared before use");
        }
        return (Scroll) removed;
    }

    boolean containsInBackpack(Item item) {
        return item != null && backpack.contains(item);
    }

    public boolean canAddToBackpack(Item item) {
        if (item == null) {
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
        if (item instanceof Wand && CoHeroWandAdapter.supported((Wand) item)) {
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

    public Item removeOneFromBackpack(Item item) {
        if (item == null || !backpack.contains(item)) {
            return null;
        }
        if (item.quantity() <= 1) {
            return removeFromBackpack(item);
        }

        Item split = item.split(1);
        if (split == null) {
            throw new IllegalStateException("CoHero backpack stack could not split one item");
        }
        return split;
    }

    MissileWeapon takeOneMissile(MissileWeapon source) {
        if (source == null || !backpack.contains(source)) {
            throw new IllegalArgumentException("Missile weapon must be in the CoHero backpack");
        }

        if (source.quantity() > 1) {
            Item split = source.split(1);
            if (!(split instanceof MissileWeapon)) {
                throw new IllegalStateException("Missile stack could not split one projectile");
            }
            return (MissileWeapon) split;
        }

        Item removed = removeFromBackpack(source);
        if (!(removed instanceof MissileWeapon)) {
            throw new IllegalStateException("Missile weapon disappeared during throw");
        }
        return (MissileWeapon) removed;
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

        if (!knownUncursed(item)) {
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

    boolean autoEquipUpgrade(Item item) {
        if (item instanceof MeleeWeapon) {
            return autoEquipWeaponUpgrade((MeleeWeapon) item);
        }
        if (item instanceof Armor) {
            return autoEquipArmorUpgrade((Armor) item);
        }
        return false;
    }

    private boolean autoEquipWeaponUpgrade(MeleeWeapon candidate) {
        if (!backpack.contains(candidate) || equipFailure(candidate) != EquipFailure.NONE) {
            return false;
        }
        if (weapon != null && meleePower(candidate) <= meleePower(weapon)) {
            return false;
        }
        return equipWeapon(candidate);
    }

    private boolean autoEquipArmorUpgrade(Armor candidate) {
        if (!backpack.contains(candidate) || equipFailure(candidate) != EquipFailure.NONE) {
            return false;
        }
        if (armor != null && armorProtection(candidate) <= armorProtection(armor)) {
            return false;
        }
        return equipArmor(candidate);
    }

    private float meleePower(MeleeWeapon value) {
        int min = value.levelKnown ? value.min() : value.min(0);
        int max = value.levelKnown ? value.max() : value.max(0);
        int strengthRequirement = value.levelKnown ? value.STRReq() : value.STRReq(0);
        float averageDamage = value.augment.damageFactor((min + max) / 2f);
        int excessStrength = Math.max(0, owner.STR() - strengthRequirement);
        averageDamage += excessStrength / 2f;
        return averageDamage / Math.max(0.01f, value.delayFactor(owner));
    }

    private float armorProtection(Armor value) {
        int min = value.levelKnown ? value.DRMin() : value.DRMin(0);
        int max = value.levelKnown ? value.DRMax() : value.DRMax(0);
        int strengthRequirement = value.levelKnown ? value.STRReq() : value.STRReq(0);
        int encumbrance = Math.max(0, strengthRequirement - owner.STR());
        return (Math.max(0, min - 2 * encumbrance)
                + Math.max(0, max - 2 * encumbrance)) / 2f;
    }

    void gainIdentificationExp(float levelPercent) {
        if (levelPercent <= 0f) {
            return;
        }
        if (weapon != null) {
            weapon.coHeroGainIdentificationExp(levelPercent);
        }
        if (armor != null) {
            armor.coHeroGainIdentificationExp(levelPercent);
        }
        if (ringOne != null) {
            ringOne.coHeroGainIdentificationExp(levelPercent);
        }
        if (ringTwo != null) {
            ringTwo.coHeroGainIdentificationExp(levelPercent);
        }
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
            if (!(value instanceof Item)) {
                throw new IllegalStateException("CoHero save contains a non-item backpack entry");
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
        // BrokenSeal.WarriorShield is Hero-only in stock SPD, so CoHero does not activate it.
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
            if (item instanceof Wand && CoHeroWandAdapter.supported((Wand) item)) {
                Wand wand = (Wand) item;
                wand.stopCharging();
                wand.charge(owner);
            }
        }
        if (weapon instanceof MagesStaff) {
            ((MagesStaff) weapon).applyWandChargeBuff(owner);
        }
    }

    boolean canUse(Item item) {
        if (item == null) {
            return false;
        }

        if (item instanceof MeleeWeapon
                || item instanceof Armor
                || item instanceof Ring) {
            return equipFailure(item) == EquipFailure.NONE;
        }

        if (item instanceof SpiritBow) {
            return knownUncursed(item);
        }

        if (item instanceof MissileWeapon) {
            return CoHeroMissileAdapter.supported((MissileWeapon) item);
        }

        if (item instanceof Wand) {
            return knownUncursed(item)
                    && CoHeroWandAdapter.supported((Wand) item);
        }

        if (item instanceof Torch || item instanceof Ankh) {
            return true;
        }

        if (item instanceof Potion) {
            Potion potion = (Potion) item;
            return potion.isKnown()
                    && (potion instanceof PotionOfHealing
                    || potion instanceof ElixirOfHoneyedHealing
                    || potion instanceof PotionOfShielding
                    || potion instanceof PotionOfInvisibility
                    || potion instanceof PotionOfHaste
                    || potion instanceof PotionOfStamina
                    || potion instanceof PotionOfCleansing
                    || potion instanceof PotionOfEarthenArmor);
        }

        if (item instanceof Scroll) {
            Scroll scroll = (Scroll) item;
            return scroll.isKnown()
                    && (scroll instanceof ScrollOfTeleportation
                    || scroll instanceof ScrollOfTerror
                    || scroll instanceof ScrollOfDread);
        }

        return item instanceof StoneOfAggression
                || item instanceof StoneOfBlast
                || item instanceof StoneOfFear
                || item instanceof StoneOfDeepSleep
                || item instanceof StoneOfBlink
                || item instanceof StoneOfFlock;
    }

    private static boolean knownUncursed(Item item) {
        return item != null && item.cursedKnown && !item.cursed;
    }
}
