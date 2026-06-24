package net.fabricmc.fabric.api.event.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;

public final class DynamicRegistries {
	private DynamicRegistries() {}

	public static <T> void registerSynced(ResourceKey<Registry<T>> key, Codec<T> codec) {
		FMLJavaModLoadingContext.get().getModEventBus().addListener((DataPackRegistryEvent.NewRegistry event) ->
			event.dataPackRegistry(key, codec, codec));
	}
}
