package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Rapier;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;

/** Builds the supported portion of the selected HeroClass's stock SPD starting loadout. */
final class CompanionStartingEquipment {

    private CompanionStartingEquipment() {
    }

    static void initialize(CoHeroAlly companion, HeroClass heroClass) {
        if (companion == null || heroClass == null) {
            throw new IllegalArgumentException("companion and heroClass must not be null");
        }

        CompanionInventory inventory = companion.inventory();
        if (inventory.weapon() != null
                || inventory.armor() != null
                || inventory.ringOne() != null
                || inventory.ringTwo() != null
                || !inventory.backpack().isEmpty()) {
            throw new IllegalStateException("CoHero starting equipment can only initialize an empty inventory");
        }

        ClothArmor cloth = identified(new ClothArmor());
        if (!Challenges.isItemBlocked(cloth)) {
            if (heroClass == HeroClass.WARRIOR) {
                cloth.affixSeal(new BrokenSeal());
            }
            add(inventory, cloth);
            if (!inventory.equipArmor(cloth)) {
                throw new IllegalStateException("CoHero could not equip starting cloth armor");
            }
        }

        switch (heroClass) {
            case WARRIOR:
                equipWeapon(inventory, identified(new WornShortsword()));
                add(inventory, identified(new ThrowingStone()));
                break;

            case MAGE:
                equipWeapon(inventory, identified(new MagesStaff(new WandOfMagicMissile())));
                break;

            case ROGUE:
                equipWeapon(inventory, identified(new Dagger()));
                add(inventory, identified(new ThrowingKnife()));
                // Cloak of Shadows is intentionally excluded: CoHero artifacts are unsupported.
                break;

            case HUNTRESS:
                equipWeapon(inventory, identified(new Gloves()));
                add(inventory, identified(new SpiritBow()));
                break;

            case DUELIST:
                equipWeapon(inventory, identified(new Rapier()));
                ThrowingSpike spikes = new ThrowingSpike();
                spikes.quantity(2);
                add(inventory, identified(spikes));
                break;

            case CLERIC:
                equipWeapon(inventory, identified(new Cudgel()));
                // Holy Tome is intentionally excluded: CoHero artifacts are unsupported.
                break;

            default:
                throw new IllegalStateException("Unsupported CoHero starting HeroClass: " + heroClass);
        }
    }

    private static void equipWeapon(CompanionInventory inventory, MeleeWeapon weapon) {
        add(inventory, weapon);
        if (!inventory.equipWeapon(weapon)) {
            throw new IllegalStateException("CoHero could not equip starting weapon: " + weapon.getClass().getName());
        }
    }

    private static void add(CompanionInventory inventory, Item item) {
        if (!inventory.addToBackpack(item)) {
            throw new IllegalStateException("CoHero starting item did not fit in backpack: " + item.getClass().getName());
        }
    }

    private static <T extends Item> T identified(T item) {
        item.identify();
        return item;
    }
}
