package net.fabricmc.fabric.api.event.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class DynamicRegistries {
	private static IEventBus modEventBus;

	private DynamicRegistries() {}

	public static void setModEventBus(IEventBus eventBus) {
		modEventBus = eventBus;
	}

	public static <T> void registerSynced(ResourceKey<Registry<T>> key, Codec<T> codec) {
		if (modEventBus == null) {
			throw new IllegalStateException("NeoForge mod event bus must be set before registering dynamic registries");
		}
		modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) ->
			event.dataPackRegistry(key, codec, codec));
	}
}
