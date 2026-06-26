package dev.attuned.compat;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

public final class DustParticles {
	private DustParticles() {}

	public static DustParticleOptions color(int rgb, float scale) {
		float r = ((rgb >> 16) & 0xFF) / 255.0F;
		float g = ((rgb >> 8) & 0xFF) / 255.0F;
		float b = (rgb & 0xFF) / 255.0F;
		return new DustParticleOptions(new Vector3f(r, g, b), scale);
	}
}
