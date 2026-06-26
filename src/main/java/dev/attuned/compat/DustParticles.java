package dev.attuned.compat;

import net.minecraft.core.particles.DustParticleOptions;

public final class DustParticles {
	private DustParticles() {}

	public static DustParticleOptions color(int rgb, float scale) {
		return new DustParticleOptions(rgb & 0x00FFFFFF, scale);
	}
}
