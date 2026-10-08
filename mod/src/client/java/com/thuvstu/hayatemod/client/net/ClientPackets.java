package com.thuvstu.hayatemod.client.net;

import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.client.hud.HudState;
import com.thuvstu.hayatemod.client.screen.CodexScreen;
import com.thuvstu.hayatemod.client.screen.CraftScreen;
import com.thuvstu.hayatemod.client.screen.LoadoutScreen;
import com.thuvstu.hayatemod.client.screen.MarketScreen;
import com.thuvstu.hayatemod.client.screen.TavernScreen;
import com.thuvstu.hayatemod.net.UiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client receivers: S2C screen payloads open the matching Screen; state feeds the HUD. */
public final class ClientPackets {
    private ClientPackets() {
    }

    public static void register() {
        // Type registration lives in NetRegistry (main init runs on both physical
        // sides, so registering here too would throw "already registered").
        // This side only attaches receivers.
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.MarketOpen.TYPE, (payload, ctx) -> {
            var rows = new ArrayList<>(payload.rows());
            ctx.client().execute(() -> Minecraft.getInstance().setScreen(new MarketScreen(rows)));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.SkillState.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> HudState.skill(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.PartyState.TYPE, (payload, ctx) -> {
            var members = new ArrayList<>(payload.members());
            float frac = payload.playerFrac();
            ctx.client().execute(() -> HudState.party(members, frac));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.TavernOpen.TYPE, (payload, ctx) -> {
            var npcs = new ArrayList<>(payload.npcs());
            var quests = new ArrayList<>(payload.quests());
            ctx.client().execute(
                    () -> Minecraft.getInstance().setScreen(new TavernScreen(npcs, payload.fee(),
                            payload.inSession(), quests)));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.CodexOpen.TYPE, (payload, ctx) -> {
            var entries = new ArrayList<>(payload.entries());
            ctx.client().execute(() -> Minecraft.getInstance().setScreen(new CodexScreen(entries)));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.CraftOpen.TYPE, (payload, ctx) -> {
            var rows = new ArrayList<>(payload.rows());
            ctx.client().execute(
                    () -> Minecraft.getInstance().setScreen(new CraftScreen(rows, payload.heldName())));
        });
        ClientPlayNetworking.registerGlobalReceiver(UiPayloads.LoadoutOpen.TYPE, (payload, ctx) -> {
            var keys = new ArrayList<>(payload.keystones());
            var loadouts = new ArrayList<>(payload.loadouts());
            ctx.client().execute(() -> Minecraft.getInstance().setScreen(
                    new LoadoutScreen(keys, loadouts, payload.buildSig())));
        });
    }
}
