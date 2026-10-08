package com.thuvstu.hayatemod.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developer damage-path tooling promoted from the S1 spike (ADR-07).
 *
 * <p>Observes every damage entry via {@code ALLOW_DAMAGE} and provides
 * {@code /s1} commands: log toggle, shield (cancel damage), wipetest
 * (cancel death + restore), hurt probe, status. Logging is off by default
 * and enabled per-session with {@code /s1 log on}.
 */
public final class DamageDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/debug");

    /** When true, all damage to players is cancelled. */
    private static volatile boolean shield = false;
    /** When true, player death is cancelled and health restored (checkpoint prototype). */
    private static volatile boolean wipeTest = false;

    private DamageDebug() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DamageDebug::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(DamageDebug::onAfterDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(DamageDebug::onAllowDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(DamageDebug::onAfterDeath);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("s1").requires(src -> src.permissions()
                        .hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2))))
                        .then(Commands.literal("log").then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> {
                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    DebugFlags.damageLog = mode.equalsIgnoreCase("on");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[debug] damageLog=" + DebugFlags.damageLog), false);
                                    return 1;
                                })))
                        .then(Commands.literal("shield").then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> {
                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    shield = mode.equalsIgnoreCase("on");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[debug] shield=" + shield), false);
                                    return 1;
                                })))
                        .then(Commands.literal("wipetest").then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> {
                                    String mode = StringArgumentType.getString(ctx, "mode");
                                    wipeTest = mode.equalsIgnoreCase("on");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[debug] wipetest=" + wipeTest), false);
                                    return 1;
                                })))
                        .then(Commands.literal("hurt").then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 1000f))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    float amount = FloatArgumentType.getFloat(ctx, "amount");
                                    boolean applied = player.hurtServer(player.level(),
                                            player.damageSources().generic(), amount);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[debug] hurt amount=" + amount
                                                    + " applied=" + applied + " hp=" + player.getHealth()),
                                            false);
                                    return 1;
                                })))
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ctx.getSource().sendSuccess(() -> Component.literal("[debug] hp=" + player.getHealth()
                                    + "/" + player.getMaxHealth()
                                    + " food=" + player.getFoodData().getFoodLevel()
                                    + " log=" + DebugFlags.damageLog
                                    + " shield=" + shield + " wipetest=" + wipeTest), false);
                            return 1;
                        }))));
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide()) {
            return true;
        }
        if (DebugFlags.damageLog) {
            LOGGER.info("[DMG][ALLOW] entity={} src={} amount={} tick={} hp-before={}",
                    describe(entity), source.getMsgId(), amount, entity.level().getGameTime(),
                    entity.getHealth());
        }
        if (shield && entity instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(
                    "[debug] shield blocked " + amount + " (" + source.getMsgId() + ")"));
            return false;
        }
        return true;
    }

    private static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken,
            float damageTaken, boolean blocked) {
        if (!DebugFlags.damageLog || entity.level().isClientSide()) {
            return;
        }
        LOGGER.info("[DMG][AFTER] entity={} src={} base={} taken={} blocked={} hp-after={}",
                describe(entity), source.getMsgId(), baseDamageTaken, damageTaken, blocked,
                entity.getHealth());
    }

    private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide()) {
            return true;
        }
        if (DebugFlags.damageLog) {
            LOGGER.info("[DMG][DEATH-ALLOW] entity={} src={} amount={}", describe(entity),
                    source.getMsgId(), amount);
        }
        if (wipeTest && entity instanceof ServerPlayer player) {
            player.setHealth(player.getMaxHealth());
            RpgHealth.restore(player);
            player.sendSystemMessage(Component.literal(
                    "[debug] wipetest: death cancelled, HP restored (checkpoint would go here)"));
            LOGGER.info("[DMG][WIPE] death cancelled for {}, HP restored to {}",
                    player.getScoreboardName(), player.getHealth());
            return false;
        }
        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!DebugFlags.damageLog || entity.level().isClientSide()) {
            return;
        }
        LOGGER.info("[DMG][DEATH] entity={} src={}", describe(entity), source.getMsgId());
    }

    private static String describe(LivingEntity entity) {
        return entity.getScoreboardName() + "<" + entity.getType().getDescriptionId() + ">";
    }
}
