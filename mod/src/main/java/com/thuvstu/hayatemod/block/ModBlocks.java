package com.thuvstu.hayatemod.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Ember ore (underground, drops cinder_iron with fortune) and ember buds
 * (surface clusters, drop ember_glass). Loot tables are datapack JSON.
 */
public final class ModBlocks {
    public static Block EMBER_ORE;
    public static Block EMBER_BUD;

    private ModBlocks() {
    }

    public static void register() {
        EMBER_ORE = Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath("hayatemod", "ember_ore"),
                new Block(blockProps("ember_ore").strength(3.0F, 3.0F)
                        .requiresCorrectToolForDrops().sound(SoundType.STONE)));
        EMBER_BUD = Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath("hayatemod", "ember_bud"),
                new EmberBudBlock(blockProps("ember_bud").strength(0.2F)
                        .sound(SoundType.GRASS).noOcclusion()));
        registerBlockItem("ember_ore", EMBER_ORE);
        registerBlockItem("ember_bud", EMBER_BUD);
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
                .modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> {
                    entries.accept(EMBER_ORE);
                    entries.accept(EMBER_BUD);
                });
    }
    private static BlockBehaviour.Properties blockProps(String path) {
        // 1.21.11 requires the id on block Properties too (else "Block id not set").
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("hayatemod", path)));
    }

    private static void registerBlockItem(String path, Block block) {
        Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("hayatemod", path),
                new BlockItem(block, new Item.Properties().setId(
                        ResourceKey.create(Registries.ITEM,
                                Identifier.fromNamespaceAndPath("hayatemod", path)))));
    }

    /** Surface bud: survives only on solid ground (worldgen would_survive). */
    public static final class EmberBudBlock extends Block {
        public EmberBudBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSurvive(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.LevelReader level,
                net.minecraft.core.BlockPos pos) {
            net.minecraft.core.BlockPos below = pos.below();
            return level.getBlockState(below).isFaceSturdy(level, below,
                    net.minecraft.core.Direction.UP);
        }
    }
}
