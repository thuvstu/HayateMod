package com.thuvstu.hayatemod.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

/**
 * 26.x removed {@code Level#sendParticles(type, x, y, z, count, dx, dy, dz,
 * speed)}; this is the replacement: {@code count} particles at {@code x,y,z},
 * each with a randomised velocity inside the given spread, server side.
 */
public final class Particles {
	private Particles() {
	}

	public static void burst(ServerLevel level, ParticleOptions type, double x, double y, double z,
			int count, double spreadX, double spreadY, double spreadZ) {
		var random = level.getRandom();
		for (int i = 0; i < count; i++) {
			level.addParticle(type, x, y, z,
					(random.nextDouble() * 2.0D - 1.0D) * spreadX,
					(random.nextDouble() * 2.0D - 1.0D) * spreadY,
					(random.nextDouble() * 2.0D - 1.0D) * spreadZ);
		}
	}
}
