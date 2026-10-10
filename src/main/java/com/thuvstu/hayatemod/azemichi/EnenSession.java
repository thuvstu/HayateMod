package com.thuvstu.hayatemod.azemichi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The state of one player's azemichi run.
 *
 * <p>The session is purely server side (it lives in {@link EnenAzemichi#INSTANCE}'s
 * map). What it knows:
 *
 * <ul>
 *     <li>the geometry of the generated corridor (center line, start, cap, surface)</li>
 *     <li>today's goal distance, which the encounter choices move up and down</li>
 *     <li>the furthest distance reached so far (the sidebar never counts backwards,
 *         because in the original game you cannot walk back either)</li>
 *     <li>the entities this run created (yokai + encounter markers), so the run
 *         cleanup can remove them all</li>
 * </ul>
 */
public final class EnenSession {
	/** What kind of yokai a tracked entity is; it decides the kill reward. */
	public enum YokaiKind {
		SPIRIT_0, SPIRIT_1, SPIRIT_2, JOROGUMO, BISHATATSU
	}

	private final UUID playerId;
	private final ResourceKey<Level> levelKey;
	private final Vec3 entryPos;
	private final float entryYRot;
	private final float entryXRot;
	private final int centerX;
	private final int startZ;
	private final int capZ;
	private final int surfaceY;
	private final boolean bossRun;

	private int goalMeters;
	private int farthestMeters;
	private int battleWins;
	private int eventsUsed;
	private UUID bossUuid;

	private final Map<UUID, YokaiKind> trackedMobs = new HashMap<>();
	private final List<UUID> eventEntities = new ArrayList<>();
	private final Map<String, Boolean> announcements = new HashMap<>();

	EnenSession(UUID playerId, ResourceKey<Level> levelKey, Vec3 entryPos, float entryYRot, float entryXRot,
			int centerX, int startZ, int capZ, int surfaceY, int goalMeters, boolean bossRun) {
		this.playerId = playerId;
		this.levelKey = levelKey;
		this.entryPos = entryPos;
		this.entryYRot = entryYRot;
		this.entryXRot = entryXRot;
		this.centerX = centerX;
		this.startZ = startZ;
		this.capZ = capZ;
		this.surfaceY = surfaceY;
		this.goalMeters = goalMeters;
		this.bossRun = bossRun;
	}

	public UUID playerId() {
		return this.playerId;
	}

	public ResourceKey<Level> levelKey() {
		return this.levelKey;
	}

	/** Where the player stood when the run started; that is where the exit goes back. */
	public Vec3 entryPos() {
		return this.entryPos;
	}

	public float entryYRot() {
		return this.entryYRot;
	}

	public float entryXRot() {
		return this.entryXRot;
	}

	public int centerX() {
		return this.centerX;
	}

	public int startZ() {
		return this.startZ;
	}

	/** The last generated block of the corridor; the goal can never be past it. */
	public int capZ() {
		return this.capZ;
	}

	public int surfaceY() {
		return this.surfaceY;
	}

	/** True while Bishatatsu guards this run's torii. */
	public boolean isBossRun() {
		return this.bossRun;
	}

	public int goalMeters() {
		return this.goalMeters;
	}

	/** Where the torii stands: the goal, translated into corridor blocks. */
	public int goalZ() {
		return this.startZ + this.goalMeters / EnenAzemichi.METERS_PER_BLOCK;
	}

	public int farthestMeters() {
		return this.farthestMeters;
	}

	public void setFarthestMeters(int meters) {
		this.farthestMeters = Math.max(this.farthestMeters, meters);
	}

	public int battleWins() {
		return this.battleWins;
	}

	public void registerBattleWin() {
		this.battleWins++;
	}

	public int eventsUsed() {
		return this.eventsUsed;
	}

	public void registerEventUsed() {
		this.eventsUsed++;
	}

	/** The player has crossed the torii (with some slack for the gate's width). */
	public boolean isGoalReached(ServerPlayer player) {
		return player.getZ() >= this.goalZ() + 0.3D && Math.abs(player.getX() - this.centerX()) <= 8.0D;
	}

	/**
	 * Distance walked so far, in the game's friendly meters (never negative).
	 *
	 * <p>The counter only moves while the player is on the corridor: wandering the
	 * fields to the side, or cutting straight across the world, does not shorten
	 * the alley.
	 */
	public int metersNow(ServerPlayer player) {
		if (Math.abs(player.getX() - this.centerX) > AzemichiTerrain.HALF_WIDTH + 2) {
			return this.farthestMeters;
		}
		int blocks = Mth.clamp((int) (player.getZ() - this.startZ), 0,
				EnenAzemichi.MAX_METERS / EnenAzemichi.METERS_PER_BLOCK);
		return blocks * EnenAzemichi.METERS_PER_BLOCK;
	}

	/**
	 * An encounter moved the exit. Returns the new goal so the caller can refresh
	 * the HUD; the value is clamped between {@link EnenAzemichi#MIN_GOAL_METERS}
	 * and the corridor cap.
	 */
	public int adjustGoal(int deltaMeters) {
		this.goalMeters = Mth.clamp(this.goalMeters + deltaMeters, EnenAzemichi.MIN_GOAL_METERS,
				EnenAzemichi.MAX_METERS);
		return this.goalMeters;
	}

	/** Milestone bookkeeping: returns true only the first time a key is seen. */
	public boolean announce(String key) {
		return this.announcements.putIfAbsent(key, Boolean.TRUE) == null;
	}

	/** Bishatatsu of this run (null when the run has no boss). */
	public UUID bossUuid() {
		return this.bossUuid;
	}

	public void setBossUuid(UUID uuid) {
		this.bossUuid = uuid;
	}

	// ------------------------------------------------------------- tracking

	public void trackMob(UUID uuid, YokaiKind kind) {
		this.trackedMobs.put(uuid, kind);
	}

	/** Removes the tracking entry (entity died) and reports what kind it was. */
	public YokaiKind untrack(UUID uuid) {
		return this.trackedMobs.remove(uuid);
	}

	public void trackEvent(UUID uuid) {
		this.eventEntities.add(uuid);
	}

	/** Every entity this run created, for the end-of-run cleanup. */
	public Collection<UUID> trackedEntityUuids() {
		List<UUID> all = new ArrayList<>(this.trackedMobs.keySet());
		all.addAll(this.eventEntities);
		return all;
	}
}
