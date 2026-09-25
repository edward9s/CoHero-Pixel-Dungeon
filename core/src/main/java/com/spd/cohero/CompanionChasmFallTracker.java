package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.watabou.utils.Bundle;

/**
 * Persists which party members actually fell while the stock Hero fall transition changes floors.
 *
 * The tracker lives on Dungeon.hero because the Hero is the actor carried through the stock
 * Chasm.Falling lifecycle. It is consumed on the destination floor before landing effects resolve.
 */
public final class CompanionChasmFallTracker extends Buff {

    private static final String HERO_ALSO_FELL = "cohero_chasm_hero_also_fell";

    private boolean heroAlsoFell;

    void includeHeroFall(boolean value) {
        heroAlsoFell |= value;
    }

    boolean heroAlsoFell() {
        return heroAlsoFell;
    }

    @Override
    public boolean act() {
        spend(TICK);
        return true;
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(HERO_ALSO_FELL, heroAlsoFell);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        heroAlsoFell = bundle.getBoolean(HERO_ALSO_FELL);
    }
}
