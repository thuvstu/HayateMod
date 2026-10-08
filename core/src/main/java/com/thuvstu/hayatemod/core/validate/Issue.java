package com.thuvstu.hayatemod.core.validate;

/** A validation finding. Codes map to IMPLEMENTATION.md §9 rule numbers. */
public record Issue(Severity severity, String code, String location, String message) {
    public enum Severity {
        ERROR, WARNING, INFO
    }

    @Override
    public String toString() {
        return "[" + severity + " " + code + "] " + location + ": " + message;
    }
}
