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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The big brother of {@link GaleCharmItem}: Speed III for 45 seconds plus Slow Falling,
 * at the cost of the charm.
 */
public class GreaterGaleCharmItem extends Item {
	private static final int SPEED_TICKS = 45 * 20;
	private static final int SLOW_FALLING_TICKS = 25 * 20;

	public GreaterGaleCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		user.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, 2));
		user.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0));
		user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);

		user.getItemInHand(hand).shrink(1);
		user.sendSystemMessage(Component.translatable("message.hayatemod.greater_gale_charm.used"));

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("itemTooltip.hayatemod.greater_gale_charm")
				.withStyle(ChatFormatting.AQUA));
	}
}
