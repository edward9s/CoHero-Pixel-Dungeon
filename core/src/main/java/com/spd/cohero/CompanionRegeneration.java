package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Regeneration;
import com.watabou.utils.Bundle;

/**
 * Hero-like base regeneration without Hero-only hunger/artifact assumptions.
 * CoHero currently regenerates 1 HP per 10 turns and has no hunger model.
 */
public final class CompanionRegeneration extends Buff {

    private static final float REGENERATION_DELAY = 10f;
    private static final String PARTIAL_REGEN = "cohero_partial_regen";

    private float partialRegen;

    {
        actPriority = HERO_PRIO - 1;
    }

    @Override
    public boolean act() {
        if (!(target instanceof CoHeroAlly)) {
            throw new IllegalStateException("CompanionRegeneration attached to non-CoHero target");
        }

        if (!target.isAlive()) {
            diactivate();
            return true;
        }

        if (Regeneration.regenOn() && target.HP < target.HT) {
            partialRegen += 1f / REGENERATION_DELAY;
            if (partialRegen >= 1f) {
                int heal = (int) partialRegen;
                target.HP = Math.min(target.HT, target.HP + heal);
                partialRegen -= heal;
            }
        }

        spend(TICK);
        return true;
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(PARTIAL_REGEN, partialRegen);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        partialRegen = bundle.getFloat(PARTIAL_REGEN);
    }
}
