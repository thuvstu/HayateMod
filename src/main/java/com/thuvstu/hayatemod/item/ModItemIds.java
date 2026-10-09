package com.thuvstu.hayatemod.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.thuvstu.hayatemod.HayateMod;

/**
 * Holds the {@link ResourceKey} of every item this mod adds.
 *
 * <p>Keys are resolved before the registries are populated, which makes them safe to
 * reference from item definitions (and from data generation for tags).
 */
public final class ModItemIds {
	private ModItemIds() {
	}

	public static final ResourceKey<Item> GALE_DUST = create("gale_dust");
	public static final ResourceKey<Item> GALE_INGOT = create("gale_ingot");
	public static final ResourceKey<Item> STORM_FRUIT = create("storm_fruit");
	public static final ResourceKey<Item> GALE_CHARM = create("gale_charm");

	private static ResourceKey<Item> create(String name) {
		return ResourceKey.create(Registries.ITEM, HayateMod.id(name));
	}
}
