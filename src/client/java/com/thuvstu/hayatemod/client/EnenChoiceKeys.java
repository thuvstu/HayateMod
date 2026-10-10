package com.thuvstu.hayatemod.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.thuvstu.hayatemod.azemichi.EnenChoiceC2SPayload;

/**
 * The azemichi choice keys: 1, 2 and 3 answer the open encounter question
 * (vending machine amounts, phone/train/monster answers).
 *
 * <p>The client sends a tiny C2S payload on every press; the server simply
 * ignores it when the player has no question open, so the keys are harmless
 * outside a run. Registered from {@link HayateModClient}.
 */
public final class EnenChoiceKeys {
	public static final KeyMapping CHOICE_A = new KeyMapping("key.hayatemod.choice_a",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1, "category.hayatemod.keys");
	public static final KeyMapping CHOICE_B = new KeyMapping("key.hayatemod.choice_b",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_2, "category.hayatemod.keys");
	public static final KeyMapping CHOICE_C = new KeyMapping("key.hayatemod.choice_c",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_3, "category.hayatemod.keys");

	private EnenChoiceKeys() {
	}

	public static void init() {
		KeyBindingHelper.registerKeyBinding(CHOICE_A);
		KeyBindingHelper.registerKeyBinding(CHOICE_B);
		KeyBindingHelper.registerKeyBinding(CHOICE_C);
		ClientTickEvents.END_CLIENT_TICK.register(EnenChoiceKeys::onTick);
	}

	private static void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (CHOICE_A.consumeClick()) {
			ClientPlayNetworking.send(new EnenChoiceC2SPayload(0));
		}
		if (CHOICE_B.consumeClick()) {
			ClientPlayNetworking.send(new EnenChoiceC2SPayload(1));
		}
		if (CHOICE_C.consumeClick()) {
			ClientPlayNetworking.send(new EnenChoiceC2SPayload(2));
		}
	}
}
