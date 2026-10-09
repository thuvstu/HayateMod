package com.thuvstu.hayatemod.core.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.thuvstu.hayatemod.core.content.model.Models.StructOp;
import com.thuvstu.hayatemod.core.content.model.Models.StructTemplate;

/**
 * Turns structure templates into explicit block placements (pure, testable).
 * The game only executes the resulting list.
 */
public final class StructurePlanner {
    /**
     * The structure palette: every block id a template may use. The mod's
     * placer maps exactly these ids; the validator rejects anything else.
     */
    public static final Set<String> ALLOWED_BLOCKS = Set.of(
            "minecraft:air", "minecraft:bedrock", "minecraft:obsidian",
            "minecraft:glowstone", "minecraft:torch", "minecraft:oak_log",
            "minecraft:oak_planks", "minecraft:oak_fence", "minecraft:cobblestone",
            "minecraft:gravel", "minecraft:water", "minecraft:crafting_table",
            "minecraft:furnace", "minecraft:anvil", "minecraft:farmland",
            "minecraft:wheat");

    private StructurePlanner() {
    }

    public record PlacedBlock(int x, int y, int z, String block) {
    }

    public static List<PlacedBlock> materialize(StructTemplate template) {
        List<PlacedBlock> out = new ArrayList<>();
        for (StructOp op : template.blocks()) {
            int x0 = Math.min(op.from().get(0), op.to().get(0));
            int x1 = Math.max(op.from().get(0), op.to().get(0));
            int y0 = Math.min(op.from().get(1), op.to().get(1));
            int y1 = Math.max(op.from().get(1), op.to().get(1));
            int z0 = Math.min(op.from().get(2), op.to().get(2));
            int z1 = Math.max(op.from().get(2), op.to().get(2));
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        out.add(new PlacedBlock(x, y, z, op.block()));
                    }
                }
            }
        }
        return out;
    }
}
