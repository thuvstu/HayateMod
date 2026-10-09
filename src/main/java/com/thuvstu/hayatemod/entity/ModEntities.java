package com.thuvstu.hayatemod.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.worldgen.ModWorldgen;

/**
 * The mod's entity types.
 *
 * <p>Entities are the one thing here that still needs Java on both sides: the type
 * has to be registered before the world is loaded, and the renderer is wired up
 * separately in {@code src/client}.
 */
public final class ModEntities {
	private ModEntities() {
	}

	/** Must match the id used by the spawn entries and by the client renderer. */
	public static final ResourceKey<EntityType<?>> GALE_SPIRIT_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, HayateMod.id("gale_spirit"));

	public static final EntityType<GaleSpiritEntity> GALE_SPIRIT = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			GALE_SPIRIT_KEY,
			EntityType.Builder.of(GaleSpiritEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.9F)
					.eyeHeight(0.55F)
					.clientTrackingRange(8)
					.build(GALE_SPIRIT_KEY));

	public static void initialize() {
		// Without an AttributeSupplier the mob throws as soon as the AI reads one.
		FabricDefaultAttributeRegistry.register(GALE_SPIRIT, GaleSpiritEntity.createAttributes());

		// ... and it only ever shows up in the mod's own two biomes.
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(ModWorldgen.GALE_HOLLOW, ModWorldgen.GALE_HEIGHTS),
				MobCategory.CREATURE, GALE_SPIRIT, 24, 1, 2);
	}
}
