package net.fabricmc.fabric.api.resource;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ResourceManagerHelper {
	private final PackType packType;

	private ResourceManagerHelper(PackType packType) {
		this.packType = packType;
	}

	public static ResourceManagerHelper get(PackType packType) {
		return new ResourceManagerHelper(packType);
	}

	public void registerReloadListener(PreparableReloadListener listener) {
		if (packType == PackType.CLIENT_RESOURCES) {
			FMLJavaModLoadingContext.get().getModEventBus()
				.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(listener));
		} else {
			MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(listener));
		}
	}
}
