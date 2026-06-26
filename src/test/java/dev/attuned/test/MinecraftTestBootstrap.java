package dev.attuned.test;

public final class MinecraftTestBootstrap {
	private MinecraftTestBootstrap() {}

	public static void ensureBootstrapped() {
		// NeoForge 21.11 routes SharedConstants initialization through FML loader
		// state, which plain unit tests intentionally do not create. The tests that
		// call this helper use only empty ItemStack values and source contracts.
	}
}
