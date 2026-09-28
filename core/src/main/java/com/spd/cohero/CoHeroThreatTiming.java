package com.spd.cohero;

/**
 * One-pass projection of how quickly the current threat set can pressure a candidate cell.
 */
final class CoHeroThreatTiming {

    final int attackersWithinHorizon;
    final float incomingDptWithinHorizon;
    final float nearestAttackTime;

    CoHeroThreatTiming(
            int attackersWithinHorizon,
            float incomingDptWithinHorizon,
            float nearestAttackTime) {
        this.attackersWithinHorizon = attackersWithinHorizon;
        this.incomingDptWithinHorizon = incomingDptWithinHorizon;
        this.nearestAttackTime = nearestAttackTime;
    }
}
