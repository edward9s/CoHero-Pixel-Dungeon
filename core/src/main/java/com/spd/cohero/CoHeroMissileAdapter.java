package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Bolas;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.FishingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Javelin;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Kunai;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingClub;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingHammer;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Tomahawk;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Trident;

final class CoHeroMissileAdapter {

    private CoHeroMissileAdapter() {
    }

    static boolean supported(MissileWeapon missile) {
        // Only stock projectile types that use the standard rangedHit/rangedMiss path are enabled.
        // Exact classes are intentional: unknown fork projectile semantics fail closed here.
        Class<?> type = missile.getClass();
        return type == ThrowingStone.class
                || type == ThrowingKnife.class
                || type == ThrowingSpike.class
                || type == FishingSpear.class
                || type == ThrowingClub.class
                || type == ThrowingSpear.class
                || type == Kunai.class
                || type == Bolas.class
                || type == Javelin.class
                || type == Tomahawk.class
                || type == Trident.class
                || type == ThrowingHammer.class;
    }

    static float expectedDamage(CoHeroAlly owner, MissileWeapon missile) {
        int level = missile.buffedLvl()
                + RingOfSharpshooting.levelDamageBonus(owner)
                + CoHeroClassTraits.missileLevelBonus(owner);
        float average = (missile.min(level) + missile.max(level)) / 2f;
        average = missile.augment.damageFactor(average);
        int excessStrength = owner.STR() - missile.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
    }

    static float expectedSpiritBowDamage(CoHeroAlly owner, SpiritBow bow) {
        float average = (bow.coHeroMin(owner) + bow.coHeroMax(owner)) / 2f;
        average = bow.augment.damageFactor(average);
        int excessStrength = owner.STR() - bow.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
    }
}
