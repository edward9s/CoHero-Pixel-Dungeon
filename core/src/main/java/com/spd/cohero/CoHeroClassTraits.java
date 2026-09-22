package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfElements;

public final class CoHeroClassTraits {

    private static final float WARRIOR_MIGHT_HT_MULTIPLIER = 1.035f;
    private static final float TENACITY_BASE = 0.85f;
    private static final float MAGE_WAND_CHARGE_MULTIPLIER = 1.175f;
    private static final float MAGE_ELEMENTS_BASE = 0.825f;
    private static final float ROGUE_MOVE_SPEED_MULTIPLIER = 1.15f;
    private static final float HUNTRESS_MISSILE_DURABILITY_MULTIPLIER = 1.2f;
    private static final float DUELIST_MELEE_SPEED_MULTIPLIER = 1.09051f;
    public static final int CLERIC_AURA_RANGE = 3;
    private static final float GENERALIST_HT_MULTIPLIER = 1.05f;

    private CoHeroClassTraits() {
    }

    public static int strengthBonus(Char target) {
        return isCompanionClass(target, HeroClass.WARRIOR) ? 1 : 0;
    }

    public static float maxHealthMultiplier(Char target) {
        if (!(target instanceof CoHeroAlly)) {
            return 1f;
        }

        HeroClass heroClass = CoHero.companionClass();
        if (heroClass == HeroClass.WARRIOR) {
            return WARRIOR_MIGHT_HT_MULTIPLIER;
        }
        return isStockClass(heroClass) ? 1f : GENERALIST_HT_MULTIPLIER;
    }

    public static float intrinsicTenacityDamageMultiplier(Char target) {
        if (!isCompanionClass(target, HeroClass.WARRIOR)
                && !isCompanionClass(target, HeroClass.DUELIST)) {
            return 1f;
        }
        if (target.HT <= 0) {
            throw new IllegalStateException("Tenacity CoHero has non-positive HT");
        }

        float missingHealthFraction = (float) (target.HT - target.HP) / target.HT;
        return (float) Math.pow(TENACITY_BASE, missingHealthFraction);
    }

    public static float elementsResistanceMultiplier(Char target, Class effect) {
        if (!isCompanionClass(target, HeroClass.MAGE)) {
            return 1f;
        }

        for (Class resistance : RingOfElements.RESISTS) {
            if (resistance.isAssignableFrom(effect)) {
                return MAGE_ELEMENTS_BASE;
            }
        }
        return 1f;
    }

    public static float wandChargeMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.MAGE) ? MAGE_WAND_CHARGE_MULTIPLIER : 1f;
    }

    public static float movementSpeedMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.ROGUE) ? ROGUE_MOVE_SPEED_MULTIPLIER : 1f;
    }

    public static int rogueWealthBonus() {
        CoHeroAlly companion = CoHero.findCompanion();
        return companion != null
                && companion.isAlive()
                && isCompanionClass(companion, HeroClass.ROGUE)
                ? 1
                : 0;
    }

    public static int huntressArcanaBonus(Char target) {
        if (!isCompanionClass(target, HeroClass.HUNTRESS)) {
            return 0;
        }
        return target.buff(MagicImmune.class) == null ? 1 : 0;
    }

    public static int missileLevelBonus(Char target) {
        return isCompanionClass(target, HeroClass.HUNTRESS) ? 1 : 0;
    }

    public static boolean isHuntress(Char target) {
        return isCompanionClass(target, HeroClass.HUNTRESS);
    }

    public static float missileDurabilityMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.HUNTRESS)
                ? HUNTRESS_MISSILE_DURABILITY_MULTIPLIER
                : 1f;
    }

    public static float meleeAttackSpeedMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.DUELIST)
                ? DUELIST_MELEE_SPEED_MULTIPLIER
                : 1f;
    }

    public static boolean isClericBlessed(Char target) {
        if (Dungeon.hero == null || Dungeon.level == null || target == null) {
            return false;
        }

        CoHeroAlly companion = CoHero.findCompanion();
        if (companion == null
                || !companion.isAlive()
                || CoHero.companionClass() != HeroClass.CLERIC) {
            return false;
        }

        if (target == companion) {
            return true;
        }

        return target == Dungeon.hero
                && target.pos >= 0
                && companion.pos >= 0
                && Dungeon.level.distance(companion.pos, target.pos) <= CLERIC_AURA_RANGE;
    }

    private static boolean isCompanionClass(Char target, HeroClass heroClass) {
        return target instanceof CoHeroAlly && CoHero.companionClass() == heroClass;
    }

    private static boolean isStockClass(HeroClass heroClass) {
        if (heroClass == null) {
            return false;
        }
        switch (heroClass) {
            case WARRIOR:
            case MAGE:
            case ROGUE:
            case HUNTRESS:
            case DUELIST:
            case CLERIC:
                return true;
            default:
                return false;
        }
    }
}
