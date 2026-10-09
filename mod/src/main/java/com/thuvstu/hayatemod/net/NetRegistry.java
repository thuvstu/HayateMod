package com.thuvstu.hayatemod.net;

import com.thuvstu.hayatemod.dungeon.EncounterRunner;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.rpg.RpgSkills;
import com.thuvstu.hayatemod.town.TownManager;
import com.thuvstu.hayatemod.net.UiPayloads.CraftMake;
import com.thuvstu.hayatemod.net.UiPayloads.KeystoneToggle;
import com.thuvstu.hayatemod.net.UiPayloads.LoadoutApply;
import com.thuvstu.hayatemod.net.UiPayloads.LoadoutSave;
import com.thuvstu.hayatemod.net.UiPayloads.MarketBuy;
import com.thuvstu.hayatemod.net.UiPayloads.MarketSell;
import com.thuvstu.hayatemod.net.UiPayloads.SalvageHeld;
import com.thuvstu.hayatemod.net.UiPayloads.TavernEnter;
import com.thuvstu.hayatemod.rpg.SolommoCommand;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers all C2S/S2C types (both ends) and server-side action receivers. */
public final class NetRegistry {
    private NetRegistry() {
    }

    public static void register() {
        var c2s = PayloadTypeRegistry.playC2S();
        c2s.register(SkillCastPayload.TYPE, SkillCastPayload.CODEC);
        c2s.register(MarketBuy.TYPE, MarketBuy.CODEC);
        c2s.register(MarketSell.TYPE, MarketSell.CODEC);
        c2s.register(CraftMake.TYPE, CraftMake.CODEC);
        c2s.register(SalvageHeld.TYPE, SalvageHeld.CODEC);
        c2s.register(LoadoutSave.TYPE, LoadoutSave.CODEC);
        c2s.register(LoadoutApply.TYPE, LoadoutApply.CODEC);
        c2s.register(KeystoneToggle.TYPE, KeystoneToggle.CODEC);
        c2s.register(TavernEnter.TYPE, TavernEnter.CODEC);

        var s2c = PayloadTypeRegistry.playS2C();
        s2c.register(UiPayloads.MarketOpen.TYPE, UiPayloads.MarketOpen.CODEC);
        s2c.register(UiPayloads.SkillState.TYPE, UiPayloads.SkillState.CODEC);
        s2c.register(UiPayloads.CombatState.TYPE, UiPayloads.CombatState.CODEC);
        s2c.register(UiPayloads.CastingState.TYPE, UiPayloads.CastingState.CODEC);
        s2c.register(UiPayloads.PartyState.TYPE, UiPayloads.PartyState.CODEC);
        s2c.register(UiPayloads.TavernOpen.TYPE, UiPayloads.TavernOpen.CODEC);
        s2c.register(UiPayloads.CodexOpen.TYPE, UiPayloads.CodexOpen.CODEC);
        s2c.register(UiPayloads.CraftOpen.TYPE, UiPayloads.CraftOpen.CODEC);
        s2c.register(UiPayloads.LoadoutOpen.TYPE, UiPayloads.LoadoutOpen.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SkillCastPayload.TYPE,
                (payload, ctx) -> RpgSkills.castHeldSlot(ctx.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(MarketBuy.TYPE, (payload, ctx) -> {
            SolommoCommand.marketBuy(ctx.player(), payload.item(), payload.count());
            UiServer.openMarket(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(MarketSell.TYPE, (payload, ctx) -> {
            SolommoCommand.marketSell(ctx.player(), payload.item(), payload.count());
            UiServer.openMarket(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(CraftMake.TYPE, (payload, ctx) -> {
            SolommoCommand.craft(ctx.player(), payload.weapon());
            UiServer.openCraft(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(SalvageHeld.TYPE, (payload, ctx) -> {
            SolommoCommand.salvage(ctx.player());
            UiServer.openCraft(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(LoadoutSave.TYPE, (payload, ctx) -> {
            SolommoCommand.saveLoadout(ctx.player(), payload.name());
            UiServer.openLoadout(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(LoadoutApply.TYPE, (payload, ctx) -> {
            SolommoCommand.loadout(ctx.player(), payload.name());
            UiServer.openLoadout(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(KeystoneToggle.TYPE, (payload, ctx) -> {
            SolommoCommand.toggleKeystone(ctx.player(), payload.id());
            UiServer.openLoadout(ctx.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(TavernEnter.TYPE, (payload, ctx) -> {
            if (ContentHolder.ready() && TownManager.requireTown(ctx.player())) {
                String id = payload.encounter();
                if (id == null || id.isEmpty()
                        || !ContentHolder.get().encounters().containsKey(id)) {
                    return; // Unknown requests never silently enter another encounter.
                }
                EncounterRunner.enter(ctx.player(), id);
            }
        });
    }
}
