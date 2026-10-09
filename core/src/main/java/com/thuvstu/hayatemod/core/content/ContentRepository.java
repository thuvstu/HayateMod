package com.thuvstu.hayatemod.core.content;

import java.nio.file.Path;

/** Owns the last valid dataset and its source. A rejected candidate changes neither. */
public final class ContentRepository {
    public record Snapshot(Path source, ContentSet set) {
    }

    private volatile Snapshot current;

    public synchronized ContentPipeline.Result reload(Path source) {
        Path normalized = source.toAbsolutePath().normalize();
        ContentPipeline.Result result = ContentPipeline.load(normalized);
        if (result.ok()) {
            current = new Snapshot(normalized, result.set());
        }
        return result;
    }

    public Snapshot current() {
        return current;
    }
}
