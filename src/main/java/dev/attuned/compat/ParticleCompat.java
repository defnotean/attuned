package dev.attuned.compat;

import net.minecraft.core.particles.DustParticleOptions;
import com.mojang.math.Vector3f;

public final class ParticleCompat {
	private ParticleCompat() {}

	public static DustParticleOptions dust(int rgb, float scale) {
		float red = ((rgb >> 16) & 0xFF) / 255.0F;
		float green = ((rgb >> 8) & 0xFF) / 255.0F;
		float blue = (rgb & 0xFF) / 255.0F;
		return new DustParticleOptions(new Vector3f(red, green, blue), scale);
	}
}
