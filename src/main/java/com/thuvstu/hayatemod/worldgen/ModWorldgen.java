package com.thuvstu.hayatemod.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import com.thuvstu.hayatemod.HayateMod;

/**
 * Hooks the mod's world generation into the overworld.
 *
 * <p>Since 26.1 features are plain data pack content, so the ore itself is described by
 * JSON (see {@code src/main/resources/data/hayatemod/worldgen/}) and only the *biome*
 * attachment is done in code:
 *
 * <ul>
 *     <li>{@code data/hayatemod/worldgen/feature/gale_ore.json} - what to place</li>
 *     <li>{@code data/hayatemod/worldgen/placed_feature/gale_ore.json} - where to place it</li>
 *     <li>{@link #initialize()} - which biomes get it</li>
 * </ul>
 */
public final class ModWorldgen {
	private ModWorldgen() {
	}

	/** Must match {@code data/hayatemod/worldgen/placed_feature/gale_ore.json}. */
	public static final ResourceKey<PlacedFeature> GALE_ORE_PLACED = ResourceKey.create(
			Registries.PLACED_FEATURE, HayateMod.id("gale_ore"));

	public static void initialize() {
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				GALE_ORE_PLACED);
	}
}
