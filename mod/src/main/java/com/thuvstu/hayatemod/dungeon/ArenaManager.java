package com.thuvstu.hayatemod.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated arena in the overworld (ADR-11/S2 decision: fixed arena instead
 * of a separate dimension for Phase 4). Bedrock box far from spawn; the
 * structure persists, session entities are tagged and swept on exit.
 */
public final class ArenaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/dungeon");

    public static final BlockPos ORIGIN = new BlockPos(10000, 100, 10000);
    private static final int HALF = 12;

    /** Data-driven origin: ground site once built, else the content hint. */
    public static BlockPos origin() {
        var peek = com.thuvstu.hayatemod.build.GroundSites.peekArena();
        if (peek.isPresent()) {
            return peek.get();
        }
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        if (set != null && set.world() != null) {
            var a = set.world().arena();
            return new BlockPos(a.x(), a.y(), a.z());
        }
        return ORIGIN;
    }

    private static int half() {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        if (set != null && set.world() != null) {
            return set.world().arena().halfSize();
        }
        return HALF;
    }

    /** Walkable floor Y (top of the bedrock floor). */
    public static double floorY() {
        return origin().getY();
    }

    private ArenaManager() {
    }

    public static Vec3 entryPos() {
        return new Vec3(origin().getX(), origin().getY(), origin().getZ() + 8);
    }

    /** Local encounter markers ([x, 0, z]) become world positions on the floor. */
    public static Vec3 markerWorld(List<Number> local) {
        double x = origin().getX() + local.get(0).doubleValue();
        double z = origin().getZ() + local.get(2).doubleValue();
        return new Vec3(x, origin().getY(), z);
    }

    public static void ensureArena(ServerLevel level) {
        ensureArena(level, "solommo:flame_arena");
    }

    /**
     * Builds the linked structure template once, then refreshes torches.
     * Returns false when the template is missing (caller must abort entry).
     */
    public static boolean ensureArena(ServerLevel level, String structureId) {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        int hintX = ORIGIN.getX();
        int hintZ = ORIGIN.getZ();
        if (set != null && set.world() != null) {
            hintX = set.world().arena().x();
            hintZ = set.world().arena().z();
        }
        BlockPos site = com.thuvstu.hayatemod.build.GroundSites.arenaSite(level, hintX, hintZ,
                half(), ORIGIN);
        if (!(level.getBlockState(site.below()).is(net.minecraft.world.level.block.Blocks.BEDROCK)
                && level.getBlockState(site.atY(site.getY())).isAir())) {
            var template = set != null && set.structures() != null
                    ? set.structures().get(structureId)
                    : null;
            if (template == null) {
                LOGGER.error("[DUNGEON] missing structure '{}'; arena build aborted", structureId);
                return false;
            }
            LOGGER.info("[DUNGEON] building arena '{}' at {}", structureId, site);
            com.thuvstu.hayatemod.build.StructureBuilder.place(level, site, template);
        }
        clearInterior(level);
        placeTorches(level);
        return true;
    }

    /** Torches keep stray mobs from spawning inside during night fights. */
    private static void placeTorches(ServerLevel level) {
        for (int x = -half() + 2; x <= half() - 2; x += 10) {
            for (int z = -half() + 2; z <= half() - 2; z += 10) {
                BlockPos p = origin().offset(x, 0, z);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.TORCH.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Clears terrain/water inside the box so spawns never get buried. */
    private static void clearInterior(ServerLevel level) {
        for (int x = -half() + 1; x <= half() - 1; x++) {
            for (int z = -half() + 1; z <= half() - 1; z++) {
                for (int y = 0; y <= 6; y++) {
                    BlockPos p = origin().offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** Discards every session-tagged non-player entity near the arena. Returns the removed count. */
    public static int sweep(ServerLevel level) {
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getEntities(EntityTypeTest.forClass(Entity.class),
                e -> e.getTags().contains("solommo_session")
                        && !(e instanceof net.minecraft.server.level.ServerPlayer))) {
            if (e.position().distanceTo(new Vec3(origin().getX(), origin().getY(), origin().getZ())) <= 48.0) {
                found.add(e);
            }
        }
        for (Entity e : found) {
            e.discard();
        }
        return found.size();
    }

    /** Residue check for S2: session entities that survived a cleanup. */
    public static List<String> residue(ServerLevel level) {
        List<String> out = new ArrayList<>();
        for (Entity e : level.getEntities(EntityTypeTest.forClass(Entity.class),
                e -> e.getTags().contains("solommo_session"))) {
            if (e.position().distanceTo(new Vec3(origin().getX(), origin().getY(), origin().getZ())) <= 48.0) {
                out.add(e.getScoreboardName() + "<" + e.getType().getDescriptionId() + ">");
            }
        }
        return out;
    }

    public static List<LivingEntity> sessionMobs(ServerLevel level, UUID excludeBoss) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && e.getTags().contains("solommo_session")
                        && !e.getUUID().equals(excludeBoss))) {
            if (e.position().distanceTo(new Vec3(origin().getX(), origin().getY(), origin().getZ())) <= 48.0
                    && !(e instanceof net.minecraft.server.level.ServerPlayer)) {
                out.add(e);
            }
        }
        return out;
    }
}
