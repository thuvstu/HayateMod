package com.thuvstu.hayatemod;

import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.build.PlayerBuilds;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.debug.CombatDebug;
import com.thuvstu.hayatemod.debug.DamageDebug;
import com.thuvstu.hayatemod.dungeon.EncounterRunner;
import com.thuvstu.hayatemod.dungeon.NpcParty;
import com.thuvstu.hayatemod.economy.MarketStore;
import com.thuvstu.hayatemod.life.LifeSkills;
import com.thuvstu.hayatemod.life.MiningHooks;
import com.thuvstu.hayatemod.net.NetRegistry;
import com.thuvstu.hayatemod.town.TownManager;
import com.thuvstu.hayatemod.town.TownTalk;
import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.block.ModBlocks;
import com.thuvstu.hayatemod.world.ModWorldgen;
import com.thuvstu.hayatemod.rpg.ArmorSets;
import com.thuvstu.hayatemod.rpg.DropHooks;
import com.thuvstu.hayatemod.rpg.McAdapter;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import com.thuvstu.hayatemod.rpg.SolommoCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.storage.LevelResource;

/** Main entrypoint: registration wiring only. Logic lives in the subpackages. */
public class Hayatemod implements ModInitializer {

    @Override
    public void onInitialize() {
        // Load content first: creative tabs are built during server construction,
        // after this entrypoint, so data-driven items must be known by then.
        ContentHolder.load();
        ModItems.register();
        ModBlocks.register();
        ModWorldgen.register();
        // Order matters: debug probe first, engine interception second, RPG pool last.
        // (ALLOW_DAMAGE stops at the first denial; RpgHealth always consumes.)
        DamageDebug.register();
        McAdapter.register();
        ArmorSets.register();
        RpgHealth.register();
        NetRegistry.register();
        DropHooks.register();
        SolommoCommand.register();
        // Developer tooling (damage funnel probe, training dummy, skill-cast receiver).
        CombatDebug.register();
        // Phase 4: session dungeon (instance arena, NPC party, boss encounter).
        NpcParty.register();
        EncounterRunner.register();
        // Phase 6: market day ticker + mining life skill.
        MarketStore.registerTick();
        MiningHooks.register();
        TownManager.register();
        TownTalk.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.thuvstu.hayatemod.rpg.DifficultyState.load(server.getWorldPath(LevelResource.ROOT));
            if (ContentHolder.ready()) {
                McAdapter.init(server);
            }
            CodexStore.load(server.getWorldPath(LevelResource.ROOT));
            PlayerBuilds.load(server.getWorldPath(LevelResource.ROOT));
            com.thuvstu.hayatemod.forge.ForgedStore.load(server.getWorldPath(LevelResource.ROOT));
            MarketStore.load(server.getWorldPath(LevelResource.ROOT));
            LifeSkills.load(server.getWorldPath(LevelResource.ROOT));
        });
    }
}
