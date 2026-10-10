package com.thuvstu.hayatemod.azemichi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RemovalReason;
import net.minecraft.world.entity.Spider;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.scoreboard.Criteria;
import net.minecraft.world.level.scoreboard.DisplaySlot;
import net.minecraft.world.level.scoreboard.Objective;
import net.minecraft.world.level.scoreboard.Score;
import net.minecraft.world.level.scoreboard.Scoreboard;
import net.minecraft.core.particles.ParticleTypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.v3.LivingEntityEvents;
import net.fabricmc.fabric.api.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.entity.GaleSpiritEntity;
import com.thuvstu.hayatemod.entity.ModEntities;
import com.thuvstu.hayatemod.item.ModItems;

/**
 * The "en'en azemichi" - a recreation of the endless rice-paddy alley from
 * Yokai Watch 3, playable anywhere in the overworld.
 *
 * <p>How a run works:
 * <ol>
 *     <li>{@code /hayate enen} (permission-free, once per in-game day) generates a
 *         corridor of rice paddies around the player's feet and teleports them to
 *         the entrance. The sidebar shows the distance and today's goal.</li>
 *     <li>The player walks forward only (the distance never counts backwards, like
 *         in the game). Encounter markers stand by the path: vending machine, phone
 *         booth, old lady, train, the dazed man, scarecrow.</li>
 *     <li>Right-clicking a marker shows its question in chat; the player answers
 *         with the 1 / 2 / 3 keys (a tiny C2S payload). Answers move the goal up or
 *         down, give items, or start a battle with a gale spirit.</li>
 *     <li>Passing the torii at the goal clears the run: rewards, a clear counter
 *         (which decides next time's goal: 4949 m, 7979 m, ...), and the daily
 *         cooldown. After {@link #BOSS_AFTER_CLEARS} clears, the giant cat
 *         Bishatatsu guards the torii.</li>
 * </ol>
 *
 * <p>This class is both the mod entrypoint (it registers the tick handler, the
 * death hook and the C2S receiver) and the server-side singleton that owns every
 * active run: {@link #sessions} and {@link #pendingChoices}.
 */
public final class EnenAzemichi implements ModInitializer {
	public static final EnenAzemichi INSTANCE = new EnenAzemichi();

	/** One block walked is 5 m on the HUD - the game likes big numbers. */
	public static final int METERS_PER_BLOCK = 5;
	/** The longest alley that is generated (2000 blocks); goals are clamped to it. */
	public static final int MAX_METERS = 10000;
	/** The goal can never be closer than this, so the alley never collapses. */
	public static final int MIN_GOAL_METERS = 600;
	/** After this many clears, Bishatatsu waits at the torii. */
	public static final int BOSS_AFTER_CLEARS = 7;

	private static final String OBJECTIVE = "hayate_enen";
	private static final int CHOICE_TIMEOUT_TICKS = 20 * 30; // 30 seconds to think

	private final Map<UUID, EnenSession> sessions = new HashMap<>();
	private final Map<UUID, PendingChoice> pendingChoices = new HashMap<>();

	private EnenAzemichi() {
	}

	@Override
	public void onInitialize() {
		// The payload type must be known on both sides of the connection. The
		// main entrypoint runs on the physical client too, so one registration
		// covers both; in singleplayer the integrated server shares the static
		// registry, and on a dedicated server this line simply runs server side.
		PayloadTypeRegistry.playC2S().register(EnenChoiceC2SPayload.TYPE, EnenChoiceC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EnenChoiceC2SPayload.TYPE, (payload, context) ->
				INSTANCE.onChoiceKeyPressed(context.player(), payload.choice()));

		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onServerTick);
		LivingEntityEvents.ENTITY_LIVING_DEATH.register(INSTANCE::onLivingDeath);
	}

	// ------------------------------------------------------------ run control

	public boolean hasSession(ServerPlayer player) {
		return this.sessions.containsKey(player.getUUID());
	}

	public EnenSession sessionOf(ServerPlayer player) {
		return this.sessions.get(player.getUUID());
	}

	/**
	 * Starts a run at the player's feet.
	 *
	 * @return false (with the reason already in chat) when the player is already
	 *         inside an azemichi or the once-per-day cooldown has not passed
	 */
	public boolean startRun(ServerLevel level, ServerPlayer player, boolean force) {
		if (this.hasSession(player)) {
			player.sendSystemMessage(msg("commands.hayatemod.enen.active"));
			return false;
		}

		long today = level.getDayCount();
		if (!force && lastDayOf(player) == today) {
			player.sendSystemMessage(msg("commands.hayatemod.enen.daily"));
			return false;
		}

		int clears = clearsOf(player);
		boolean bossRun = clears >= BOSS_AFTER_CLEARS && !bossDefeatedOf(player);

		// The corridor is laid out on the player's feet; the surface is the top
		// block the player stands on, the centre line an even x.
		int surfaceY = Mth.floor(player.getY()) - 1;
		int centerX = Mth.floor(player.getX()) & ~1;
		int startZ = Mth.floor(player.getZ()) + 4;
		int capZ = startZ + MAX_METERS / METERS_PER_BLOCK;

		int goalMeters = Mth.clamp(baseGoal(clears) + level.random.nextInt(501), MIN_GOAL_METERS, MAX_METERS);
		int goalZ = startZ + goalMeters / METERS_PER_BLOCK;

		// Where the run started, so the exit can return the player to it.
		Vec3 entryPos = player.position();
		float entryYRot = player.getYRot();
		float entryXRot = player.getXRot();

		player.sendSystemMessage(msg("enen.hayatemod.generating"));
		HayateMod.LOGGER.info("EnenAzemichi: laying out a {} m alley at ({}, {}, {}), goal at z={}",
				goalMeters, centerX, surfaceY, startZ, goalZ);

		AzemichiTerrain.buildCorridor(level, centerX, surfaceY, startZ, capZ, level.random);
		AzemichiTerrain.buildPlaza(level, centerX, surfaceY, capZ);
		AzemichiTerrain.buildTorii(level, centerX, surfaceY, goalZ);

		EnenSession session = new EnenSession(player.getUUID(), level.dimension(), entryPos, entryYRot,
				entryXRot, centerX, startZ, capZ, surfaceY, goalMeters, bossRun);
		this.sessions.put(player.getUUID(), session);

		spawnEvents(level, session);
		spawnYokai(level, session);
		if (bossRun) {
			spawnBishatatsu(level, session);
		}

		// Off we go: to the entrance, facing down the alley (yaw 0 = +Z).
		player.teleport(new TeleportTransition(level,
				new Vec3(centerX + 0.5, surfaceY + 1, startZ - 3),
				Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));

		setLastDay(player, today);
		setupHud(level, session);
		announceIntro(player, session);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	/**
	 * Ends the run the way the scarecrow does: teleport back to where the run
	 * started, remove everything the run created. The daily cooldown stays spent.
	 */
	public void exitRun(ServerPlayer player, boolean viaScarecrow) {
		EnenSession session = this.sessions.get(player.getUUID());
		if (session == null) {
			player.sendSystemMessage(msg("commands.hayatemod.enen.not_in"));
			return;
		}
		this.sessions.remove(player.getUUID());
		this.pendingChoices.remove(player.getUUID());

		ServerLevel level = (ServerLevel) player.level();
		player.teleport(new TeleportTransition(level, session.entryPos(),
				Vec3.ZERO, session.entryYRot(), session.entryXRot(), TeleportTransition.DO_NOTHING));

		player.sendSystemMessage(msg(viaScarecrow
				? "enen.hayatemod.exit.scarecrow" : "commands.hayatemod.enen.exit"));
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

		cleanupRun(level, session);
		refreshHud(level.getServer());
	}

	/** The numbers of the current run, or the career stats when not running. */
	public void reportStatus(ServerPlayer player) {
		EnenSession session = this.sessionOf(player);
		if (session == null) {
			player.sendSystemMessage(msg("commands.hayatemod.enen.status.none", clearsOf(player)));
			return;
		}
		player.sendSystemMessage(msg("commands.hayatemod.enen.status",
				session.farthestMeters(), session.goalMeters(),
				session.battleWins(), session.eventsUsed(), clearsOf(player)));
	}

	/** The player disconnected mid-run: quietly tear the run down. */
	private void abandonRun(MinecraftServer server, EnenSession session) {
		this.sessions.remove(session.playerId());
		this.pendingChoices.remove(session.playerId());
		ServerLevel level = server.getLevel(session.levelKey());
		if (level != null) {
			cleanupRun(level, session);
		}
		refreshHud(server);
	}

	// ------------------------------------------------------------------- tick

	private void onServerTick(MinecraftServer server) {
		long now = server.getTickCount();

		for (PendingChoice pending : new ArrayList<>(this.pendingChoices.values())) {
			if (now > pending.expiresTick()) {
				this.pendingChoices.remove(pending.player().getUUID());
				if (!pending.player().hasDisconnected()) {
					pending.player().sendSystemMessage(msg("enen.hayatemod.choice.timeout"));
				}
			}
		}

		if (this.sessions.isEmpty()) {
			return;
		}

		for (EnenSession session : new ArrayList<>(this.sessions.values())) {
			ServerLevel level = server.getLevel(session.levelKey());
			if (level == null) {
				this.abandonRun(server, session);
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
			if (player == null) {
				this.abandonRun(server, session);
				continue;
			}
			if (player.level() != level || player.isDeadOrDying()) {
				continue; // changed dimension or is dead: the run waits for them
			}

			int meters = session.metersNow(player);
			if (meters > session.farthestMeters()) {
				session.setFarthestMeters(meters);
				updateHud(level, session);
				announceMilestones(player, session);
			}

			if (session.isGoalReached(player)) {
				onGoalReached(level, session, player);
			}
		}
	}

	/** The player crossed the torii - clear the run, unless the boss is in the way. */
	private void onGoalReached(ServerLevel level, EnenSession session, ServerPlayer player) {
		if (session.isBossRun() && bossAlive(level, session)) {
			if (session.announce("boss_warn")) {
				player.sendSystemMessage(msg("enen.hayatemod.boss.blocked"));
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 0.7F);
			}
			return;
		}
		clearRun(level, session, player);
	}

	private boolean bossAlive(ServerLevel level, EnenSession session) {
		return session.bossUuid() != null
				&& (level.getEntity(session.bossUuid()) instanceof LivingEntity living
						&& !living.isDeadOrDying());
	}

	/** The happy ending: rewards, stats, celebration, teleport to the plaza. */
	private void clearRun(ServerLevel level, EnenSession session, ServerPlayer player) {
		this.sessions.remove(player.getUUID());
		this.pendingChoices.remove(player.getUUID());

		int clears = clearsOf(player) + 1;
		setClears(player, clears);

		give(player, new ItemStack(ModItems.GALE_ORB));
		give(player, new ItemStack(ModItems.STORM_FRUIT, 3));
		give(player, new ItemStack(ModItems.GALE_DUST, 8));
		if (session.isBossRun()) {
			give(player, new ItemStack(ModItems.GALE_ORB, 2));
			give(player, new ItemStack(ModItems.GREATER_GALE_CHARM));
			setBossDefeated(player, true);
		}

		player.sendSystemMessage(Component.translatable("enen.hayatemod.clear.title")
				.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("enen.hayatemod.clear.stats",
				session.farthestMeters(), session.battleWins(), session.eventsUsed(), clears)
				.withStyle(ChatFormatting.YELLOW));

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(),
				24, 0.6, 0.4, 0.6, 0.3);

		// Beyond the torii, facing back at it (yaw 180 = -Z).
		player.teleport(new TeleportTransition(level,
				new Vec3(session.centerX() + 0.5, session.surfaceY() + 1, session.goalZ() + 6),
				Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING));

		cleanupRun(level, session);
		refreshHud(level.getServer());
	}

	/** Removes every entity the run created (yokai, boss, encounter markers). */
	private void cleanupRun(ServerLevel level, EnenSession session) {
		for (UUID uuid : session.trackedEntityUuids()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				entity.remove(RemovalReason.DISCARDED);
			}
		}
	}

	// -------------------------------------------------------------- spawning

	/**
	 * Places the encounter markers: always a scarecrow, plus a handful of random
	 * characters (each kind at most twice, like in the game), spread 150 m apart
	 * along the first 80 % of the alley.
	 */
	private void spawnEvents(ServerLevel level, EnenSession session) {
		int count = Mth.clamp(3 + session.goalMeters() / 2000, 3, 8);
		List<EnenEventType> pool = new ArrayList<>(List.of(
				EnenEventType.VENDING, EnenEventType.PHONE, EnenEventType.OLD_LADY,
				EnenEventType.TRAIN, EnenEventType.MONSTER));
		List<EnenEventType> picks = new ArrayList<>();
		for (int i = 0; i < count - 1 && !pool.isEmpty(); i++) {
			EnenEventType type = pool.get(level.random.nextInt(pool.size()));
			pool.remove(type);
			if (picks.contains(type)) {
				pool.add(type); // the second copy of a character is allowed
			}
			picks.add(type);
		}
		picks.add(EnenEventType.SCAREROW);

		int cursor = session.startZ() + Mth.floor((session.goalZ() - session.startZ()) * 0.08);
		for (EnenEventType type : picks) {
			int z = Mth.clamp(cursor + level.random.nextInt(120), session.startZ() + 10,
					session.goalZ() - 40);
			cursor = z + METERS_TO_BLOCKS(150);
			int x = session.centerX() + (level.random.nextBoolean() ? 5 : -5);
			Vec3 position = new Vec3(x + 0.5, session.surfaceY() + 1, z + 0.5);
			EnenEventEntity entity = EnenEventEntity.create(level, session.playerId(), type, position);
			session.trackEvent(entity.getUUID());
		}
	}

	private static int METERS_TO_BLOCKS(int meters) {
		return meters / METERS_PER_BLOCK;
	}

	/** The yokai of the path: gale spirits, the further you go the stronger they are. */
	private void spawnYokai(ServerLevel level, EnenSession session) {
		int count = Mth.clamp(2 + session.goalMeters() / 1500, 3, 9);
		for (int i = 0; i < count; i++) {
			double fraction = 0.05 + 0.85 * (i + level.random.nextDouble() * 0.8) / count;
			int z = session.startZ() + Mth.floor(fraction * (session.capZ() - session.startZ()));
			int x = session.centerX() + (level.random.nextBoolean() ? 1 : -1)
					* (2 + level.random.nextInt(7));
			spawnYokai(level, session, x, session.surfaceY() + 1.5, z,
					fraction < 0.35 ? 0 : fraction < 0.7 ? 1 : 2);
		}
	}

	/** Spawns one gale spirit yokai of the given rank and tracks it in the run. */
	private void spawnYokai(ServerLevel level, EnenSession session, int x, double y, int z, int rank) {
		GaleSpiritEntity spirit = new GaleSpiritEntity(ModEntities.GALE_SPIRIT, level);
		spirit.setPos(x + 0.5, y, z + 0.5);
		spirit.getAttribute(Attributes.MAX_HEALTH).setBaseValue(12.0 + rank * 14.0);
		// The spirit's attribute set (it is a PathfinderMob, not a Monster) may not
		// carry ATTACK_DAMAGE: the battle is about the hunt, not the damage.
		setBase(spirit.getAttribute(Attributes.ATTACK_DAMAGE), 4.0 + rank * 2.0);
		spirit.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.55 + rank * 0.08);
		String nameKey = rank == 0 ? "enen.hayatemod.yokai.0"
				: rank == 1 ? "enen.hayatemod.yokai.1" : "enen.hayatemod.yokai.2";
		spirit.setCustomName(Component.translatable(nameKey));
		spirit.setCustomNameVisible(true);
		level.addFreshEntity(spirit);
		session.trackMob(spirit.getUUID(), rank == 0 ? EnenSession.YokaiKind.SPIRIT_0
				: rank == 1 ? EnenSession.YokaiKind.SPIRIT_1 : EnenSession.YokaiKind.SPIRIT_2);
	}

	/**
	 * Bishatatsu: a giant gale spirit that waits just before the torii on the boss
	 * run. The run only clears after it is defeated (the goal check sees through
	 * it via {@link #bossAlive}).
	 */
	private void spawnBishatatsu(ServerLevel level, EnenSession session) {
		GaleSpiritEntity boss = new GaleSpiritEntity(ModEntities.GALE_SPIRIT, level);
		boss.setPos(session.centerX() + 0.5, session.surfaceY() + 2.5, session.goalZ() - 3.5);
		setBase(boss.getAttribute(Attributes.SCALE), 3.0);
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		setBase(boss.getAttribute(Attributes.ATTACK_DAMAGE), 12.0);
		boss.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.8);
		boss.setCustomName(Component.translatable("enen.hayatemod.yokai.bishatatsu"));
		boss.setCustomNameVisible(true);
		level.addFreshEntity(boss);
		session.trackMob(boss.getUUID(), EnenSession.YokaiKind.BISHATATSU);
		session.setBossUuid(boss.getUUID());
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 0.6F);
	}

	/** Attribute sets differ per mob; a missing attribute is simply skipped. */
	private static void setBase(AttributeInstance instance, double value) {
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	// --------------------------------------------------------------- events

	/**
	 * The marker was right-clicked: scarecrow escapes, old lady talks (and
	 * eventually something happens), everything else asks a question.
	 */
	public InteractionResult onEventInteract(Player interacting, EnenEventEntity event) {
		if (!(interacting instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		if (event.used()) {
			player.sendSystemMessage(msg("enen.hayatemod.event.used"));
			return InteractionResult.SUCCESS;
		}
		EnenSession session = this.sessionOf(player);
		if (session == null || !event.ownerId().equals(player.getUUID())) {
			player.sendSystemMessage(msg("enen.hayatemod.event.gone"));
			return InteractionResult.SUCCESS;
		}

		EnenEventType type = event.type();
		if (type == EnenEventType.SCAREROW) {
			this.exitRun(player, true);
			return InteractionResult.CONSUME;
		}
		if (type == EnenEventType.OLD_LADY) {
			return this.onOldLady(player, event, session);
		}
		return this.askChoice(player, event, session);
	}

	private InteractionResult onOldLady(ServerPlayer player, EnenEventEntity event, EnenSession session) {
		ServerLevel level = (ServerLevel) player.level();
		event.tickProgress();
		if (event.progress() < 4) {
			int bread = 1 + level.random.nextInt(3);
			give(player, new ItemStack(Items.BREAD, bread));
			player.sendSystemMessage(msg("enen.hayatemod.event.old_lady.feed",
					event.progress(), bread));
		} else {
			event.markUsed();
			session.registerEventUsed();
			spawnJorogumo(level, session, player);
			player.sendSystemMessage(msg("enen.hayatemod.event.old_lady.reveal"));
		}
		return InteractionResult.CONSUME;
	}

	private void spawnJorogumo(ServerLevel level, EnenSession session, ServerPlayer player) {
		Spider spider = new Spider(EntityType.Spider, level);
		spider.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0);
		spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7.0);
		spider.setCustomName(Component.translatable("enen.hayatemod.yokai.jorogumo"));
		spider.setCustomNameVisible(true);
		level.addFreshEntity(spider);
		session.trackMob(spider.getUUID(), EnenSession.YokaiKind.JOROGUMO);
		level.playSound(null, spider.getX(), spider.getY(), spider.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	private InteractionResult askChoice(ServerPlayer player, EnenEventEntity event, EnenSession session) {
		if (this.pendingChoices.containsKey(player.getUUID())) {
			player.sendSystemMessage(msg("enen.hayatemod.choice.busy"));
			return InteractionResult.SUCCESS;
		}
		ServerLevel level = (ServerLevel) player.level();
		EnenEventType type = event.type();

		this.pendingChoices.put(player.getUUID(), new PendingChoice(player, event,
				level.getServer().getTickCount() + CHOICE_TIMEOUT_TICKS));
		player.sendSystemMessage(msg(type.questionKey()));
		for (int i = 0; i < type.choiceCount(); i++) {
			player.sendSystemMessage(Component.translatable("enen.hayatemod.choice.line", i + 1,
					Component.translatable(type.choiceKey(i))).withStyle(ChatFormatting.YELLOW));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.3F);
		return InteractionResult.CONSUME;
	}

	/** The choice keybind arrived: resolves the open question, if any. */
	public void onChoiceKeyPressed(ServerPlayer player, int choice) {
		PendingChoice pending = this.pendingChoices.get(player.getUUID());
		if (pending == null) {
			return;
		}
		EnenEventEntity event = pending.event();
		EnenSession session = this.sessionOf(player);
		if (session == null || event.used() || choice < 0 || choice >= event.type().choiceCount()) {
			return;
		}
		this.pendingChoices.remove(player.getUUID());

		ServerLevel level = (ServerLevel) player.level();
		int roll = level.random.nextInt(100);
		boolean applied = switch (event.type()) {
			case VENDING -> applyVending(level, player, session, choice, roll);
			case PHONE -> applyPhone(level, player, session, choice, roll);
			case TRAIN -> applyTrain(level, player, session, choice);
			case MONSTER -> applyMonster(level, player, session, choice);
			default -> false;
		};
		if (!applied) {
			return; // e.g. the player could not afford the vending machine
		}
		event.markUsed();
		session.registerEventUsed();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	private boolean applyVending(ServerLevel level, ServerPlayer player, EnenSession session, int choice,
			int roll) {
		int[] costs = {1, 10, 100};
		int cost = costs[choice];
		if (player.getInventory().countItem(Items.EMERALD) < cost) {
			player.sendSystemMessage(msg("enen.hayatemod.event.vending.broke"));
			return false;
		}
		for (int i = 0; i < cost; i++) {
			player.getInventory().removeItem(new ItemStack(Items.EMERALD));
		}

		switch (choice) {
			case 0 -> {
				if (roll < 50) {
					give(player, new ItemStack(Items.BREAD, 2));
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.bread"));
				} else if (roll < 80) {
					session.adjustGoal(-200);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.closer"));
				} else {
					spawnYokaiNear(level, session, player, 1);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.battle"));
				}
			}
			case 1 -> {
				if (roll < 40) {
					give(player, new ItemStack(ModItems.GALE_FEATHER));
					session.adjustGoal(-500);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.feather"));
				} else if (roll < 70) {
					give(player, new ItemStack(Items.BREAD, 5));
					give(player, new ItemStack(ModItems.STORM_FRUIT));
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.food"));
				} else {
					spawnYokaiNear(level, session, player, 2);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.battle"));
				}
			}
			default -> {
				if (roll < 50) {
					give(player, new ItemStack(ModItems.GALE_ORB));
					session.adjustGoal(-1000);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.orb"));
				} else {
					spawnYokaiNear(level, session, player, 2);
					spawnYokaiNear(level, session, player, 2);
					player.sendSystemMessage(msg("enen.hayatemod.event.vending.battle"));
				}
			}
		}
		updateHud(level, session);
		return true;
	}

	private boolean applyPhone(ServerLevel level, ServerPlayer player, EnenSession session, int choice,
			int roll) {
		if (choice == 0) {
			if (roll < 50) {
				give(player, new ItemStack(ModItems.GALE_CHARM));
				player.sendSystemMessage(msg("enen.hayatemod.event.phone.charm"));
			} else {
				session.adjustGoal(400);
				player.sendSystemMessage(msg("enen.hayatemod.event.phone.farther"));
			}
		} else {
			session.adjustGoal(-150);
			player.sendSystemMessage(msg("enen.hayatemod.event.phone.closer"));
		}
		updateHud(level, session);
		return true;
	}

	private boolean applyTrain(ServerLevel level, ServerPlayer player, EnenSession session, int choice) {
		if (choice == 0) {
			// Boarding jumps 400 m forward, but the exit moves 800 m further away.
			int targetZ = Mth.min(Mth.floor(player.getZ()) + METERS_TO_BLOCKS(400),
					session.goalZ() - 5);
			player.teleport(new TeleportTransition(level,
					new Vec3(session.centerX() + 0.5, session.surfaceY() + 1, targetZ),
					Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
			session.adjustGoal(800);
			player.sendSystemMessage(msg("enen.hayatemod.event.train.boarded"));
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(),
					30, 0.8, 0.4, 0.8, 0.05);
		} else {
			player.sendSystemMessage(msg("enen.hayatemod.event.train.passed"));
		}
		updateHud(level, session);
		return true;
	}

	private boolean applyMonster(ServerLevel level, ServerPlayer player, EnenSession session, int choice) {
		switch (choice) {
			case 0 -> {
				spawnYokaiNear(level, session, player, 2);
				give(player, new ItemStack(ModItems.GALE_ORB));
				player.sendSystemMessage(msg("enen.hayatemod.event.monster.shovel"));
			}
			case 1 -> {
				spawnYokaiNear(level, session, player, 2);
				session.adjustGoal(1500);
				player.sendSystemMessage(msg("enen.hayatemod.event.monster.phone"));
			}
			default -> {
				session.adjustGoal(-1500);
				player.sendSystemMessage(msg("enen.hayatemod.event.monster.money"));
			}
		}
		updateHud(level, session);
		return true;
	}

	/** A surprise battle: a rank-scaled yokai right next to the player. */
	private void spawnYokaiNear(ServerLevel level, EnenSession session, ServerPlayer player, int rank) {
		int x = Mth.floor(player.getX()) + (level.random.nextInt(5) - 2);
		int z = Mth.floor(player.getZ()) + 1 + level.random.nextInt(3);
		spawnYokai(level, session, x, player.getY() + 0.5, z, rank);
	}

	// ------------------------------------------------------------------ death

	/** The death hook: yokai of active runs drop their rewards on the killer. */
	private void onLivingDeath(LivingEntity entity) {
		if (!(entity instanceof Spider || entity instanceof GaleSpiritEntity)) {
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		for (EnenSession session : this.sessions.values()) {
			EnenSession.YokaiKind kind = session.untrack(entity.getUUID());
			if (kind == null) {
				continue;
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(session.playerId());
			if (owner == null) {
				continue;
			}
			if (entity.killer != owner) {
				continue; // someone else got the kill: no reward for the runner
			}

			switch (kind) {
				case SPIRIT_0, SPIRIT_1, SPIRIT_2 -> {
					session.registerBattleWin();
					giveSpiritReward(level, owner, kind);
					owner.sendSystemMessage(msg("enen.hayatemod.battle.win",
							entity.getCustomName() == null ? Component.empty() : entity.getCustomName()));
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
							SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8F, 1.2F);
				}
				case JOROGUMO -> {
					session.registerBattleWin();
					give(owner, new ItemStack(ModItems.GALE_ORB));
					owner.sendSystemMessage(msg("enen.hayatemod.battle.jorogumo"));
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
							SoundEvents.ENTITY_PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
				}
				case BISHATATSU -> {
					// The kill itself is the gate: the clearing (which happens as
					// soon as the player is past the goal) pays the real reward.
					setBossDefeated(owner, true);
					owner.sendSystemMessage(msg("enen.hayatemod.battle.bishatatsu"));
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY(),
							entity.getZ(), 40, 1.0, 0.6, 1.0, 0.3);
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
							SoundEvents.ENTITY_PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.4F);
				}
			}
		}
	}

	private void giveSpiritReward(ServerLevel level, ServerPlayer player, EnenSession.YokaiKind kind) {
		int roll = level.random.nextInt(100);
		switch (kind) {
			case SPIRIT_0 -> give(player, new ItemStack(ModItems.GALE_DUST, 2));
			case SPIRIT_1 -> {
				give(player, new ItemStack(ModItems.GALE_DUST, 4));
				if (roll < 40) {
					give(player, new ItemStack(ModItems.STORM_FRUIT));
				}
			}
			case SPIRIT_2 -> {
				give(player, new ItemStack(ModItems.GALE_DUST, 6));
				give(player, new ItemStack(ModItems.GALE_FEATHER));
				if (roll < 50) {
					give(player, new ItemStack(ModItems.GALE_ORB));
				}
			}
			default -> {
			}
		}
	}

	// -------------------------------------------------------------------- hud

	private void setupHud(ServerLevel level, EnenSession session) {
		Scoreboard board = level.getServer().getScoreboard();
		Objective objective = board.getObjectiveFromName(OBJECTIVE);
		if (objective == null) {
			objective = board.addScore(OBJECTIVE, Criteria.DUMMY,
					Component.translatable("enen.hayatemod.hud"));
			board.setObjectiveSlot(objective, DisplaySlot.SIDEBAR);
		}
		updateHud(level, session);
	}

	private void updateHud(ServerLevel level, EnenSession session) {
		MinecraftServer server = level.getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
		if (player == null) {
			return;
		}
		Scoreboard board = server.getScoreboard();
		board.getPlayersScore().getOrCreate(player.getScoreboardName()).setScore(session.farthestMeters());

		// The goal rides along as a fake score row, so it sits just above the player
		// while they are still short of it.
		Score goal = board.getPlayersScore().getOrCreate(goalName(player));
		goal.setDisplayName(Component.translatable("enen.hayatemod.hud.goal", session.goalMeters()));
		goal.setScore(session.goalMeters() + 1);
	}

	private static String goalName(ServerPlayer player) {
		return "enen_goal_" + player.getUUID();
	}

	/** Drops the objective once the last run has ended. */
	private void refreshHud(MinecraftServer server) {
		if (!this.sessions.isEmpty()) {
			return;
		}
		Scoreboard board = server.getScoreboard();
		Objective objective = board.getObjectiveFromName(OBJECTIVE);
		if (objective != null) {
			board.removeObjective(objective);
		}
	}

	// ------------------------------------------------------------------ misc

	private void announceIntro(ServerPlayer player, EnenSession session) {
		player.sendSystemMessage(Component.translatable("enen.hayatemod.intro.title")
				.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(msg("enen.hayatemod.intro.1"));
		player.sendSystemMessage(msg("enen.hayatemod.intro.2"));
		player.sendSystemMessage(msg("enen.hayatemod.intro.3", session.goalMeters()));
	}

	private void announceMilestones(ServerPlayer player, EnenSession session) {
		int meters = session.farthestMeters();
		int goal = session.goalMeters();
		if (meters >= 1000 && session.announce("1000")) {
			player.sendSystemMessage(msg("enen.hayatemod.milestone.1000"));
		}
		if (meters >= goal / 2 && session.announce("half")) {
			player.sendSystemMessage(msg("enen.hayatemod.milestone.half"));
		}
		if (goal - meters <= 500 && session.announce("500")) {
			player.sendSystemMessage(msg("enen.hayatemod.milestone.500"));
		}
		if (goal - meters <= 200 && session.announce("200")) {
			player.sendSystemMessage(msg("enen.hayatemod.milestone.200"));
		}
	}

	/** The base goal of the (clears + 1)-st run: the game's classic numbers. */
	private static int baseGoal(int clears) {
		int[] bases = {1500, 2500, 3500, 4949, 7979, 9999, 12345, 15000, 18000, 20000, MAX_METERS};
		return Mth.min(bases[Mth.clamp(clears, 0, bases.length - 1)], MAX_METERS);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
	}

	private static Component msg(String key, Object... args) {
		return Component.translatable(key, args).withStyle(ChatFormatting.GOLD);
	}

	// ------------------------------------------------------------ persistence

	private static long lastDayOf(ServerPlayer player) {
		return player.getPersistentData().getLong("hayate_enen_last_day");
	}

	private static void setLastDay(ServerPlayer player, long day) {
		player.getPersistentData().putLong("hayate_enen_last_day", day);
	}

	private static int clearsOf(ServerPlayer player) {
		return player.getPersistentData().getInt("hayate_enen_cleares");
	}

	private static void setClears(ServerPlayer player, int clears) {
		player.getPersistentData().putInt("hayate_enen_cleares", clears);
	}

	private static boolean bossDefeatedOf(ServerPlayer player) {
		return player.getPersistentData().getBoolean("hayate_enen_boss_defeated");
	}

	private static void setBossDefeated(ServerPlayer player, boolean defeated) {
		player.getPersistentData().putBoolean("hayate_enen_boss_defeated", defeated);
	}

	/** A question waiting for the player's key press. */
	private record PendingChoice(ServerPlayer player, EnenEventEntity event, long expiresTick) {
	}
}
