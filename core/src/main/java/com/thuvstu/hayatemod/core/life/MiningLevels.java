package com.thuvstu.hayatemod.core.life;

import java.util.List;
import java.util.Map;

/** Mining skill tables (IMPLEMENTATION.md §5.14). Arrival unlocks, not procs. */
public final class MiningLevels {
    private MiningLevels() {
    }

    public record LevelRow(int level, int xpRequired, List<String> unlocks) {
    }

    public record MiningBook(Map<String, Integer> oreXp, List<LevelRow> levels, int gemMinLevel,
            List<String> gemOres, int gemBonusMaterials) {
    }

    public static int xpFor(String oreId, MiningBook book) {
        return book.oreXp().getOrDefault(oreId, 0);
    }

    public static int levelForXp(int xp, MiningBook book) {
        return levelForRows(xp, book.levels());
    }

    public static int levelForRows(int xp, List<LevelRow> rows) {
        int level = 1;
        for (LevelRow row : rows) {
            if (xp >= row.xpRequired()) {
                level = row.level();
            }
        }
        return level;
    }

    public static boolean unlocksContain(MiningBook book, int level, String unlock) {
        return unlocksContain(book.levels(), level, unlock);
    }

    public static boolean unlocksContain(List<LevelRow> rows, int level, String unlock) {
        for (LevelRow row : rows) {
            if (row.level() <= level && row.unlocks().contains(unlock)) {
                return true;
            }
        }
        return false;
    }
}
