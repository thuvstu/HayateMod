package com.thuvstu.hayatemod.dungeon;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Small entity-lookup helpers shared by the dungeon package. */
final class EntityTypeTestHelper {
    private EntityTypeTestHelper() {
    }

    static LivingEntity living(ServerLevel level, UUID id) {
        if (id == null) {
            return null;
        }
        Entity e = level.getEntity(id);
        return e instanceof LivingEntity living ? living : null;
    }

    static void discard(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        if (e != null) {
            e.discard();
        }
    }

    static ServerPlayer serverPlayer(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        return e instanceof ServerPlayer p ? p : null;
    }
}
