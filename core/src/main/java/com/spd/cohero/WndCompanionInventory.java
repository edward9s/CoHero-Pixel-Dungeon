package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;

/**
 * The companion inventory is now the stock SPD bag UI backed by the companion's real Belongings.
 * Owner-aware WndBag/WndUseItem seams keep all nested item selectors on this Hero.
 */
public final class WndCompanionInventory extends WndBag {

    public WndCompanionInventory(CompanionHero companion) {
        super(requireCompanion(companion).belongings.backpack);
    }

    private static CompanionHero requireCompanion(CompanionHero companion) {
        if (companion == null || !companion.isAlive()) {
            throw new IllegalArgumentException("companion must be alive");
        }
        return companion;
    }
}
