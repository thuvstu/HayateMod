package com.thuvstu.hayatemod.forge;

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
 * Player-forged one-off weapons (in-game forge). Each forged weapon keeps a
 * spec: vessel style, donor weapons per slot, custom name, material tier.
 * Runtime cards are assembled from donor skills on resolve; specs persist
 * as JSON in the world folder next to builds.
 */
public final class ForgedStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/forge");
    private static final Gson GSON = new Gson();

    private static Path file;
    private static final Map<String, Spec> DATA = new HashMap<>();

    private ForgedStore() {
    }

    public static final class Spec {
        public String style = "";
        public String base = "";
        public String special = "";
        public String heavy = "";
        public String name = "";
        public String material = "";
        public int itemLevel;
    }

    public static void load(Path worldRoot) {
        file = worldRoot.resolve("hayatemod/forged.json");
        DATA.clear();
        Map<String, Spec> loaded = com.thuvstu.hayatemod.save.SaveFiles.load(file,
                new TypeToken<Map<String, Spec>>() {
                }.getType(),
                "forge");
        if (loaded != null) {
            DATA.putAll(loaded);
        }
        LOGGER.info("[forge] loaded {} forged weapons from {}", DATA.size(), file);
    }

    private static void save() {
        if (file == null) {
            return;
        }
        com.thuvstu.hayatemod.save.SaveFiles.save(file, DATA, "forge");
    }

    public static Spec get(String id) {
        return DATA.get(id);
    }

    /** Stores a spec under a fresh id. Returns the id. */
    public static String put(Spec spec) {
        String id;
        do {
            id = "solommo:forged:"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (DATA.containsKey(id));
        DATA.put(id, spec);
        save();
        return id;
    }

    public static int count() {
        return DATA.size();
    }
}
