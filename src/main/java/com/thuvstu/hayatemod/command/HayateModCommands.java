package com.thuvstu.hayatemod.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers {@code /hayate}:
 *
 * <pre>
 * /hayate about
 * /hayate boost [targets] [seconds] [amplifier]   (permission level 2)
 * </pre>
 *
 * <p>Each node is built bottom-up into its own local variable - brigadier trees get
 * unreadable fast when they are written as one nested expression.
 */
public class HayateModCommands implements ModInitializer {
	private static final int DEFAULT_DURATION_SECONDS = 30;
	private static final int DEFAULT_AMPLIFIER = 1;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// /hayate boost <targets> <seconds> <amplifier>
			var amplifier = Commands.argument("amplifier", IntegerArgumentType.integer(0, 4))
					.executes(context -> executeBoost(context,
							EntityArgument.getPlayers(context, "targets"),
							IntegerArgumentType.getInteger(context, "seconds"),
							IntegerArgumentType.getInteger(context, "amplifier")));

			// /hayate boost <targets> <seconds>
			var seconds = Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
					.executes(context -> executeBoost(context,
							EntityArgument.getPlayers(context, "targets"),
							IntegerArgumentType.getInteger(context, "seconds"),
							DEFAULT_AMPLIFIER))
					.then(amplifier);

			// /hayate boost <targets>
			var targets = Commands.argument("targets", EntityArgument.players())
					.executes(context -> executeBoost(context,
							EntityArgument.getPlayers(context, "targets"),
							DEFAULT_DURATION_SECONDS, DEFAULT_AMPLIFIER))
					.then(seconds);

			// /hayate boost  (self)
			var boost = Commands.literal("boost")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.executes(context -> executeBoost(context,
							List.of(context.getSource().getPlayerOrException()),
							DEFAULT_DURATION_SECONDS, DEFAULT_AMPLIFIER))
					.then(targets);

			dispatcher.register(Commands.literal("hayate")
					.then(Commands.literal("about").executes(HayateModCommands::executeAbout))
					.then(boost));
		});
	}

	private static int executeAbout(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(
				() -> Component.translatable("commands.hayatemod.about", "1.0.0", "26.3"), false);

		return 1;
	}

	private static int executeBoost(CommandContext<CommandSourceStack> context,
			Collection<ServerPlayer> targets, int seconds, int amplifier) throws CommandSyntaxException {
		for (ServerPlayer player : targets) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, seconds * 20, amplifier));
		}

		context.getSource().sendSuccess(() -> Component.translatable("commands.hayatemod.boost.success",
				targets.size(), seconds, amplifier + 1), true);

		return targets.size();
	}
}
