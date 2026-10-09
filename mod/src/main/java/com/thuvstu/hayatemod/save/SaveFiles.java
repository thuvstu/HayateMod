package com.thuvstu.hayatemod.save;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.thuvstu.hayatemod.core.save.AtomicSaveFile;
import com.thuvstu.hayatemod.core.save.SaveVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Versioned JSON adapter. Failed loads are read-only for this process so empty fallback state cannot overwrite them. */
public final class SaveFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/save");
    private static final Gson GSON = new Gson();
    private static final Set<Path> READ_ONLY = ConcurrentHashMap.newKeySet();
    public static final int VERSION = SaveVersions.SAVE_VERSION;

    private SaveFiles() {
    }

    /** Returns null for missing/unreadable data; unreadable files are protected from subsequent saves. */
    public static synchronized <T> T load(Path file, Type dataType, String tag) {
        Path target = file.toAbsolutePath().normalize();
        if (Files.notExists(target)) {
            return null;
        }
        try {
            JsonObject root = readRoot(target);
            JsonElement data = payload(root);
            T value = GSON.fromJson(data, dataType);
            if (value == null) {
                throw new IllegalArgumentException("save payload must not be null");
            }
            if (!root.has("save_version") || version(root, "save_version") < VERSION) {
                LOGGER.info("[save] {} migrating envelope to v{} on next successful save", tag, VERSION);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException e) {
            READ_ONLY.add(target);
            LOGGER.error("[save] {} cannot read {}; writes blocked until restart: {}", tag, target, e.getMessage());
            return null;
        }
    }

    public static synchronized void save(Path file, Object data, String tag) {
        Path target = file.toAbsolutePath().normalize();
        if (READ_ONLY.contains(target)) {
            LOGGER.error("[save] {} refusing to overwrite unreadable/unsupported save {}", tag, target);
            return;
        }
        // Also protect a future/corrupt save when callers did not load it first.
        if (!Files.notExists(target)) {
            try {
                payload(readRoot(target));
            } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException e) {
                READ_ONLY.add(target);
                LOGGER.error("[save] {} refusing to overwrite {}: {}", tag, target, e.getMessage());
                return;
            }
        }
        try {
            if (data == null) {
                throw new IllegalArgumentException("save payload must not be null");
            }
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("save_version", VERSION);
            wrapped.addProperty("content_version", SaveVersions.CONTENT_VERSION);
            wrapped.add("data", GSON.toJsonTree(data));
            // Serialization finishes before touching even a temporary file.
            AtomicSaveFile.write(target, GSON.toJson(wrapped));
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOGGER.error("[save] {} cannot write {}; previous save retained: {}", tag, target, e.getMessage());
        }
    }

    private static JsonObject readRoot(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("save root must be an object");
        }
        return root.getAsJsonObject();
    }

    private static JsonElement payload(JsonObject root) {
        if (!root.has("save_version")) {
            // Do not treat an incomplete envelope as a legacy payload.
            if (root.has("content_version") || root.has("data")) {
                throw new IllegalArgumentException("incomplete save envelope");
            }
            SaveVersions.migratedContentVersion(0, null);
            return root;
        }
        int stored = version(root, "save_version");
        if (stored == 0) {
            throw new IllegalArgumentException("v0 saves must be bare payloads");
        }
        Integer contentVersion = root.has("content_version") ? version(root, "content_version") : null;
        SaveVersions.migratedContentVersion(stored, contentVersion);
        if (!root.has("data") || root.get("data").isJsonNull()) {
            throw new IllegalArgumentException("save envelope requires non-null data");
        }
        return root.get("data");
    }

    private static int version(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer", e);
        }
    }
}
