package com.thuvstu.hayatemod.core.content;

/** A single content-loading problem. Loader collects these instead of failing fast. */
public record ContentError(String file, String path, String message) {
    @Override
    public String toString() {
        return file + ":" + path + ": " + message;
    }
}
