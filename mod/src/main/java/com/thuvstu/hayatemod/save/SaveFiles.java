package com.thuvstu.hayatemod.save;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versioned world JSON (§7.1): files look like
 * {@code {"save_version": 1, "data": ...}}. Legacy bare payloads still
 * load (treated as version 0) and are rewritten versioned on next save.
 */
public final class SaveFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/save");
    private static final Gson GSON = new Gson();
    public static final int VERSION = 1;

    private SaveFiles() {
    }

    /** Reads a versioned file. Returns null when missing or unreadable. */
    public static <T> T load(Path file, Type dataType, String tag) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("save_version") && root.has("data")) {
                return GSON.fromJson(root.get("data"), dataType);
            }
            LOGGER.info("[save] {} has no version, migrating as v0", tag);
            return GSON.fromJson(json, dataType);
        } catch (IOException | com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LOGGER.error("[save] {} cannot read {}: {}", tag, file, e.getMessage());
            return null;
        }
    }

    public static void save(Path file, Object data, String tag) {
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("save_version", VERSION);
            wrapped.put("data", data);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(wrapped, w);
            }
        } catch (IOException e) {
            LOGGER.error("[save] {} cannot write {}: {}", tag, file, e.getMessage());
        }
    }
}
