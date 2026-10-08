package com.thuvstu.hayatemod.core.life;

import java.util.List;

/** Fishing skill tables (second life skill, §5.14). Arrival unlocks. */
public final class FishingLevels {
    private FishingLevels() {
    }

    public record FishingBook(int catchXp, List<MiningLevels.LevelRow> levels) {
    }
}
