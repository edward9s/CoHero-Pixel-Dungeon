package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLiquidFlame;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfMindVision;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfLullaby;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRage;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Rapier;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;

/**
 * Owner-aware equivalent of HeroClass.initHero().
 *
 * HeroClass.initHero() cannot be called for the companion because several stock starter items use
 * Item.collect(), which is intentionally hard-wired to Dungeon.hero. This initializer mirrors the
 * stock loadout while collecting into the companion's real Belongings.
 */
final class CompanionStartingEquipment {

    private CompanionStartingEquipment() {
    }

    static void initialize(CompanionHero companion, HeroClass heroClass) {
        if (companion == null || heroClass == null) {
            throw new IllegalArgumentException("companion and heroClass must not be null");
        }
        if (companion.belongings.weapon != null
                || companion.belongings.armor != null
                || companion.belongings.artifact != null
                || companion.belongings.misc != null
                || companion.belongings.ring != null
                || companion.belongings.secondWep != null
                || !companion.belongings.backpack.items.isEmpty()) {
            throw new IllegalStateException("CoHero starting equipment can only initialize empty Belongings");
        }

        companion.heroClass = heroClass;
        Talent.initClassTalents(companion);

        ClothArmor cloth = (ClothArmor) new ClothArmor().identify();
        if (!Challenges.isItemBlocked(cloth)) {
            companion.belongings.armor = cloth;
        }

        Food food = new Food();
        if (!Challenges.isItemBlocked(food)) {
            collect(companion, food);
        }

        collect(companion, new VelvetPouch());
        collect(companion, new Waterskin());
        new ScrollOfIdentify().identify();

        switch (heroClass) {
            case WARRIOR:
                companion.belongings.weapon = new WornShortsword().identify();
                collect(companion, new ThrowingStone().identify());
                if (companion.belongings.armor != null) {
                    companion.belongings.armor.affixSeal(new BrokenSeal());
                }
                new PotionOfHealing().identify();
                new ScrollOfRage().identify();
                break;

            case MAGE:
                companion.belongings.weapon = new MagesStaff(new WandOfMagicMissile()).identify();
                new ScrollOfUpgrade().identify();
                new PotionOfLiquidFlame().identify();
                break;

            case ROGUE:
                companion.belongings.weapon = new Dagger().identify();
                companion.belongings.artifact = new CloakOfShadows().identify();
                collect(companion, new ThrowingKnife().identify());
                new ScrollOfMagicMapping().identify();
                new PotionOfInvisibility().identify();
                break;

            case HUNTRESS:
                companion.belongings.weapon = new Gloves().identify();
                collect(companion, new SpiritBow().identify());
                new PotionOfMindVision().identify();
                new ScrollOfLullaby().identify();
                break;

            case DUELIST:
                companion.belongings.weapon = new Rapier().identify();
                ThrowingSpike spikes = new ThrowingSpike();
                spikes.quantity(2).identify();
                collect(companion, spikes);
                new PotionOfStrength().identify();
                new ScrollOfMirrorImage().identify();
                break;

            case CLERIC:
                companion.belongings.weapon = new Cudgel().identify();
                companion.belongings.artifact = new HolyTome().identify();
                new PotionOfPurity().identify();
                new ScrollOfRemoveCurse().identify();
                break;

            default:
                throw new IllegalStateException("Unsupported CoHero starting HeroClass: " + heroClass);
        }

        if (companion.belongings.weapon != null) {
            companion.belongings.weapon.activate(companion);
        }
        if (companion.belongings.armor != null) {
            companion.belongings.armor.activate(companion);
        }
        if (companion.belongings.artifact != null) {
            companion.belongings.artifact.activate(companion);
        }
    }

    private static void collect(CompanionHero companion, Item item) {
        if (!item.collect(companion.belongings.backpack)) {
            throw new IllegalStateException(
                    "CoHero starter item did not fit in backpack: " + item.getClass().getName());
        }
    }
}
