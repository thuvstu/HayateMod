package com.thuvstu.hayatemod.client;

import com.thuvstu.hayatemod.client.hud.HudState;
import com.thuvstu.hayatemod.client.hud.PartyHud;
import com.thuvstu.hayatemod.client.hud.SkillHud;
import com.thuvstu.hayatemod.client.net.ClientPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class HayatemodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Skill key input (G) -> C2S cast request.
        SkillKeyHandler.register();
        // Right-click with a weapon -> same C2S cast request.
        WeaponUseHandler.register();
        ClientPackets.register();
        SkillHud.register();
        PartyHud.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                HudState.clear();
            } else {
                HudState.tickSkill();
            }
        });
    }
}
