package com.thuvstu.hayatemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.thuvstu.hayatemod.HayateMod;
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
	/** 26.x key categories are records; custom ones go through {@code register}. */
	private static final KeyMapping.Category HAYATE_CATEGORY =
			KeyMapping.Category.register(HayateMod.id("keys"));

	public static final KeyMapping CHOICE_A = new KeyMapping("key.hayatemod.choice_a",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_1, HAYATE_CATEGORY);
	public static final KeyMapping CHOICE_B = new KeyMapping("key.hayatemod.choice_b",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_2, HAYATE_CATEGORY);
	public static final KeyMapping CHOICE_C = new KeyMapping("key.hayatemod.choice_c",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_3, HAYATE_CATEGORY);

	private EnenChoiceKeys() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(CHOICE_A);
		KeyMappingHelper.registerKeyMapping(CHOICE_B);
		KeyMappingHelper.registerKeyMapping(CHOICE_C);
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
