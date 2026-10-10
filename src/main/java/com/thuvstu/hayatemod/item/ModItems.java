package com.thuvstu.hayatemod.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.component.UseCooldown;
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

	/** The gale trim material, registered by {@code data/hayatemod/trim_material/gale.json}. */
	public static final ResourceKey<TrimMaterial> GALE_TRIM_MATERIAL = ResourceKey.create(
			Registries.TRIM_MATERIAL, HayateMod.id("gale"));

	/** Gust + ember, ground into a fine powder. Crafted from a feather and blaze powder. */
	public static final Item GALE_DUST = register(ModItemIds.GALE_DUST, Item::new, new Item.Properties());

	/**
	 * Smelted from {@link #GALE_DUST}. The basic material of the mod - and, thanks to
	 * {@code trimMaterial}, a colour option at the smithing table (see
	 * {@code data/hayatemod/trim_material/gale.json}).
	 */
	public static final Item GALE_INGOT = register(ModItemIds.GALE_INGOT, Item::new,
			new Item.Properties().trimMaterial(GALE_TRIM_MATERIAL));

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
			new Item.Properties().stacksTo(16)
					.component(DataComponents.LORE, ItemUsage.lore("itemTooltip.hayatemod.gale_charm")));

	// -------------------------------------------- second batch: gear and parts

	/** A wind tinted feather. Cheap to make, used in every gale tool recipe. */
	public static final Item GALE_FEATHER = register(ModItemIds.GALE_FEATHER, Item::new, new Item.Properties());

	/** Wind compressed into a sphere; the core of the gale tools. */
	public static final Item GALE_ORB = register(ModItemIds.GALE_ORB, Item::new, new Item.Properties());

	/**
	 * A sword made of gale metal.
	 *
	 * <p>There is no {@code SwordItem} class any more: since 26.x the weapon and tool
	 * behaviour is attached through components, which {@code Properties#sword} sets up
	 * from a {@link net.minecraft.world.item.ToolMaterial}.
	 */
	public static final Item GALE_BLADE = register(ModItemIds.GALE_BLADE, Item::new,
			new Item.Properties().sword(ModToolMaterials.GALE, 3.0F, -2.2F));

	/** Same material, as a pickaxe. */
	public static final Item GALE_PICKAXE = register(ModItemIds.GALE_PICKAXE, Item::new,
			new Item.Properties().pickaxe(ModToolMaterials.GALE, 1.0F, -2.8F));

	/** Right-click to dash. See {@link GaleStaffItem}. */
	public static final Item GALE_STAFF = register(ModItemIds.GALE_STAFF, GaleStaffItem::new,
			new Item.Properties()
					.durability(256)
					.component(DataComponents.USE_COOLDOWN, new UseCooldown(1.5F))
					.component(DataComponents.LORE, ItemUsage.lore("itemTooltip.hayatemod.gale_staff")));

	/**
	 * Right-click to blow everything in front of you away. See {@link GaleFanItem}.
	 *
	 * <p>Note the {@code UseCooldown} component: it lives on the stack, so the item
	 * greys out in the hotbar for three seconds after every gust.
	 */
	public static final Item GALE_FAN = register(ModItemIds.GALE_FAN, GaleFanItem::new,
			new Item.Properties()
					.durability(128)
					.component(DataComponents.USE_COOLDOWN, new UseCooldown(3.0F))
					.component(DataComponents.LORE, ItemUsage.lore("itemTooltip.hayatemod.gale_fan")));

	// --------------------------------------------- third batch: the gale armour

	/**
	 * The four armour pieces.
	 *
	 * <p>{@code Item.Properties#humanoidArmor} attaches the {@code equippable} component
	 * (slot, equip sound, equipment asset) and the defence attributes for us.
	 */
	public static final Item GALE_HELMET = register(ModItemIds.GALE_HELMET, Item::new,
			new Item.Properties().humanoidArmor(ModArmorMaterials.GALE, ArmorType.HELMET));

	public static final Item GALE_CHESTPLATE = register(ModItemIds.GALE_CHESTPLATE, Item::new,
			new Item.Properties().humanoidArmor(ModArmorMaterials.GALE, ArmorType.CHESTPLATE));

	public static final Item GALE_LEGGINGS = register(ModItemIds.GALE_LEGGINGS, Item::new,
			new Item.Properties().humanoidArmor(ModArmorMaterials.GALE, ArmorType.LEGGINGS));

	public static final Item GALE_BOOTS = register(ModItemIds.GALE_BOOTS, Item::new,
			new Item.Properties().humanoidArmor(ModArmorMaterials.GALE, ArmorType.BOOTS));

	/** Speed III for 45 seconds and Slow Falling, at the cost of the charm. */
	public static final Item GREATER_GALE_CHARM = register(ModItemIds.GREATER_GALE_CHARM,
			GreaterGaleCharmItem::new, new Item.Properties().stacksTo(16)
					.component(DataComponents.LORE, ItemUsage.lore("itemTooltip.hayatemod.greater_gale_charm")));

	// ---------------------------------------------------------- creative tab

	public static final ResourceKey<CreativeModeTab> HAYATE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), HayateMod.id("hayate"));

	/**
	 * The vanilla tools tab.
	 *
	 * <p>Use the {@link CreativeModeTabs} constant directly: the registry id is
	 * {@code minecraft:tools_and_utilities}, not {@code minecraft:tools}.
	 */
	private static final ResourceKey<CreativeModeTab> TOOLS_TAB = CreativeModeTabs.TOOLS_AND_UTILITIES;

	public static final CreativeModeTab HAYATE_TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ModItems.GALE_INGOT))
			.title(Component.translatable("creativeTab.hayatemod"))
			.displayItems((params, output) -> {
				output.accept(ModItems.GALE_DUST);
				output.accept(ModItems.GALE_INGOT);
				output.accept(ModItems.STORM_FRUIT);
				output.accept(ModItems.GALE_CHARM);
				output.accept(ModItems.GREATER_GALE_CHARM);
				output.accept(ModItems.GALE_FEATHER);
				output.accept(ModItems.GALE_ORB);
				output.accept(ModItems.GALE_BLADE);
				output.accept(ModItems.GALE_PICKAXE);
				output.accept(ModItems.GALE_STAFF);
				output.accept(ModItems.GALE_FAN);
				output.accept(ModItems.GALE_HELMET);
				output.accept(ModItems.GALE_CHESTPLATE);
				output.accept(ModItems.GALE_LEGGINGS);
				output.accept(ModItems.GALE_BOOTS);

				// The tab builder also accepts blocks (via their BlockItem).
				output.accept(ModBlocks.GALE_BLOCK);
				output.accept(ModBlocks.GALE_LAMP);
				output.accept(ModBlocks.GALE_ORE);
				output.accept(ModBlocks.DEEPSLATE_GALE_ORE);
				output.accept(ModBlocks.GALE_ASH);
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

		// ... and drop the raw materials and craftable parts into the vanilla
		// ingredients tab as well.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(ModItems.GALE_DUST);
			output.accept(ModItems.GALE_INGOT);
			output.accept(ModItems.GALE_FEATHER);
			output.accept(ModItems.GALE_ORB);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.accept(ModItems.STORM_FRUIT));

		// ... and the gear next to the vanilla equivalents.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(ModItems.GALE_BLADE);
			output.accept(ModItems.GALE_STAFF);
			output.accept(ModItems.GALE_HELMET);
			output.accept(ModItems.GALE_CHESTPLATE);
			output.accept(ModItems.GALE_LEGGINGS);
			output.accept(ModItems.GALE_BOOTS);
		});

		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB)
				.register(output -> output.accept(ModItems.GALE_PICKAXE));
	}
}
