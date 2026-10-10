package com.thuvstu.hayatemod.item;

import java.util.function.Consumer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.thuvstu.hayatemod.util.Particles;

/**
 * A reusable staff: right-clicking pushes the holder along their look direction.
 *
 * <p>Demonstrates three things a mod item usually needs:
 * <ul>
 *     <li>a {@code UseCooldown} component (declared in {@link ModItems}), so the item
 *         greys out in the hotbar for a moment</li>
 *     <li>durability, spent with {@link ItemStack#hurtAndBreak}</li>
 *     <li>server side movement plus a client visible sound/particles</li>
 * </ul>
 *
 * <p>Cooldown and durability are applied through {@link ItemUsage}.
 */
public class GaleStaffItem extends Item {
	/** How hard the dash pushes, in blocks per tick. */
	private static final double PUSH = 1.15;
	/** Keeps the dash useful when looking straight ahead. */
	private static final double LIFT = 0.42;

	public GaleStaffItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		Vec3 look = user.getLookAngle();
		Vec3 velocity = user.getDeltaMovement();
		user.setDeltaMovement(velocity.x + look.x * PUSH,
				Math.max(velocity.y, 0.0) + LIFT + Math.max(0.0, look.y) * 0.5,
				velocity.z + look.z * PUSH);
		user.resetFallDistance();

		level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.WIND_CHARGE_THROW,
				SoundSource.PLAYERS, 1.0F, 1.2F);
		if (level instanceof ServerLevel serverLevel) {
			Particles.burst(serverLevel, ParticleTypes.CLOUD, user.getX(), user.getY() + 0.5, user.getZ(),
					12, 0.4, 0.3, 0.4);
		}

		// Cooldown declared on the item, and one durability point per dash.
		ItemUsage.applyCooldownAndDamage(stack, user);

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		ItemUsage.addTooltip(tooltip, "itemTooltip.hayatemod.gale_staff");
	}
}
