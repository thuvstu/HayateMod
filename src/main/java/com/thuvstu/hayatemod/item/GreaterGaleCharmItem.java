package com.thuvstu.hayatemod.item;

import java.util.List;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The big brother of {@link GaleCharmItem}: Speed III for 45 seconds plus Slow Falling,
 * at the cost of the charm.
 */
public class GreaterGaleCharmItem extends SingleUseEffectItem {
	private static final int SPEED_TICKS = 45 * 20;
	private static final int SLOW_FALLING_TICKS = 25 * 20;

	public GreaterGaleCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	protected List<MobEffectInstance> effects() {
		return List.of(
				new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, 2),
				new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0));
	}

	@Override
	protected SoundEvent sound() {
		return SoundEvents.WIND_CHARGE_THROW;
	}

	@Override
	protected float soundPitch() {
		return 0.8F;
	}

	@Override
	protected String messageKey() {
		return "message.hayatemod.greater_gale_charm.used";
	}

	@Override
	protected String tooltipKey() {
		return "itemTooltip.hayatemod.greater_gale_charm";
	}
}
