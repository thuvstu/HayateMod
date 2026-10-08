package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DropSolverTest {
    @Test
    void dungeonBossExampleFromDesign() {
        // §5.10: 15min/run, pity 12 runs, target expectation 8 runs -> p ~= 7.7%
        assertEquals(0.077, DropSolver.solveRate(8.0, 12), 0.005);
        assertEquals(8.0, DropSolver.expectedRuns(0.077, 12), 0.5);
    }

    @Test
    void pityOnlyEdgeCase() {
        assertEquals(12.0, DropSolver.expectedRuns(0.0, 12), 1e-9);
        assertEquals(0.0, DropSolver.solveRate(12.0, 12), 1e-9);
    }
}
