package com.thuvstu.hayatemod.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.thuvstu.hayatemod.dungeon.SignalDebug;
import com.thuvstu.hayatemod.rpg.McAdapter;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thuvstu.hayatemod.rpg.RpgSkills;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developer combat tooling promoted from the S4 spike (G0).
 *
 * <p>Owns the client skill-cast receiver (G key -> C2S -> engine) and the
 * {@code /s4 dummy} training target (NoAI zombie with boss bar and a periodic
 * particle-telegraphed AoE slam).
 */
public final class CombatDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/debug");

    private static final float SLAM_RADIUS = 6.0F;
    private static final float SLAM_DAMAGE = 6.0F;
    private static final long SLAM_WINDUP_TICKS = 40L; // 2s telegraph
    private static final long SLAM_PERIOD_TICKS = 300L; // 15s cycle

    private static final Map<UUID, TrackedDummy> DUMMIES = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private CombatDebug() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CombatDebug::onServerTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("s4")
                        .requires(src -> src.permissions().hasPermission(
                                new net.minecraft.server.permissions.Permission.HasCommandLevel(
                                        net.minecraft.server.permissions.PermissionLevel.byId(2))))
                        .then(Commands.literal("dummy").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            spawnDummy(ctx.getSource().getLevel(), player);
                            return 1;
                        }))
                        .then(Commands.literal("signal")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            SignalDebug.send(name);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("[debug] signal " + name),
                                                    false);
                                            return 1;
                                        })))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("enemy", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player =
                                                    ctx.getSource().getPlayerOrException();
                                            String id = StringArgumentType.getString(ctx, "enemy");
                                            try {
                                                java.util.UUID mob =
                                                        McAdapter.spawnDebugEnemy(player, id);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("[debug] spawned " + id + " " + mob),
                                                        false);
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("[debug] " + e.getMessage()));
                                            }
                                            return 1;
                                        })))));
    }

    // ---- skill casts now arrive via NetRegistry (SkillCastPayload -> RpgSkills) ----

    // ---- dummy enemy ----

    private static void spawnDummy(ServerLevel level, ServerPlayer player) {
        Vec3 spot = player.position().add(player.getLookAngle().scale(3.0));
        BlockPos pos = BlockPos.containing(spot);
        Zombie dummy = EntityType.ZOMBIE.spawn(level, zombie -> {
            zombie.setNoAi(true);
            Objects.requireNonNull(zombie.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(200.0);
            zombie.setHealth(200.0F);
            zombie.setCustomName(Component.literal("訓練用ダミー"));
            zombie.setCustomNameVisible(true);
            zombie.setPersistenceRequired();
            // 日光で燃えて消えないよう耐火を付与（訓練用。ダメージ検証には影響しない）
            zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 999999, 0, false, false));
        }, pos, EntitySpawnReason.COMMAND, false, false);
        if (dummy == null) {
            player.sendSystemMessage(Component.literal("[debug] 召喚に失敗（場所を変えて再試行）"));
            return;
        }
        DUMMIES.put(dummy.getUUID(), new TrackedDummy(level, new SlamState(SLAM_PERIOD_TICKS)));
        ServerBossEvent bar = new ServerBossEvent(
                Component.literal("訓練用ダミー"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        BARS.put(dummy.getUUID(), bar);
        player.sendSystemMessage(Component.literal("[debug] 訓練用ダミー召喚 (HP200)。Gキーで火弾、ダミーは15秒毎に範囲攻撃。"));
        LOGGER.info("[debug][DUMMY] spawned {} at {}", dummy.getUUID(), pos);
    }

    // ---- slam scheduler ----

    private static void onServerTick(net.minecraft.server.MinecraftServer server) {
        for (UUID id : new ArrayList<>(DUMMIES.keySet())) {
            TrackedDummy tracked = DUMMIES.get(id);
            if (tracked == null) {
                continue;
            }
            ServerLevel level = tracked.level;
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Zombie dummy) || !dummy.isAlive()) {
                untrack(id);
                continue;
            }
            SlamState state = tracked.state;
            ServerPlayer target = nearestPlayer(level, dummy, 16.0);
            if (target == null) {
                continue;
            }
            ServerBossEvent bar = BARS.get(id);
            if (bar != null) {
                bar.setProgress(dummy.getHealth() / dummy.getMaxHealth());
            }
            state.cooldown--;
            if (state.cooldown == SLAM_WINDUP_TICKS) {
                target.sendSystemMessage(Component.literal("[debug] ダミーの範囲攻撃が来る！離れろ！"));
                LOGGER.info("[debug][TELEGRAPH] slam windup start {}", id);
            }
            if (state.cooldown <= SLAM_WINDUP_TICKS && state.cooldown > 0) {
                if (state.cooldown % 2 == 0) {
                    ringParticles(level, dummy.position(), SLAM_RADIUS);
                }
            }
            if (state.cooldown <= 0) {
                slam(level, dummy);
                state.cooldown = SLAM_PERIOD_TICKS;
            }
        }
    }

    private static void slam(ServerLevel level, Zombie dummy) {
        Vec3 center = dummy.position();
        for (int i = 0; i < 40; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.LAVA,
                    center.x, center.y + 0.3, center.z, 1,
                    Math.cos(a) * 2.0, 1.5, Math.sin(a) * 2.0, 0.2);
        }
        int hits = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(dummy) <= SLAM_RADIUS && !player.isCreative() && !player.isSpectator()) {
                player.hurtServer(level, level.damageSources().mobAttack(dummy), SLAM_DAMAGE);
                hits++;
            }
        }
        LOGGER.info("[debug][SLAM] {} hits={}", dummy.getUUID(), hits);
    }

    private static void ringParticles(ServerLevel level, Vec3 center, float radius) {
        double y = center.y + 0.15;
        for (int i = 0; i < 24; i++) {
            double a = (Math.PI * 2.0 * i) / 24.0;
            level.sendParticles(ParticleTypes.FLAME,
                    center.x + Math.cos(a) * radius, y, center.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, LivingEntity entity, double range) {
        ServerPlayer best = null;
        double bestDist = range;
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            double d = player.distanceTo(entity);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private static void untrack(UUID id) {
        DUMMIES.remove(id);
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        LOGGER.info("[debug][DUMMY] untracked {}", id);
    }

    private static final class TrackedDummy {
        final ServerLevel level;
        final SlamState state;

        TrackedDummy(ServerLevel level, SlamState state) {
            this.level = level;
            this.state = state;
        }
    }

    private static final class SlamState {
        long cooldown;

        SlamState(long cooldown) {
            this.cooldown = cooldown;
        }
    }
}
