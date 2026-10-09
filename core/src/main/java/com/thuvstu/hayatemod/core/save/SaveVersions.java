package com.thuvstu.hayatemod.core.save;

/** Explicit envelope migrations. Content version describes the supported content schema, not a mod release. */
public final class SaveVersions {
    public static final int SAVE_VERSION = 2;
    public static final int CONTENT_VERSION = 1;

    private SaveVersions() {
    }

    /** v0 = bare payload, v1 = save_version/data, v2 adds mandatory content_version. */
    public static int migratedContentVersion(int saveVersion, Integer contentVersion) {
        if (saveVersion < 0 || saveVersion > SAVE_VERSION) {
            throw new IllegalArgumentException("unsupported save_version: " + saveVersion);
        }
        if (contentVersion == null) {
            if (saveVersion >= 2) {
                throw new IllegalArgumentException("content_version is required for v2 saves");
            }
            // Explicit v0/v1 -> v2 migration: their payload schema was content v1.
            return CONTENT_VERSION;
        }
        if (contentVersion != CONTENT_VERSION) {
            throw new IllegalArgumentException("unsupported content_version: " + contentVersion);
        }
        return contentVersion;
    }
}
