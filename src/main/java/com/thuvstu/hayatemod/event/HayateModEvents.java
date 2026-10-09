package com.thuvstu.hayatemod.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.item.ModItems;

/**
 * A couple of Fabric API event hooks.
 *
 * <ul>
 *     <li>{@link LootTableEvents#MODIFY} sprinkles Gale Dust into coal ore drops.</li>
 *     <li>{@link ServerLifecycleEvents#SERVER_STARTED} just logs, as a template.</li>
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
	}
}
