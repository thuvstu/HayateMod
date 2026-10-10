package com.thuvstu.hayatemod.azemichi;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the azemichi subcommands under {@code /hayate}:
 *
 * <pre>
 * /hayate enen            start a run (permission-free, once per day)
 * /hayate enen force      ignore the once-per-day limit (for testing)
 * /hayate enen exit       escape, like poking the scarecrow
 * /hayate enen status     the numbers of the current run
 * </pre>
 *
 * <p>The start command generates the whole corridor synchronously, which takes a
 * couple of seconds - acceptable for a minigame, and the player gets a "generating"
 * message first.
 */
public final class EnenCommand implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("hayate").then(
						Commands.literal("enen")
								.executes(context -> start(context.getSource(), false))
								.then(Commands.literal("force").executes(context ->
										start(context.getSource(), true)))
								.then(Commands.literal("exit").executes(context ->
										exit(context.getSource())))
								.then(Commands.literal("status").executes(context ->
										status(context.getSource()))))));
	}

	private static int start(CommandSourceStack source, boolean force) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) source.getLevel();

		boolean started = EnenAzemichi.INSTANCE.startRun(level, player, force);
		source.sendSuccess(() -> Component.translatable(started
				? "commands.hayatemod.enen.success" : "commands.hayatemod.enen.failed"), started);
		return started ? 1 : 0;
	}

	private static int exit(CommandSourceStack source) throws CommandSyntaxException {
		EnenAzemichi.INSTANCE.exitRun(source.getPlayerOrException(), false);
		return 1;
	}

	private static int status(CommandSourceStack source) throws CommandSyntaxException {
		EnenAzemichi.INSTANCE.reportStatus(source.getPlayerOrException());
		return 1;
	}
}
