package com.thuvstu.hayatemod.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.thuvstu.hayatemod.town.TownManager;

/** {@code /solommo dungeon enter|exit|stress} wired into SolommoCommand. */
public final class DungeonCommand {
    private DungeonCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("dungeon")
                .then(Commands.literal("enter").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!com.thuvstu.hayatemod.town.TownManager.requireTown(player)) {
                        return 0;
                    }
                    EncounterRunner.enter(player);
                    return 1;
                })
                .then(Commands.argument("encounter",
                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (!com.thuvstu.hayatemod.town.TownManager.requireTown(player)) {
                                return 0;
                            }
                            EncounterRunner.enter(player,
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(
                                            ctx, "encounter"));
                            return 1;
                        })))
                .then(Commands.literal("exit").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!EncounterRunner.isActive()) {
                        player.sendSystemMessage(Component.literal("[dungeon] no active session"));
                        return 0;
                    }
                    EncounterRunner.exit(player, "manual");
                    return 1;
                }))
                .then(Commands.literal("stress")
                        .requires(com.thuvstu.hayatemod.rpg.SolommoCommand.op2())
                        .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    EncounterRunner.stress(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "rounds"));
                                    return 1;
                                })));
    }
}
