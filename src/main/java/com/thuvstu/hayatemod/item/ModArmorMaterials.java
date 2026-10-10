package com.thuvstu.hayatemod.item;

import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.thuvstu.hayatemod.HayateMod;

/**
 * The material of the gale armour set.
 *
 * <p>Two things are worth knowing about armour in 26.x:
 * <ul>
 *     <li>There is no {@code ArmorItem} class - an armour piece is a plain {@code Item}
 *         whose {@code Item.Properties#humanoidArmor} call attaches the equippable and
 *         attribute components.</li>
 *     <li>The texture is not referenced anywhere in code. It is looked up by convention
 *         from the {@link EquipmentAsset} id, so {@code hayatemod:gale} resolves to
 *         {@code assets/hayatemod/textures/entity/equipment/humanoid/gale.png}.</li>
 * </ul>
 */
public final class ModArmorMaterials {
	private ModArmorMaterials() {
	}

	/** Registered by {@code data/hayatemod/equipment/gale.json}. */
	public static final ResourceKey<EquipmentAsset> GALE_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID, HayateMod.id("gale"));

	/** Between iron and diamond: light, tough, and a little knockback resistant. */
	public static final ArmorMaterial GALE = new ArmorMaterial(
			24,    // durability multiplier, scaled per slot by ArmorType
			Map.of(
					ArmorType.HELMET, 3,
					ArmorType.CHESTPLATE, 7,
					ArmorType.LEGGINGS, 5,
					ArmorType.BOOTS, 3,
					ArmorType.BODY, 9),
			18,                                  // enchantability
			SoundEvents.ARMOR_EQUIP_DIAMOND,     // equip sound
			1.5F,                                // toughness
			0.1F,                                // knockback resistance
			ModToolMaterials.GALE_REPAIR_MATERIALS,
			GALE_ASSET);
}
