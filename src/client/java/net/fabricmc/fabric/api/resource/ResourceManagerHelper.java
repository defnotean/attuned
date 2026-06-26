package net.fabricmc.fabric.api.resource;

import dev.attuned.platform.NeoForgeEventBuses;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

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
			NeoForgeEventBuses.modEventBus()
				.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(listener));
		} else {
			NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(listener));
		}
	}
}
