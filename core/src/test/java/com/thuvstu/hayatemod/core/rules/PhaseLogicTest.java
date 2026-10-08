package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.model.Models.PhaseDef;

class PhaseLogicTest {
    private static List<PhaseDef> golem() {
        return List.of(
                new PhaseDef("p1", 100, 60, 50, List.of(), List.of()),
                new PhaseDef("p2", 60, 0, 55, List.of(), List.of()));
    }

    @Test
    void resolvesByHpFraction() {
        assertEquals("p1", PhaseLogic.phaseFor(golem(), 1.0).id());
        assertEquals("p1", PhaseLogic.phaseFor(golem(), 0.61).id());
        assertEquals("p2", PhaseLogic.phaseFor(golem(), 0.60).id());
        assertEquals("p2", PhaseLogic.phaseFor(golem(), 0.01).id());
    }
}
