package com.thuvstu.hayatemod.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import com.thuvstu.hayatemod.HayateMod;

/**
 * The tool tier of the gale equipment.
 *
 * <p>Since 26.x a tier is a plain {@link ToolMaterial} record: it takes the *tag* of the
 * blocks it cannot harvest, plus durability, mining speed, bonus attack damage,
 * enchantability and the tag of items that repair it.
 */
public final class ModToolMaterials {
	private ModToolMaterials() {
	}

	/** Items that repair gale equipment, see {@code data/hayatemod/tags/item/}. */
	public static final TagKey<Item> GALE_REPAIR_MATERIALS = TagKey.create(
			Registries.ITEM, HayateMod.id("gale_repair_materials"));

	/** Roughly diamond level, but faster and a little less durable. */
	public static final ToolMaterial GALE = new ToolMaterial(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
			512,   // durability
			8.0F,  // mining speed
			3.0F,  // attack damage bonus
			18,    // enchantability
			GALE_REPAIR_MATERIALS);
}
