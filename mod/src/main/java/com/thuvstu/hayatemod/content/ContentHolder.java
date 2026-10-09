package com.thuvstu.hayatemod.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.thuvstu.hayatemod.core.content.ContentRepository;
import com.thuvstu.hayatemod.core.content.ContentSet;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Game-side source selection and logging; validation/publication live in pure Java core. */
public final class ContentHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/content");
    private static final ContentRepository REPOSITORY = new ContentRepository();

    private ContentHolder() {
    }

    /** Returns true only when a new candidate passed every gate and was published. */
    public static boolean load() {
        Path dir = findContentDir();
        if (dir == null) {
            LOGGER.error("[content] no content source found; keeping previous dataset");
            return false;
        }
        var result = REPOSITORY.reload(dir);
        for (var error : result.loaderErrors()) {
            LOGGER.error("[content] {}", error);
        }
        for (var issue : result.issues()) {
            switch (issue.severity()) {
                case ERROR -> LOGGER.error("[content] {}", issue);
                case WARNING -> LOGGER.warn("[content] {}", issue);
                case INFO -> LOGGER.info("[content] {}", issue);
            }
        }
        if (!result.ok()) {
            LOGGER.error("[content] rejected {}; keeping previous dataset", dir);
            return false;
        }
        ContentSet set = result.set();
        LOGGER.info("[content] loaded from {}: weapons={} skills={} enemies={} encounters={} npcs={}",
                dir, set.weapons().size(), set.skills().size(), set.enemies().size(),
                set.encounters().size(), set.npcs().size());
        return true;
    }

    public static ContentSet get() {
        var snapshot = REPOSITORY.current();
        return snapshot == null ? null : snapshot.set();
    }

    public static boolean ready() {
        return REPOSITORY.current() != null;
    }

    /** Source of the active dataset, not the most recently attempted candidate. */
    public static Path source() {
        var snapshot = REPOSITORY.current();
        return snapshot == null ? null : snapshot.source();
    }

    private static Path findContentDir() {
        var loader = FabricLoader.getInstance();
        Path override = loader.getConfigDir().resolve("hayatemod/content");
        // An explicit override is a complete pack, never a partial merge. If broken,
        // reject it rather than silently falling back to a different dataset.
        if (Files.exists(override)) {
            return override;
        }
        if (loader.isDevelopmentEnvironment()) {
            for (String candidate : List.of("content", "../content")) {
                Path p = Paths.get(candidate);
                if (Files.isDirectory(p)) {
                    return p;
                }
            }
        }
        // Fabric owns the JAR filesystem; do not close it after reading.
        return loader.getModContainer("hayatemod")
                .flatMap(container -> container.findPath("content")).orElse(null);
    }
}
