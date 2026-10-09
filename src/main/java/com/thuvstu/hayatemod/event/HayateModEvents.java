package com.thuvstu.hayatemod.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.item.ModItems;

/**
 * The Fabric API event hooks of the mod.
 *
 * <ul>
 *     <li>{@link LootTableEvents#MODIFY} sprinkles Gale Dust into coal ore drops.</li>
 *     <li>{@link ServerLifecycleEvents#SERVER_STARTED} just logs, as a template.</li>
 *     <li>{@link ServerTickEvents#END_SERVER_TICK} grants the gale set bonus.</li>
 * </ul>
 */
public class HayateModEvents implements ModInitializer {
	private static final ResourceKey<LootTable> COAL_ORE_LOOT_TABLE = Blocks.COAL_ORE.getLootTable().orElseThrow();

	@Override
	public void onInitialize() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			// Only modify built-in loot tables and leave data pack loot tables untouched.
			if (source.isBuiltin() && COAL_ORE_LOOT_TABLE.equals(key)) {
				LootPool.Builder poolBuilder = LootPool.lootPool()
						.add(LootItem.lootTableItem(ModItems.GALE_DUST));

				tableBuilder.withPool(poolBuilder);
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				HayateMod.LOGGER.info("HayateMod: server started - gale dust awaits in the coal"));

		ServerTickEvents.END_SERVER_TICK.register(HayateModEvents::rewardFullGaleSet);
	}

	/** Every two seconds, refresh the set bonus of everyone wearing the full gale armour. */
	private static void rewardFullGaleSet(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isWearingGaleSet(player)) {
				continue;
			}

			// A short, ambient Speed effect: it refreshes while the set is worn and lapses
			// two seconds after the last piece comes off.
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false));

			if (player.level() instanceof ServerLevel level && player.getRandom().nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
						2, 0.35, 0.1, 0.35, 0.02);
			}
		}
	}

	private static boolean isWearingGaleSet(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GALE_HELMET)
				&& entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.GALE_CHESTPLATE)
				&& entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.GALE_LEGGINGS)
				&& entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.GALE_BOOTS);
	}
}
