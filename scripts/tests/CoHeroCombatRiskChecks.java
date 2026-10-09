package com.spd.cohero;

public final class CoHeroCombatRiskChecks {
    private static int checks;

    private static void check(boolean value, String explanation) {
        if (!value) {
            throw new AssertionError(explanation);
        }
        checks++;
    }

    public static void main(String[] args) {
        // Three weak flying swarms while Hero is fighting should not force retreat.
        check(!CoHeroCombatRisk.retreatRequired(false, 3, 10f,
                true, true, true), "assisted swarm at healthy HP");
        check(CoHeroCombatRisk.retreatRequired(false, 3, 10f,
                true, true, false), "same losing fight while alone");
        check(!CoHeroCombatRisk.retreatRequired(false, 3, 20f,
                false, false, false), "enemy count is not sufficient");
        check(CoHeroCombatRisk.retreatRequired(false, 3, 4f,
                false, false, true), "outnumbered and dangerous despite Hero");
        check(CoHeroCombatRisk.retreatRequired(true, 1, 20f,
                false, false, true), "immediate lethal risk");
        check(CoHeroCombatRisk.retreatRequired(false, 1, 2f,
                false, false, true), "critical time to death");
        check(!CoHeroCombatRisk.retreatRequired(false, 2, 8f,
                true, true, true), "Hero can help win the race");
        check(CoHeroCombatRisk.retreatRequired(false, 2, 8f,
                true, true, false), "unassisted losing race");

        // Sleeping enemies remain protected unless they block every safe approach
        // during an unfavorable active ranged exchange.
        check(!CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                true, true, 0f, 8f), "safe path protects sleeping enemies");
        check(!CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                false, false, 0f, 8f), "no actual ranged fire must not wake sleepers");
        check(!CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                false, true, 8f, 2f), "winning the ranged exchange protects sleepers");
        check(CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                false, true, 0f, 8f), "no ranged answer under fire permits necessary close");
        check(CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                false, true, 4f, 8f), "losing ranged exchange permits necessary close");
        check(!CoHeroCombatRisk.riskWakingEnemyForRangedApproach(
                false, true, 0f, 0f), "no actual incoming damage protects sleepers");

        boolean rejectedNegativeRangedRisk = false;
        try {
            CoHeroCombatRisk.riskWakingEnemyForRangedApproach(false, true, -1f, 5f);
        } catch (IllegalArgumentException expected) {
            rejectedNegativeRangedRisk = true;
        }
        check(rejectedNegativeRangedRisk, "negative combat damage estimate must fail fast");

        // No escape tile by itself must never spend healing/shielding.
        check(!CoHeroCombatRisk.emergencyConsumableRequired(99f, 1f, 1f),
                "minor scratch is not an emergency");
        check(!CoHeroCombatRisk.emergencyConsumableRequired(100f, 0f, 0f),
                "no incoming damage");
        check(CoHeroCombatRisk.emergencyConsumableRequired(100f, 2f, 100f),
                "full HP but lethal next attack needs shielding");
        check(CoHeroCombatRisk.emergencyConsumableRequired(30f, 12f, 5f),
                "low time to death");
        check(!CoHeroCombatRisk.emergencyHealingWorth(99, 100),
                "emergency healing must not waste potion on small wounds");
        check(!CoHeroCombatRisk.emergencyHealingWorth(61, 100),
                "emergency healing max HP threshold");
        check(CoHeroCombatRisk.emergencyHealingWorth(60, 100),
                "emergency healing allowed at 60 percent");
        check(!CoHeroCombatRisk.emergencyHealingWorth(0, 0),
                "invalid max HP cannot justify healing");

        boolean rejectedNegative = false;
        try {
            CoHeroCombatRisk.emergencyConsumableRequired(100f, -1f, 0f);
        } catch (IllegalArgumentException expected) {
            rejectedNegative = true;
        }
        check(rejectedNegative, "negative damage must fail fast");
        System.out.println("CoHero consumable policy checks passed: " + checks);
    }
}
