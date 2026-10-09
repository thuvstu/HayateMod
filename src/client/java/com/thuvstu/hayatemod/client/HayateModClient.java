package com.thuvstu.hayatemod.client;

import net.fabricmc.api.ClientModInitializer;

import com.thuvstu.hayatemod.HayateMod;

public class HayateModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as
		// rendering, key bindings or screens.
		HayateMod.LOGGER.info("HayateMod: client side ready");
	}
}
