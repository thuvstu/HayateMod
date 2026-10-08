package com.thuvstu.hayatemod.core.describe;

/** Thrown when no Japanese template exists for a vocabulary word (§9 V10). */
public class MissingTemplateException extends RuntimeException {
    public MissingTemplateException(String kind, String word) {
        super("no description template for " + kind + " '" + word + "'");
    }
}
