package com.thuvstu.hayatemod.build;

import java.util.Map;

import com.thuvstu.hayatemod.core.build.StructurePlanner;
import com.thuvstu.hayatemod.core.content.model.Models.StructTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes materialized structure templates in the world (ST1). The palette
 * mirrors {@link StructurePlanner#ALLOWED_BLOCKS}; unknown ids are skipped
 * loudly rather than crashing the tick.
 */
public final class StructureBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/build");

    private static final Map<String, Block> PALETTE = Map.ofEntries(
            Map.entry("minecraft:air", Blocks.AIR),
            Map.entry("minecraft:bedrock", Blocks.BEDROCK),
            Map.entry("minecraft:obsidian", Blocks.OBSIDIAN),
            Map.entry("minecraft:glowstone", Blocks.GLOWSTONE),
            Map.entry("minecraft:torch", Blocks.TORCH),
            Map.entry("minecraft:oak_log", Blocks.OAK_LOG),
            Map.entry("minecraft:oak_planks", Blocks.OAK_PLANKS),
            Map.entry("minecraft:oak_fence", Blocks.OAK_FENCE),
            Map.entry("minecraft:cobblestone", Blocks.COBBLESTONE),
            Map.entry("minecraft:gravel", Blocks.GRAVEL),
            Map.entry("minecraft:water", Blocks.WATER),
            Map.entry("minecraft:crafting_table", Blocks.CRAFTING_TABLE),
            Map.entry("minecraft:furnace", Blocks.FURNACE),
            Map.entry("minecraft:anvil", Blocks.ANVIL),
            Map.entry("minecraft:farmland", Blocks.FARMLAND),
            Map.entry("minecraft:wheat", Blocks.WHEAT));

    private StructureBuilder() {
    }

    /** Places every block of the template relative to {@code origin}. Returns the placed count. */
    public static int place(ServerLevel level, BlockPos origin, StructTemplate template) {
        int placed = 0;
        for (var b : StructurePlanner.materialize(template)) {
            Block block = PALETTE.get(b.block());
            if (block == null) {
                LOGGER.warn("[build] '{}' uses '{}' which is outside the palette; skipped",
                        template.id(), b.block());
                continue;
            }
            level.setBlock(origin.offset(b.x(), b.y(), b.z()),
                    block.defaultBlockState(), 3);
            placed++;
        }
        LOGGER.info("[build] placed {} blocks of '{}' at {}", placed, template.id(), origin);
        return placed;
    }
}
