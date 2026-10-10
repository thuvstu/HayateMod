package com.thuvstu.hayatemod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import com.thuvstu.hayatemod.block.ModBlocks;
import com.thuvstu.hayatemod.entity.ModEntities;
import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.worldgen.ModWorldgen;

public class HayateMod implements ModInitializer {
	public static final String MOD_ID = "hayatemod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Builds an {@link Identifier} in this mod's namespace, e.g. {@code hayatemod:gale_ingot}. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("HayateMod: booting on Minecraft {} (unobfuscated)", "26.3");

		// Touching these classes runs their static initialisers, which is where every
		// registry entry of this mod is created. Static initialisation is the pattern
		// vanilla itself uses (see net.minecraft.world.item.Items).
		ModItems.initialize();
		ModBlocks.initialize();
		// ModWorldgen spawns the gale spirit, so the entity type has to exist first.
		ModEntities.initialize();
		ModWorldgen.initialize();
	}
}
