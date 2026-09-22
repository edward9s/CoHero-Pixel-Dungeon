package com.spd.cohero;

/**
 * Snapshot of CoHero's current combat-survival race.
 */
final class CoHeroCombatRisk {

    final boolean retreat;
    final int attackersNow;
    final float incomingDpt;
    final float immediateIncoming;
    final float ttd;
    final float ttk;

    CoHeroCombatRisk(
            boolean retreat,
            int attackersNow,
            float incomingDpt,
            float immediateIncoming,
            float ttd,
            float ttk) {
        this.retreat = retreat;
        this.attackersNow = attackersNow;
        this.incomingDpt = incomingDpt;
        this.immediateIncoming = immediateIncoming;
        this.ttd = ttd;
        this.ttk = ttk;
    }
}
