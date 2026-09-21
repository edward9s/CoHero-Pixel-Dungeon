package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;

public final class CoHeroClassTraits {

    private static final float WARRIOR_MIGHT_HT_MULTIPLIER = 1.035f;
    private static final float MAGE_WAND_CHARGE_MULTIPLIER = 1.175f;
    private static final float ROGUE_MOVE_SPEED_MULTIPLIER = 1.15f;
    private static final float HUNTRESS_MISSILE_DURABILITY_MULTIPLIER = 1.2f;
    private static final float DUELIST_MELEE_SPEED_MULTIPLIER = 1.09051f;
    private static final float CLERIC_AURA_MULTIPLIER = 1.10f;
    public static final int CLERIC_AURA_RANGE = 8;
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

    public static float wandChargeMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.MAGE) ? MAGE_WAND_CHARGE_MULTIPLIER : 1f;
    }

    public static float movementSpeedMultiplier(Char target) {
        return isCompanionClass(target, HeroClass.ROGUE) ? ROGUE_MOVE_SPEED_MULTIPLIER : 1f;
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

    public static float clericAuraMultiplier(Char target) {
        if (Dungeon.hero == null || Dungeon.level == null || target == null) {
            return 1f;
        }

        CoHeroAlly companion = CoHero.findCompanion();
        if (companion == null
                || !companion.isAlive()
                || CoHero.companionClass() != HeroClass.CLERIC
                || (target != Dungeon.hero && target != companion)
                || target.pos < 0
                || companion.pos < 0) {
            return 1f;
        }

        if (target == companion) {
            return CLERIC_AURA_MULTIPLIER;
        }

        return Dungeon.level.distance(companion.pos, target.pos) <= CLERIC_AURA_RANGE
                ? CLERIC_AURA_MULTIPLIER
                : 1f;
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
