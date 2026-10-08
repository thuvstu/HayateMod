package com.thuvstu.hayatemod.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the repository {@code content/} directory from test working dirs. */
public final class TestContent {
    private TestContent() {
    }

    public static Path dir() {
        for (String candidate : new String[] {"content", "../content", "../../content"}) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("vocabulary/core.yaml"))) {
                return p;
            }
        }
        throw new IllegalStateException("content directory not found from " + Path.of("").toAbsolutePath());
    }
}
