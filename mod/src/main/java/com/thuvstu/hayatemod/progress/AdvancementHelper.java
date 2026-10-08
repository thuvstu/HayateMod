package com.thuvstu.hayatemod.progress;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Code-driven advancement grants (criteria use the impossible trigger). */
public final class AdvancementHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/progress");

    private AdvancementHelper() {
    }

    public static boolean grant(ServerPlayer player, String path) {
        try {
            var server = player.level() instanceof net.minecraft.server.level.ServerLevel sl
                    ? sl.getServer()
                    : null;
            if (server == null) {
                return false;
            }
            var holder = server.getAdvancements()
                    .get(Identifier.fromNamespaceAndPath("hayatemod", path));
            if (holder == null) {
                return false;
            }
            return player.getAdvancements().award(holder, "code");
        } catch (Exception e) {
            LOGGER.warn("[progress] grant {} failed: {}", path, e.getMessage());
            return false;
        }
    }
}
