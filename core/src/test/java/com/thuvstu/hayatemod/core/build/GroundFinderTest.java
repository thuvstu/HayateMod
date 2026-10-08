package com.thuvstu.hayatemod.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class GroundFinderTest {
    /** Sea at x >= 0 (surface 62, not land), land plateau at x < 0 (surface 70). */
    private static GroundFinder.Column shore(int x, int z) {
        if (x >= 0) {
            return new GroundFinder.Column(62, false);
        }
        return new GroundFinder.Column(70, true);
    }

    @Test
    void skipsSeaAndFindsLand() {
        Optional<GroundFinder.Found> found = GroundFinder.find(GroundFinderTest::shore,
                100, 100, 62, 12, 3, 192, 16);
        assertTrue(found.isPresent());
        assertTrue(found.get().x() < -12, "footprint must clear the shoreline: " + found.get());
        assertEquals(70, found.get().y());
    }

    @Test
    void rejectsSteepSlopes() {
        GroundFinder.Sampler slope = (x, z) -> new GroundFinder.Column(70 + x / 2, true);
        Optional<GroundFinder.Found> found =
                GroundFinder.find(slope, 0, 0, 62, 12, 3, 64, 16);
        assertTrue(found.isEmpty(), "24-wide slope of 6 exceeds tolerance: " + found);
    }

    @Test
    void emptyWhenAllSea() {
        GroundFinder.Sampler sea = (x, z) -> new GroundFinder.Column(60, false);
        assertTrue(GroundFinder.find(sea, 0, 0, 62, 12, 3, 64, 16).isEmpty());
    }
}
