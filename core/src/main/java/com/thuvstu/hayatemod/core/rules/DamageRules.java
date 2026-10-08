package com.thuvstu.hayatemod.core.rules;

/**
 * Pure damage functions from {@code docs/rules-minimal.md}.
 *
 * <p>Used by both the game and the simulator/CLI. No Minecraft classes.
 * All computation in double; rounding happens only at display time.
 */
public final class DamageRules {
    private DamageRules() {
    }

    public static double corePower(double skillCoreBase, double playerLevelCurve,
            double itemLevelCurve, double skillRankCurve) {
        return skillCoreBase * playerLevelCurve * itemLevelCurve * skillRankCurve;
    }

    public static double rawDamage(double corePower, double damageModifiers, double criticalMultiplier) {
        return corePower * damageModifiers * criticalMultiplier;
    }

    public static double applyResistance(double rawDamage, double targetResistance) {
        return rawDamage * (1.0 - targetResistance);
    }

    public static double applyMitigation(double afterResistance, double armorMitigation, double guardReduction) {
        return afterResistance * (1.0 - armorMitigation) * (1.0 - guardReduction);
    }

    public static double finalDamage(double skillCoreBase, double playerLevelCurve, double itemLevelCurve,
            double skillRankCurve, double damageModifiers, double criticalMultiplier,
            double targetResistance, double armorMitigation, double guardReduction) {
        double power = corePower(skillCoreBase, playerLevelCurve, itemLevelCurve, skillRankCurve);
        double raw = rawDamage(power, damageModifiers, criticalMultiplier);
        return applyMitigation(applyResistance(raw, targetResistance), armorMitigation, guardReduction);
    }

    /**
     * Lethal-gimmick damage. {@code lethalRatio 1.0} equals one reference-HP bar.
     * Never expressed through {@code damage_mult}.
     */
    public static double lethalDamage(double referenceHp, double lethalRatio) {
        return referenceHp * lethalRatio;
    }
}
