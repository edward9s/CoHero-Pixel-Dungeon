package com.spd.cohero;

/**
 * Snapshot of CoHero's current combat-survival race.
 */
final class CoHeroCombatRisk {

    final boolean retreat;
    final int attackersNow;
    final float incomingDpt;
    final float immediateIncoming;
    final float outgoingDpt;
    final float ttd;
    final float ttk;

    // Relative enemy count is not an emergency on its own. Hero's ability to fight alongside
    // CoHero lets the party accept a longer fight, but cannot override imminent death.
    static boolean retreatRequired(boolean immediateLethal, int attackersNow, float survivalTurns,
            boolean losingRace, boolean outnumberedRace, boolean heroEngaged) {
        return immediateLethal
                || survivalTurns <= 3f
                || (attackersNow >= 3 && survivalTurns <= 5f)
                || ((losingRace || outnumberedRace)
                    && (!heroEngaged || survivalTurns <= 5f));
    }

    // Excludes hypothetical healing/shielding potions. They are only spent for a concrete
    // current threat, even when escape movement and control are unavailable.
    static boolean emergencyConsumableRequired(
            float effectiveHp, float incomingDpt, float immediateIncoming) {
        if (incomingDpt < 0f || immediateIncoming < 0f) {
            throw new IllegalArgumentException("Emergency incoming damage must be non-negative");
        }
        if (effectiveHp <= 0f) {
            return false;
        }
        float survivalTurns = incomingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : effectiveHp / incomingDpt;
        return immediateIncoming * 1.35f >= effectiveHp || survivalTurns <= 3f;
    }

    static boolean emergencyHealingWorth(int hp, int maxHp) {
        return maxHp > 0 && hp * 100L <= maxHp * 60L;
    }

    CoHeroCombatRisk(
            boolean retreat,
            int attackersNow,
            float incomingDpt,
            float immediateIncoming,
            float outgoingDpt,
            float ttd,
            float ttk) {
        this.retreat = retreat;
        this.attackersNow = attackersNow;
        this.incomingDpt = incomingDpt;
        this.immediateIncoming = immediateIncoming;
        this.outgoingDpt = outgoingDpt;
        this.ttd = ttd;
        this.ttk = ttk;
    }
}
