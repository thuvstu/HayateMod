package com.thuvstu.hayatemod.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;

/**
 * Shared helpers for usable items: cooldown handling, durability and tooltips.
 */
public final class ItemUsage {
	private ItemUsage() {
	}

	/**
	 * Applies the stack's {@link UseCooldown} component (if present) and spends one
	 * durability point. The cooldown lives on the stack, so the item greys out in
	 * the hotbar until it expires.
	 */
	public static void applyCooldownAndDamage(ItemStack stack, LivingEntity user) {
		UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
		if (cooldown != null) {
			cooldown.apply(stack, user);
		}
		stack.hurtAndBreak(1, user, user.getEquipmentSlotForItem(stack));
	}

	/** A single aqua lore line: the data-driven replacement for overriding tooltips. */
	public static ItemLore lore(String key) {
		Component raw = Component.translatable(key);
		return new ItemLore(List.of(raw), List.of(raw.copy().withStyle(ChatFormatting.AQUA)));
	}
}
