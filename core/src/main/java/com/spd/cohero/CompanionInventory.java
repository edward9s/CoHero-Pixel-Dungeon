package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CoHero-owned inventory model.
 *
 * This intentionally does not reuse Hero/Belongings. The companion only supports the item
 * categories that have explicit CoHero semantics: one melee weapon, one armor, two rings and
 * wands. Artifacts, trinkets and consumables are not accepted here.
 */
public final class CompanionInventory {

    public static final int MAX_WANDS = 20;

    private static final String WEAPON = "cohero_weapon";
    private static final String ARMOR = "cohero_armor";
    private static final String RING_ONE = "cohero_ring_one";
    private static final String RING_TWO = "cohero_ring_two";
    private static final String WANDS = "cohero_wands";

    private final CompanionHero owner;

    private MeleeWeapon weapon;
    private Armor armor;
    private Ring ringOne;
    private Ring ringTwo;
    private final ArrayList<Wand> wands = new ArrayList<>();

    CompanionInventory(CompanionHero owner) {
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

    public List<Wand> wands() {
        return Collections.unmodifiableList(wands);
    }

    public boolean hasWandSpace() {
        return wands.size() < MAX_WANDS;
    }

    public void equipWeapon(MeleeWeapon value) {
        weapon = value;
    }

    public void equipArmor(Armor value) {
        armor = value;
    }

    public void equipRingOne(Ring value) {
        ringOne = value;
        rebuildRingBuffs();
    }

    public void equipRingTwo(Ring value) {
        ringTwo = value;
        rebuildRingBuffs();
    }

    public MeleeWeapon removeWeapon() {
        MeleeWeapon result = weapon;
        weapon = null;
        return result;
    }

    public Armor removeArmor() {
        Armor result = armor;
        armor = null;
        return result;
    }

    public Ring removeRingOne() {
        Ring result = ringOne;
        ringOne = null;
        rebuildRingBuffs();
        return result;
    }

    public Ring removeRingTwo() {
        Ring result = ringTwo;
        ringTwo = null;
        rebuildRingBuffs();
        return result;
    }

    public void addWand(Wand wand) {
        if (wand == null) {
            throw new IllegalArgumentException("wand must not be null");
        }
        if (!hasWandSpace()) {
            throw new IllegalStateException("CoHero wand inventory is full");
        }
        if (wands.contains(wand)) {
            throw new IllegalStateException("Wand is already in the CoHero inventory");
        }
        wands.add(wand);
        wand.charge(owner);
    }

    public Wand removeWand(int index) {
        Wand wand = wands.remove(index);
        wand.stopCharging();
        return wand;
    }

    void storeInBundle(Bundle bundle) {
        bundle.put(WEAPON, weapon);
        bundle.put(ARMOR, armor);
        bundle.put(RING_ONE, ringOne);
        bundle.put(RING_TWO, ringTwo);
        bundle.put(WANDS, wands);
    }

    void restoreFromBundle(Bundle bundle) {
        weapon = (MeleeWeapon) bundle.get(WEAPON);
        armor = (Armor) bundle.get(ARMOR);
        ringOne = (Ring) bundle.get(RING_ONE);
        ringTwo = (Ring) bundle.get(RING_TWO);

        wands.clear();
        for (Bundlable value : bundle.getCollection(WANDS)) {
            if (!(value instanceof Wand)) {
                throw new IllegalStateException("CoHero save contains a non-wand in wand inventory");
            }
            wands.add((Wand) value);
        }
        if (wands.size() > MAX_WANDS) {
            throw new IllegalStateException("CoHero save contains too many wands: " + wands.size());
        }
    }

    void rebuildPassiveEffects() {
        rebuildRingBuffs();
        rebuildWandCharging();
    }

    private void rebuildRingBuffs() {
        // Ring.activate(Char) is already Char-generic. Rebuilding instead of reaching into the
        // Ring's protected buff field also makes save/load and same-type double rings deterministic.
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
    }

    private void rebuildWandCharging() {
        ArrayList<Buff> existing = new ArrayList<>(owner.buffs());
        for (Buff buff : existing) {
            if (buff instanceof Wand.Charger) {
                buff.detach();
            }
        }

        for (Wand wand : wands) {
            wand.stopCharging();
            wand.charge(owner);
        }
    }

    static boolean supported(Item item) {
        return item instanceof MeleeWeapon
                || item instanceof Armor
                || item instanceof Ring
                || item instanceof Wand;
    }
}
