package com.thuvstu.hayatemod.debug;

/**
 * Runtime debug switches for development builds. Defaults are off so normal
 * play does not spam the log; {@code /s1 log on} enables damage-path logging.
 * These are developer aids, not game rules.
 */
public final class DebugFlags {
    /** Logs every damage/heal event on the server thread (very verbose). */
    public static volatile boolean damageLog = false;

    private DebugFlags() {
    }
}
