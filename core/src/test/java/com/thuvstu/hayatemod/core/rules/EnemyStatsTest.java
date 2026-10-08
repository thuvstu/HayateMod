package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;

class EnemyStatsTest {
    @Test
    void derivesFromReferenceTable() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        var set = pack.set();
        // Level 10 reference: HP 450 / DPS 38. Trash: 14s, 0.15.
        var trash = EnemyStats.derive(10, "trash", set.reference(),
                set.tuning().rankTargets(), 1.0, 1.0);
        assertEquals(38.0 * 14, trash.maxHp(), 1e-9);
        assertEquals(450.0 * 0.15 / 14, trash.dps(), 1e-9);
        // Level 20 boss: HP 1200 / DPS 90. Boss: 720s, 0.90.
        var boss = EnemyStats.derive(20, "boss", set.reference(),
                set.tuning().rankTargets(), 1.0, 1.0);
        assertEquals(90.0 * 720, boss.maxHp(), 1e-9);
        assertEquals(1200.0 * 0.90 / 720, boss.dps(), 1e-9);
    }

    @Test
    void interpolatesBetweenRows() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        var set = pack.set();
        // Level 12 sits between rows 10 (450/38) and 15 (800/60).
        double[] ref = EnemyStats.interpolate(set.reference(), 12);
        assertEquals(450.0 + (800.0 - 450.0) * 0.4, ref[0], 1e-9);
        assertEquals(38.0 + (60.0 - 38.0) * 0.4, ref[1], 1e-9);
    }

    @Test
    void tuningHasRankTargets() {
        // Unknown rank must fail loudly, not silently default.
        var set = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir()).set();
        Map<String, com.thuvstu.hayatemod.core.content.model.Models.RankTarget> targets =
                set.tuning().rankTargets();
        org.junit.jupiter.api.Assertions.assertTrue(targets.containsKey("trash"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> EnemyStats.derive(10, "nope", set.reference(), targets, 1.0, 1.0));
    }
}
