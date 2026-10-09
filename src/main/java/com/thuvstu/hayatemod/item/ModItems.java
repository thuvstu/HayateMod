package com.thuvstu.hayatemod.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.block.ModBlocks;

/**
 * Every item added by HayateMod, plus the mod's own creative tab.
 */
public final class ModItems {
	private ModItems() {
	}

	// ------------------------------------------------------------------ items

	/** Gust + ember, ground into a fine powder. Crafted from a feather and blaze powder. */
	public static final Item GALE_DUST = register(ModItemIds.GALE_DUST, Item::new, new Item.Properties());

	/** Smelted from {@link #GALE_DUST}. The basic material of the mod. */
	public static final Item GALE_INGOT = register(ModItemIds.GALE_INGOT, Item::new, new Item.Properties());

	/** A food that also grants Speed II for 15 seconds. */
	public static final FoodProperties STORM_FRUIT_FOOD = new FoodProperties.Builder()
			.nutrition(5)
			.saturationModifier(0.6F)
			.alwaysEdible()
			.build();

	public static final Consumable STORM_FRUIT_CONSUMABLE = Consumables.defaultFood()
			.onConsume(new ApplyStatusEffectsConsumeEffect(
					new MobEffectInstance(MobEffects.SPEED, 15 * 20, 1), 1.0F))
			.build();

	public static final Item STORM_FRUIT = register(ModItemIds.STORM_FRUIT, Item::new,
			new Item.Properties().food(STORM_FRUIT_FOOD, STORM_FRUIT_CONSUMABLE));

	/** Single-use item that grants Speed II for 30 seconds. See {@link GaleCharmItem}. */
	public static final Item GALE_CHARM = register(ModItemIds.GALE_CHARM, GaleCharmItem::new,
			new Item.Properties().stacksTo(16));

	// ---------------------------------------------------------- creative tab

	public static final ResourceKey<CreativeModeTab> HAYATE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), HayateMod.id("hayate"));

	public static final CreativeModeTab HAYATE_TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ModItems.GALE_INGOT))
			.title(Component.translatable("creativeTab.hayatemod"))
			.displayItems((params, output) -> {
				output.accept(ModItems.GALE_DUST);
				output.accept(ModItems.GALE_INGOT);
				output.accept(ModItems.STORM_FRUIT);
				output.accept(ModItems.GALE_CHARM);

				// The tab builder also accepts blocks (via their BlockItem).
				output.accept(ModBlocks.GALE_BLOCK);
				output.accept(ModBlocks.GALE_LAMP);
				output.accept(ModBlocks.GALE_ORE);
				output.accept(ModBlocks.DEEPSLATE_GALE_ORE);
			})
			.build();

	// ------------------------------------------------------------ plumbing

	public static Item register(ResourceKey<Item> itemKey, Function<Item.Properties, Item> itemFactory,
			Item.Properties settings) {
		// Create the item instance using the registry key, so the item knows its own id.
		Item item = itemFactory.apply(settings.setId(itemKey));

		// Register the item.
		return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
	}

	public static void initialize() {
		// Register the custom creative tab.
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HAYATE_TAB_KEY, HAYATE_TAB);

		// ... and drop the raw materials into the vanilla ingredients tab as well.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(ModItems.GALE_DUST);
			output.accept(ModItems.GALE_INGOT);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.accept(ModItems.STORM_FRUIT));
	}
}
