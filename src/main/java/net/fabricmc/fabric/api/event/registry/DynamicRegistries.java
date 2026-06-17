package net.fabricmc.fabric.api.event.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** Minimal 1.19-style entrypoint backed by Fabric's legacy synced registry builder. */
public final class DynamicRegistries {
	private DynamicRegistries() {}

	public static <T> void registerSynced(ResourceKey<Registry<T>> key, Codec<T> codec) {
		FabricRegistryBuilder.createSimple(Object.class, key.location())
			.attribute(RegistryAttribute.SYNCED)
			.buildAndRegister();
	}
}
