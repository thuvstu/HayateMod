package com.thuvstu.hayatemod.build;

import java.util.Optional;

import com.thuvstu.hayatemod.core.build.GroundFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks real ground for the town and the arena (land above sea level, gentle
 * slope). Deterministic per world: the content hint is the search center and
 * candidates are visited nearest-first, so the result is stable without
 * persistence. Falls back to the legacy fixed coords only when no land
 * exists in range (pure ocean world).
 */
public final class GroundSites {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/build");

    private static final int RADIUS = 192;
    private static final int STEP = 16;
    private static final int SLOPE = 3;

    private static ServerLevel townLevel;
    private static BlockPos townSite;
    private static ServerLevel arenaLevel;
    private static BlockPos arenaSite;

    private GroundSites() {
    }

    public static BlockPos townSite(ServerLevel level, int hintX, int hintZ, int half) {
        if (level != townLevel || townSite == null) {
            townSite = find(level, hintX, hintZ, half, new BlockPos(hintX,
                    level.getSeaLevel() + 1, hintZ), "town");
            townLevel = level;
        }
        return townSite;
    }

    public static java.util.Optional<BlockPos> peekTown() {
        return java.util.Optional.ofNullable(townSite);
    }

    public static java.util.Optional<BlockPos> peekArena() {
        return java.util.Optional.ofNullable(arenaSite);
    }

    /**
     * Spawn-first town search (spawn village guarantee): tries the world
     * spawn, then the content hint, then the given fallback.
     */
    public static BlockPos townSiteSpawn(ServerLevel level, int hintX, int hintZ, int half,
            BlockPos fallback) {
        if (level == townLevel && townSite != null) {
            return townSite;
        }
        BlockPos spawn = null;
        try {
            var respawn = level.getRespawnData();
            if (respawn != null
                    && respawn.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                spawn = respawn.pos();
            }
        } catch (Exception e) {
            LOGGER.warn("[build] spawn lookup failed, using hint: {}", e.getMessage());
        }
        Optional<GroundFinder.Found> near = Optional.empty();
        if (spawn != null) {
            near = GroundFinder.find((x, z) -> sample(level, x, z), spawn.getX(), spawn.getZ(),
                    level.getSeaLevel(), half, SLOPE, 160, STEP);
        }
        BlockPos site;
        if (near.isPresent()) {
            site = new BlockPos(near.get().x(), near.get().y(), near.get().z());
            LOGGER.info("[build] town site at spawn: {}", site);
        } else {
            site = find(level, hintX, hintZ, half, fallback, "town");
        }
        townSite = site;
        townLevel = level;
        return site;
    }

    public static BlockPos arenaSite(ServerLevel level, int hintX, int hintZ, int half,
            BlockPos legacy) {
        if (level != arenaLevel || arenaSite == null) {
            arenaSite = find(level, hintX, hintZ, half, legacy, "arena");
            arenaLevel = level;
        }
        return arenaSite;
    }

    private static BlockPos find(ServerLevel level, int hintX, int hintZ, int half,
            BlockPos fallback, String what) {
        Optional<GroundFinder.Found> found = GroundFinder.find(
                (x, z) -> sample(level, x, z), hintX, hintZ, level.getSeaLevel(), half, SLOPE,
                RADIUS, STEP);
        if (found.isPresent()) {
            BlockPos site = new BlockPos(found.get().x(), found.get().y(), found.get().z());
            LOGGER.info("[build] {} site: {} (hint {}, {})", what, site, hintX, hintZ);
            return site;
        }
        LOGGER.warn("[build] no land for {} near ({}, {}); using fallback {}",
                what, hintX, hintZ, fallback);
        return fallback;
    }

    private static GroundFinder.Column sample(ServerLevel level, int x, int z) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos surface = new BlockPos(x, h, z);
        BlockPos below = new BlockPos(x, h - 1, z);
        if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(below).isEmpty()) {
            return new GroundFinder.Column(h, false);
        }
        var state = level.getBlockState(below);
        if (state.isAir() || state.is(BlockTags.LEAVES)
                || !state.isCollisionShapeFullBlock(level, below)) {
            return new GroundFinder.Column(h, false);
        }
        return new GroundFinder.Column(h, true);
    }
}
