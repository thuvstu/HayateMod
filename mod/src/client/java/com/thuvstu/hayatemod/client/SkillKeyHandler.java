package com.thuvstu.hayatemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thuvstu.hayatemod.net.SkillCastPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * G key sends a skill-cast request (C2S). The server validates and executes.
 */
public final class SkillKeyHandler {
    private static KeyMapping fireboltKey;

    private SkillKeyHandler() {
    }

    public static void register() {
        fireboltKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hayatemod.firebolt",
                InputConstants.KEY_G,
                KeyMapping.Category.GAMEPLAY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (fireboltKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(SkillCastPayload.TYPE)) {
                    ClientPlayNetworking.send(new SkillCastPayload("special"));
                }
            }
        });
    }
}
