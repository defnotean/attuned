package dev.attuned.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftTestBootstrap {
	private MinecraftTestBootstrap() {}

	public static void ensureBootstrapped() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
}
