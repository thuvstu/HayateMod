package com.thuvstu.hayatemod.block;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

/**
 * Every block added by HayateMod.
 *
 * <p>Each block is registered together with its {@link BlockItem}; since 26.2 the pair is
 * described by a single {@link BlockItemId}.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final Block GALE_BLOCK = register(ModBlockItemIds.GALE_BLOCK, Block::new,
			BlockBehaviour.Properties.of()
					.sound(SoundType.METAL)
					.strength(3.0F, 6.0F)
					.requiresCorrectToolForDrops());

	public static final Block GALE_LAMP = register(ModBlockItemIds.GALE_LAMP, GaleLampBlock::new,
			BlockBehaviour.Properties.of()
					.sound(SoundType.LANTERN)
					.lightLevel(GaleLampBlock::getLuminance)
					.strength(1.5F));

	/** Overworld ore, mined for {@link com.thuvstu.hayatemod.item.ModItems#GALE_DUST}. */
	public static final Block GALE_ORE = register(ModBlockItemIds.GALE_ORE, Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE));

	public static final Block DEEPSLATE_GALE_ORE = register(ModBlockItemIds.DEEPSLATE_GALE_ORE, Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE));

	/** Wind-scoured ash; carpets the floor of the Gale Hollow. */
	public static final Block GALE_ASH = register(ModBlockItemIds.GALE_ASH, Block::new,
			BlockBehaviour.Properties.of()
					.sound(SoundType.SAND)
					.strength(0.5F)
					.requiresCorrectToolForDrops());

	// ------------------------------------------------------------ plumbing

	/** Registers a block and, because a {@link BlockItemId} is used, its block item too. */
	public static Block register(BlockItemId id, Function<BlockBehaviour.Properties, Block> blockFactory,
			BlockBehaviour.Properties properties) {
		Block block = register(id.block(), blockFactory, properties);

		BlockItem blockItem = new BlockItem(block,
				new Item.Properties().setId(id.item()).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, id.item(), blockItem);

		return block;
	}

	/** Registers a block without an item. */
	public static Block register(ResourceKey<Block> id, Function<BlockBehaviour.Properties, Block> blockFactory,
			BlockBehaviour.Properties properties) {
		Block block = blockFactory.apply(properties.setId(id));

		return Registry.register(BuiltInRegistries.BLOCK, id, block);
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			output.accept(ModBlocks.GALE_BLOCK);
			output.accept(ModBlocks.GALE_LAMP);
			output.accept(ModBlocks.GALE_ORE);
			output.accept(ModBlocks.DEEPSLATE_GALE_ORE);
			output.accept(ModBlocks.GALE_ASH);
		});
	}
}
