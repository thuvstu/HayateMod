package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.content.model.Models.*;

class LootRollerTest {
    private final List<DirectDrop> drops = List.of(new DirectDrop("a", .25), new DirectDrop("b", .5));
    private LootTable table() {
        return new LootTable("test:loot", drops, "pity_shard", 2, 10, "a",
                List.of(new MaterialDrop("craft_material", 2, 4)), List.of(new RuneDrop("rune", .5)));
    }
    @Test void exclusiveIntervalsHaveNoOverlapAndAllowNoDrop() {
        assertEquals("a", LootRoller.equipment(drops, 0));
        assertEquals("b", LootRoller.equipment(drops, .25));
        assertEquals("", LootRoller.equipment(drops, .75));
        assertEquals("", LootRoller.equipment(List.of(), .1));
    }
    @Test void zeroProbabilityNeverDrops() {
        assertEquals("b", LootRoller.equipment(List.of(new DirectDrop("a", 0), new DirectDrop("b", 1)), 0));
    }
    @Test void directDropStillAwardsFullPity() {
        var roll = LootRoller.roll(table(), RuleResolver.Multipliers.identity(), () -> 0, bound -> 0);
        assertEquals("a", roll.equipment());
        assertEquals(2, roll.pity());
        assertEquals(List.of("rune"), roll.runes());
    }
    @Test void noDirectDropStillAwardsFullPity() {
        var roll = LootRoller.roll(table(), RuleResolver.Multipliers.identity(), () -> .9, bound -> 0);
        assertEquals("", roll.equipment());
        assertEquals(2, roll.pity());
    }
    @Test void difficultyScalesCurrencyAndMaterialsNotDirectProbability() {
        var roll = LootRoller.roll(table(), new RuleResolver.Multipliers(1.5, 1.2, 2, 1.5), () -> .3, bound -> bound - 1);
        assertEquals("b", roll.equipment());
        assertEquals(4, roll.pity());
        assertEquals(6, roll.materials().getFirst().count());
    }
    @Test void sameSeedProducesSameLootSequence() {
        Random a = new Random(42), b = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(LootRoller.roll(table(), RuleResolver.Multipliers.identity(), a::nextDouble, a::nextInt),
                    LootRoller.roll(table(), RuleResolver.Multipliers.identity(), b::nextDouble, b::nextInt));
        }
    }
    @Test void exclusiveDrawMatchesConfiguredFrequencies() {
        Random random = new Random(20261009);
        int a = 0, b = 0, none = 0;
        for (int i = 0; i < 100000; i++) {
            switch (LootRoller.equipment(drops, random.nextDouble())) {
                case "a" -> a++;
                case "b" -> b++;
                default -> none++;
            }
        }
        assertEquals(.25, a / 100000., .005);
        assertEquals(.5, b / 100000., .005);
        assertEquals(.25, none / 100000., .005);
    }
    @Test void malformedDrawOrTotalFails() {
        assertThrows(IllegalArgumentException.class, () -> LootRoller.equipment(drops, 1));
        assertThrows(IllegalArgumentException.class, () -> LootRoller.equipment(drops, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> LootRoller.equipment(List.of(new DirectDrop("a", .6), new DirectDrop("b", .6)), 0));
    }
}
