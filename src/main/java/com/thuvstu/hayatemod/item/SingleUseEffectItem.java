package com.thuvstu.hayatemod.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A single-use item: right-clicking grants fixed effects and consumes one item.
 *
 * <p>Note how the class never touches client-only classes - it lives in the common
 * source set and runs identically on the server and the client.
 */
public abstract class SingleUseEffectItem extends Item {
	protected SingleUseEffectItem(Properties properties) {
		super(properties);
	}

	/** Effects applied on use. */
	protected abstract List<MobEffectInstance> effects();

	/** Sound played on use. */
	protected abstract SoundEvent sound();

	/** Pitch of {@link #sound()}. */
	protected float soundPitch() {
		return 1.0F;
	}

	/** Translation key of the chat message sent on use. */
	protected abstract String messageKey();

	/** Translation key of the hover tooltip. */
	protected abstract String tooltipKey();

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		// Client side: just swallow the interaction, the server does the real work.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		// Copy each effect: MobEffectInstance is mutable and must not be shared
		// between uses.
		for (MobEffectInstance effect : effects()) {
			user.addEffect(new MobEffectInstance(effect));
		}
		user.level().playSound(null, user.getX(), user.getY(), user.getZ(), sound(),
				SoundSource.PLAYERS, 1.0F, soundPitch());

		// Single use.
		user.getItemInHand(hand).shrink(1);
		user.sendSystemMessage(Component.translatable(messageKey()));

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		ItemUsage.addTooltip(tooltip, tooltipKey());
	}
}
