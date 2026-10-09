package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.sim.CollectionSimulation;

class CollectionSimulationTest {
    private WeaponCard card(String id) { return new WeaponCard(id, id, "sword", "unique", 1, List.of(), List.of(), Map.of(), ""); }
    private LootTable table(List<DirectDrop> drops, int tokens, List<MaterialDrop> materials) {
        return new LootTable("loot", drops, "pity_shard", tokens, 5, "a", materials, List.of());
    }
    @Test void zeroDropStillReachesExactExchangeGuarantee() {
        var table = table(List.of(new DirectDrop("a", 0)), 1, List.of());
        var r = CollectionSimulation.run(table, Map.of("a", card("a")), List.of("a"), RuleResolver.Multipliers.identity(), 42, 100, 10);
        assertEquals(5, r.meanCompletedRuns());
        assertEquals(5, r.guaranteedRuns());
        assertEquals(0, r.censored());
    }
    @Test void fullCollectionSharesAndSpendsTokensRatherThanResettingOnDrops() {
        var table = table(List.of(new DirectDrop("a", 1)), 2, List.of(new MaterialDrop("craft_material", 1, 1)));
        var cards = Map.of("a", card("a"), "b", card("b"));
        var r = CollectionSimulation.run(table, cards, List.of("a", "b"), RuleResolver.Multipliers.identity(), 42, 100, 10);
        assertEquals(3, r.meanCompletedRuns()); // 6 kill tokens + 4 duplicate salvage tokens.
        assertEquals(0, r.censored());
        assertTrue(r.guaranteedRuns() >= r.maximumCompletedRuns());
    }
    @Test void impossibleCollectionIsCensoredNotCountedAsSuccess() {
        var table = table(List.of(), 0, List.of());
        var r = CollectionSimulation.run(table, Map.of("b", card("b")), List.of("b"), RuleResolver.Multipliers.identity(), 42, 10, 20);
        assertEquals(10, r.censored());
        assertEquals(0, r.completed());
        assertTrue(Double.isNaN(r.meanCompletedRuns()));
        assertEquals(-1, r.guaranteedRuns());
    }
    @Test void simulationIsReproducible() {
        var table = table(List.of(new DirectDrop("a", .1)), 1, List.of());
        var a = CollectionSimulation.run(table, Map.of("a", card("a")), List.of("a"), RuleResolver.Multipliers.identity(), 42, 100, 10);
        var b = CollectionSimulation.run(table, Map.of("a", card("a")), List.of("a"), RuleResolver.Multipliers.identity(), 42, 100, 10);
        assertEquals(a, b);
    }
    @Test void heroicRewardShortensGuaranteedRuns() {
        var table = table(List.of(), 1, List.of());
        assertEquals(3, CollectionSimulation.guarantee(table, Map.of("a", card("a")), List.of("a"), new RuleResolver.Multipliers(1, 1, 2, 1)));
    }
    @Test void workBudgetAndUnknownTargetsFailFast() {
        var table = table(List.of(), 1, List.of());
        assertThrows(IllegalArgumentException.class, () -> CollectionSimulation.run(table, Map.of(), List.of("bad"), RuleResolver.Multipliers.identity(), 42, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CollectionSimulation.run(table, Map.of("a", card("a")), List.of("a"), RuleResolver.Multipliers.identity(), 42, 100000, 100000));
    }
}
