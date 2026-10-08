package com.thuvstu.hayatemod.life;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Life-skill persistence (Phase 6). Mining XP/level per player, JSON in the
 * world folder (same pattern as codex/builds; unify under PlayerProfileStore
 * when it lands per §7.1).
 */
public final class LifeSkills {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/life");
    private static final Gson GSON = new Gson();

    private static Path file;
    private static final Map<String, Integer> MINING_XP = new HashMap<>();
    private static final Map<String, Integer> FISHING_XP = new HashMap<>();

    private LifeSkills() {
    }

    public static void load(Path worldRoot) {
        file = worldRoot.resolve("hayatemod/lifeskills.json");
        MINING_XP.clear();
        FISHING_XP.clear();
        // Versioned {"mining": {...}, "fishing": {...}} with fallback to the
        // legacy flat map (treated as mining).
        Map<String, Map<String, Integer>> loaded =
                com.thuvstu.hayatemod.save.SaveFiles.load(file,
                        new TypeToken<Map<String, Map<String, Integer>>>() {
                        }.getType(),
                        "life");
        if (loaded != null && (loaded.containsKey("mining") || loaded.containsKey("fishing"))) {
            if (loaded.get("mining") != null) {
                MINING_XP.putAll(loaded.get("mining"));
            }
            if (loaded.get("fishing") != null) {
                FISHING_XP.putAll(loaded.get("fishing"));
            }
            LOGGER.info("[life] loaded skills for {} players", MINING_XP.size());
            return;
        }
        if (loaded != null) {
            LOGGER.info("[life] no skill keys, trying legacy flat map");
        } else if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader r2 = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Integer> legacy = GSON.fromJson(r2, new TypeToken<Map<String, Integer>>() {
            }.getType());
            if (legacy != null) {
                MINING_XP.putAll(legacy);
                LOGGER.info("[life] migrated legacy flat map ({} players)", legacy.size());
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.error("[life] cannot read {}: {}", file, e.getMessage());
        }
    }

    public static int miningXp(UUID player) {
        return MINING_XP.getOrDefault(player.toString(), 0);
    }

    public static void addMiningXp(UUID player, int amount) {
        MINING_XP.put(player.toString(), miningXp(player) + amount);
        save();
    }

    public static int fishingXp(UUID player) {
        return FISHING_XP.getOrDefault(player.toString(), 0);
    }

    public static void addFishingXp(UUID player, int amount) {
        FISHING_XP.put(player.toString(), fishingXp(player) + amount);
        save();
    }

    private static void save() {
        if (file == null) {
            return;
        }
        Map<String, Map<String, Integer>> data = new HashMap<>();
        data.put("mining", new HashMap<>(MINING_XP));
        data.put("fishing", new HashMap<>(FISHING_XP));
        com.thuvstu.hayatemod.save.SaveFiles.save(file, data, "life");
    }
}
