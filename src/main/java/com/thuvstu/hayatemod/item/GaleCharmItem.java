package com.thuvstu.hayatemod.item;

import java.util.List;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * A single-use charm: right-clicking it grants a burst of speed and consumes the item.
 */
public class GaleCharmItem extends SingleUseEffectItem {
	/** 30 seconds, in ticks (20 ticks = 1 second). */
	private static final int DURATION_TICKS = 30 * 20;
	/** Amplifier 1 = Speed II. */
	private static final int AMPLIFIER = 1;

	public GaleCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	protected List<MobEffectInstance> effects() {
		return List.of(new MobEffectInstance(MobEffects.SPEED, DURATION_TICKS, AMPLIFIER));
	}

	@Override
	protected SoundEvent sound() {
		return SoundEvents.WIND_CHARGE_THROW;
	}

	@Override
	protected String messageKey() {
		return "message.hayatemod.gale_charm.used";
	}

	@Override
	protected String tooltipKey() {
		return "itemTooltip.hayatemod.gale_charm";
	}
}
