package com.thuvstu.hayatemod.codex;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal codex: obtained weapon ids per player, persisted as JSON in the
 * world folder (no player-save mixin needed in Phase 3).
 */
public final class CodexStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/codex");
    private static final Gson GSON = new Gson();

    private static Path file;
    private static final Map<String, Set<String>> DATA = new HashMap<>();

    private CodexStore() {
    }

    public static void load(Path worldRoot) {
        file = worldRoot.resolve("hayatemod/codex.json");
        DATA.clear();
        Map<String, Set<String>> loaded = com.thuvstu.hayatemod.save.SaveFiles.load(file,
                new TypeToken<Map<String, Set<String>>>() {
                }.getType(),
                "codex");
        if (loaded != null) {
            for (var e : loaded.entrySet()) {
                DATA.put(e.getKey(), new HashSet<>(e.getValue()));
            }
        }
        LOGGER.info("[codex] loaded {} players from {}", DATA.size(), file);
    }

    public static void record(UUID player, String weaponId) {
        DATA.computeIfAbsent(player.toString(), k -> new HashSet<>()).add(weaponId);
        save();
    }

    public static Set<String> list(UUID player) {
        return Set.copyOf(DATA.getOrDefault(player.toString(), Set.of()));
    }

    private static void save() {
        if (file == null) {
            return;
        }
        com.thuvstu.hayatemod.save.SaveFiles.save(file, DATA, "codex");
    }
}
