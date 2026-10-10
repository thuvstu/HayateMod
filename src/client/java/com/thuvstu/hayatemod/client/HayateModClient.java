package com.thuvstu.hayatemod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.entity.ModEntities;

public class HayateModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as
		// rendering, key bindings or screens.
		EntityRendererRegistry.register(ModEntities.GALE_SPIRIT, GaleSpiritRenderer::new);
		EntityRendererRegistry.register(ModEntities.AZEMICHI_EVENT, EnenEventRenderer::new);

		// The azemichi choice keys (1/2/3 answer encounter questions).
		EnenChoiceKeys.init();

		HayateMod.LOGGER.info("HayateMod: client side ready");
	}
}
