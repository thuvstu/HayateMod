package com.thuvstu.hayatemod.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A single-use charm: right-clicking it grants a burst of speed and consumes the item.
 *
 * <p>Note how the class never touches client-only classes - it lives in the common
 * source set and runs identically on the server and the client.
 */
public class GaleCharmItem extends Item {
	/** 30 seconds, in ticks (20 ticks = 1 second). */
	private static final int DURATION_TICKS = 30 * 20;
	/** Amplifier 1 = Speed II. */
	private static final int AMPLIFIER = 1;

	public GaleCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		// Client side: just swallow the interaction, the server does the real work.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		user.addEffect(new MobEffectInstance(MobEffects.SPEED, DURATION_TICKS, AMPLIFIER));
		user.level().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.WIND_CHARGE_THROW,
				SoundSource.PLAYERS, 1.0F, 1.0F);

		// Single use.
		user.getItemInHand(hand).shrink(1);
		user.sendSystemMessage(Component.translatable("message.hayatemod.gale_charm.used"));

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("itemTooltip.hayatemod.gale_charm").withStyle(ChatFormatting.AQUA));
	}
}
