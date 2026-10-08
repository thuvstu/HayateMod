package com.thuvstu.hayatemod.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.ContentError;
import com.thuvstu.hayatemod.core.content.ContentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the Content Pack for the game. Dev runs read {@code ./content} (or
 * {@code ../content} when the working directory is {@code run/}).
 * Packaged-jar loading from classpath resources is a later task (see ADR-08).
 */
public final class ContentHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/content");

    private static ContentSet set;
    private static List<ContentError> errors = List.of();
    private static Path source;

    private ContentHolder() {
    }

    public static void load() {
        Path dir = findContentDir();
        source = dir;
        if (dir == null) {
            LOGGER.error("[content] no content directory found (tried ./content, ../content)");
            return;
        }
        ContentPack.LoadedPack pack = ContentPack.load(dir);
        errors = pack.errors();
        if (!pack.ok()) {
            for (var e : errors) {
                LOGGER.error("[content] {}", e);
            }
            LOGGER.error("[content] keeping previous dataset ({} errors)", errors.size());
            return;
        }
        set = pack.set();
        LOGGER.info("[content] loaded from {}: weapons={} skills={} enemies={} encounters={} npcs={}",
                dir, set.weapons().size(), set.skills().size(), set.enemies().size(),
                set.encounters().size(), set.npcs().size());
    }

    public static ContentSet get() {
        return set;
    }

    public static boolean ready() {
        return set != null;
    }

    public static Path source() {
        return source;
    }

    private static Path findContentDir() {
        for (String candidate : List.of("content", "../content")) {
            Path p = Paths.get(candidate);
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("vocabulary/core.yaml"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        return null;
    }
}
