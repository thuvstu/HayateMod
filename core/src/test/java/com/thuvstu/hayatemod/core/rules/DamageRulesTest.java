package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageRulesTest {
    @Test
    void corePowerMultipliesAllCurves() {
        assertEquals(198.0, DamageRules.corePower(100.0, 1.2, 1.5, 1.1), 1e-9);
    }

    @Test
    void finalDamageFollowsSpecFormula() {
        // power=100, mods=1.0, crit=1.5 -> raw=150
        // resistance 0.2 -> 120; armor 0.25, guard 0.0 -> 90
        assertEquals(90.0, DamageRules.finalDamage(
                100.0, 1.0, 1.0, 1.0, 1.0, 1.5, 0.2, 0.25, 0.0), 1e-9);
    }

    @Test
    void lethalRatioScalesReferenceHp() {
        assertEquals(1680.0, DamageRules.lethalDamage(1200.0, 1.4), 1e-9);
    }
}
