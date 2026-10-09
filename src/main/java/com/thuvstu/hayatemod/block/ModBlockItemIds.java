package com.thuvstu.hayatemod.block;

import net.minecraft.references.BlockItemId;

import com.thuvstu.hayatemod.HayateMod;

/**
 * Ids of the blocks that also have a {@link net.minecraft.world.item.BlockItem}.
 *
 * <p>Since Minecraft 26.2 a block and its item share a {@link BlockItemId}; the single
 * string form used below gives the block and the item the same name.
 */
public final class ModBlockItemIds {
	private ModBlockItemIds() {
	}

	public static final BlockItemId GALE_BLOCK = create("gale_block");
	public static final BlockItemId GALE_LAMP = create("gale_lamp");
	public static final BlockItemId GALE_ORE = create("gale_ore");
	public static final BlockItemId DEEPSLATE_GALE_ORE = create("deepslate_gale_ore");

	private static BlockItemId create(String name) {
		return BlockItemId.create(HayateMod.id(name), HayateMod.id(name));
	}
}
