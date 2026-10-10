package com.thuvstu.hayatemod.item;

import java.util.function.Consumer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A fan that shoves everything standing in front of the holder away.
 *
 * <p>Unlike {@link GaleStaffItem} this one acts on other entities, which makes it a
 * good place to show the two halves of a server side ability:
 *
 * <ul>
 *     <li>a cone test - {@code direction.dot(look) >= 0} keeps only what is in front</li>
 *     <li>a falloff - {@code 1 - distance / radius} so close targets fly further</li>
 * </ul>
 *
 * <p>Movement is applied through {@link Entity#setDeltaMovement}, which the server
 * replicates on its own; no packets needed.
 */
public class GaleFanItem extends Item {
	/** How far the gust reaches, in blocks. */
	private static final double RADIUS = 6.0;
	/** Sideways push at point blank range, in blocks per tick. */
	private static final double STRENGTH = 1.15;
	/** Upward push, so the gust also lifts. */
	private static final double LIFT = 0.55;
	/** Keep everything at or above this dot product - cos(75deg) for a 150 degree cone. */
	private static final double CONE = 0.2588190451;

	public GaleFanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		Vec3 look = user.getLookAngle().normalize();
		Vec3 origin = user.position().add(0.0, 1.0, 0.0);
		AABB area = AABB.ofSize(origin, RADIUS * 2, RADIUS * 2, RADIUS * 2);

		int pushed = 0;
		for (Entity target : level.getEntities(user, area)) {
			Vec3 away = target.position().subtract(origin);
			double distance = away.length();
			if (distance > RADIUS || distance < 1.0E-4) {
				continue;
			}

			Vec3 direction = away.scale(1.0 / distance);
			if (direction.dot(look) < CONE) {
				continue;
			}

			double falloff = 1.0 - distance / RADIUS;
			Vec3 velocity = target.getDeltaMovement();
			target.setDeltaMovement(velocity.x + direction.x * STRENGTH * falloff,
					Math.max(velocity.y, 0.0) + LIFT * falloff,
					velocity.z + direction.z * STRENGTH * falloff);
			target.resetFallDistance();
			pushed++;
		}

		level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.WIND_CHARGE_BURST,
				SoundSource.PLAYERS, 1.0F, 0.9F);
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.CLOUD, origin.x + look.x * 2.0,
					origin.y + look.y * 2.0, origin.z + look.z * 2.0,
					6 + pushed * 4, 1.2, 0.8, 1.2, 0.06);
		}

		ItemUsage.applyCooldownAndDamage(stack, user);

		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		ItemUsage.addTooltip(tooltip, "itemTooltip.hayatemod.gale_fan");
	}
}
