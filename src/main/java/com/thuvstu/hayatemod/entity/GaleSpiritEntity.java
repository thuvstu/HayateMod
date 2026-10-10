package com.thuvstu.hayatemod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A drifting gale spirit: harmless, curious, and never touching the ground.
 *
 * <p>Two things make an entity fly in 26.x, and neither of them is a
 * {@code FlyingMob} base class - 26.3 dropped it:
 *
 * <ul>
 *     <li>{@link #createNavigation} returning a {@link FlyingPathNavigation}</li>
 *     <li>a {@link FlyingMoveControl} assigned to {@code Mob#moveControl}</li>
 * </ul>
 *
 * <p>With {@code setNoGravity(true)} on top of that the mob just hangs in the air and
 * patrols with the ordinary stroll goals, which is all the personality it needs.
 */
public class GaleSpiritEntity extends PathfinderMob {
	/** How many degrees the spirits can turn per tick. */
	private static final int TURN_SPEED = 20;

	public GaleSpiritEntity(EntityType<? extends GaleSpiritEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.moveControl = new FlyingMoveControl<GaleSpiritEntity>(this, TURN_SPEED, true);
	}

	/**
	 * Spawn rule for {@code FabricEntityType.Builder}: a spirit only appears where there
	 * is room for it, so it never ends up inside a block.
	 */
	public static boolean checkSpiritSpawnRules(EntityType<GaleSpiritEntity> type,
			ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FLYING_SPEED, 0.55)
				.add(Attributes.FOLLOW_RANGE, 18.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new FlyingPathNavigation(this, level);
	}
}
