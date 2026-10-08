package com.thuvstu.hayatemod.world;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Injects datapack-defined placed features into overworld biomes. */
public final class ModWorldgen {
    private ModWorldgen() {
    }

    public static void register() {
        // Tag-based (is_overworld) instead of foundInOverworld: deterministic
        // at finalize time, no biome-source identity involved.
        var overworld = BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OVERWORLD);
        BiomeModifications.addFeature(overworld,
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registries.PLACED_FEATURE,
                        Identifier.fromNamespaceAndPath("hayatemod", "ember_ore")));
        BiomeModifications.addFeature(overworld,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE,
                        Identifier.fromNamespaceAndPath("hayatemod", "ember_bud")));
    }
}
