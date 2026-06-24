package net.fabricmc.fabric.api.event.registry;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;

public final class DynamicRegistries {
	private DynamicRegistries() {}

	public static <T> void registerSynced(ResourceKey<?> key, Codec<T> codec) {
	}
}
