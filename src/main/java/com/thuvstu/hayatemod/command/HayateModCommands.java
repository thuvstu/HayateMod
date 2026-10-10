package com.thuvstu.hayatemod.command;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

import com.thuvstu.hayatemod.HayateMod;

/**
 * Registers {@code /hayate} and {@code /loctp}:
 *
 * <pre>
 * /hayate about
 * /hayate boost [targets] [seconds] [amplifier]   (permission level 2)
 * /loctp &lt;structure|#tag&gt;                    (permission level 2)
 * </pre>
 *
 * <p>Each node is built bottom-up into its own local variable - brigadier trees get
 * unreadable fast when they are written as one nested expression.
 *
 * <p>{@code /loctp} mirrors vanilla {@code /locate structure} (same argument type,
 * same search) and then teleports the executor to the hit, so it doubles as a
 * worldgen smoke test: if a structure never resolves, its data is broken.
 */
public class HayateModCommands implements ModInitializer {
	private static final int DEFAULT_DURATION_SECONDS = 30;
	private static final int DEFAULT_AMPLIFIER = 1;

	/** Vanilla {@code /locate} only searches 100 blocks out; too tight for sparse biomes. */
	private static final int LOCTP_SEARCH_RADIUS = 10000;

	private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID =
			new DynamicCommandExceptionType(
					name -> Component.translatable("commands.hayatemod.loctp.invalid", name));
	private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND =
			new DynamicCommandExceptionType(
					name -> Component.translatable("commands.hayatemod.loctp.not_found", name));

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

			// /loctp <structure|#tag>
			var structure = Commands
					.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
					.executes(context -> executeLoctp(context,
							ResourceOrTagKeyArgument.getResourceOrTagKey(context, "structure",
									Registries.STRUCTURE, ERROR_STRUCTURE_INVALID)));

			dispatcher.register(Commands.literal("loctp")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(structure));
		});
	}

	private static int executeAbout(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(
				() -> Component.translatable("commands.hayatemod.about", modVersion(), minecraftVersion()),
				false);

		return 1;
	}

	/** The mod version from {@code fabric.mod.json}, so it cannot drift from the build. */
	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(HayateMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	/** The running game version, as known to the loader. */
	private static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
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

	private static int executeLoctp(CommandContext<CommandSourceStack> context,
			ResourceOrTagKeyArgument.Result<Structure> result) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Registry<Structure> registry = source.registryAccess().lookupOrThrow(Registries.STRUCTURE);

		// Same unwrapping vanilla /locate does: a direct id becomes a one-entry
		// set, a #tag resolves through the tag loader.
		Optional<? extends HolderSet.ListBacked<Structure>> holders = result.unwrap().map(
				key -> registry.get(key).map(HolderSet::direct),
				registry::get);
		HolderSet<Structure> holderSet = holders
				.orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(result.asPrintable()));

		BlockPos origin = BlockPos.containing(source.getPosition());
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, holderSet, origin, LOCTP_SEARCH_RADIUS, false);
		if (found == null) {
			throw ERROR_STRUCTURE_NOT_FOUND.create(result.asPrintable());
		}

		ServerPlayer player = source.getPlayerOrException();
		BlockPos landing = findLanding(level, found.getFirst());
		player.teleport(new TeleportTransition(level,
				new Vec3(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5),
				Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));

		source.sendSuccess(() -> Component.translatable("commands.hayatemod.loctp.success",
				result.asPrintable(), landing.getX(), landing.getY(), landing.getZ()), true);

		return 1;
	}

	/**
	 * Structure starts can sit inside solid rock (our nether ruin does), so climb
	 * until there is room to stand. Falls back to just above the hit.
	 */
	private static BlockPos findLanding(ServerLevel level, BlockPos found) {
		BlockPos.MutableBlockPos cursor = found.mutable();
		for (int i = 0; i < 16; i++) {
			if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
				return cursor.immutable();
			}
			cursor.move(Direction.UP);
		}
		return found.above();
	}
}
