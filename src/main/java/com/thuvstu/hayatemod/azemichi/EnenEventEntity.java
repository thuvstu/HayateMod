package com.thuvstu.hayatemod.azemichi;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.thuvstu.hayatemod.entity.ModEntities;

/**
 * The marker of an azemichi encounter: a signboard on a post that stands by the
 * path (see the "How to add a mob" section of the README for the 26.x mob recipe).
 *
 * <p>The entity itself knows nothing about the game logic - it just stands there,
 * shows its custom name, and forwards right-clicks to
 * {@link EnenAzemichi#onEventInteract(Player, EnenEventEntity)}. It never despawns
 * (the run cleanup removes it) and never moves (it re-anchors itself every tick,
 * so players cannot shove it off the path).
 */
public class EnenEventEntity extends Mob {
	private EnenEventType type = EnenEventType.VENDING;
	private int progress;
	private boolean used;
	private UUID owner = UUID.randomUUID();
	private double anchorX;
	private double anchorY;
	private double anchorZ;

	public EnenEventEntity(EntityType<? extends EnenEventEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setInvulnerable(true);
	}

	/** Spawns the marker at {@code position} and hands it to the level. */
	public static EnenEventEntity create(ServerLevel level, UUID owner, EnenEventType type, Vec3 position) {
		EnenEventEntity entity = new EnenEventEntity(ModEntities.AZEMICHI_EVENT, level);
		entity.type = type;
		entity.owner = owner;
		entity.anchorX = position.x;
		entity.anchorY = position.y;
		entity.anchorZ = position.z;
		entity.applyName();
		entity.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
		level.addFreshEntity(entity);
		return entity;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	/** Spawn rule for {@code FabricEntityType.Builder}: markers only exist where a run puts them. */
	public static boolean neverSpawnsNaturally(EntityType<EnenEventEntity> type, ServerLevelAccessor level,
			EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return false;
	}

	private void applyName() {
		this.setCustomName(Component.translatable(this.type.nameKey()));
		this.setCustomNameVisible(true);
	}

	@Override
	protected void registerGoals() {
		// no goals at all: the marker just stands where the generator put it
	}

	@Override
	public void aiStep() {
		super.aiStep();
		// Re-anchor against being pushed; the client-side copy is where vanilla
		// renders the entity, so it must not snap there.
		if (!this.level().isClientSide()) {
			this.moveTo(this.anchorX, this.anchorY, this.anchorZ, this.getYRot(), this.getXRot());
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return EnenAzemichi.INSTANCE.onEventInteract(player, this);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void checkDespawn() {
		// the run cleanup (not distance) is what removes this entity
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("EventType", this.type.ordinal());
		output.putInt("Progress", this.progress);
		output.putBoolean("Used", this.used);
		output.store("Owner", UUIDUtil.CODEC, this.owner);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.type = EnenEventType.byId(input.getIntOr("EventType", 0));
		this.progress = input.getIntOr("Progress", 0);
		this.used = input.getBooleanOr("Used", false);
		input.read("Owner", UUIDUtil.CODEC).ifPresent(this::owner);
		this.applyName();
	}

	public EnenEventType type() {
		return this.type;
	}

	/** How many times the player already talked to this encounter. */
	public int progress() {
		return this.progress;
	}

	public void tickProgress() {
		this.progress++;
	}

	public boolean used() {
		return this.used;
	}

	public void markUsed() {
		this.used = true;
	}

	/** The UUID of the player whose run owns this marker. */
	public UUID ownerId() {
		return this.owner;
	}

	private void owner(UUID owner) {
		this.owner = owner;
	}
}
