package net.fabricmc.fabric.api.resource;

import dev.attuned.platform.NeoForgeEventBuses;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

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
				.addListener((AddClientReloadListenersEvent event) -> event.addListener(idOf(listener), listener));
		} else {
			NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> event.addListener(idOf(listener), listener));
		}
	}

	private static Identifier idOf(PreparableReloadListener listener) {
		if (listener instanceof IdentifiableResourceReloadListener identifiable) {
			return identifiable.getFabricId();
		}
		return Identifier.fromNamespaceAndPath("attuned", listener.getClass().getName().replace('.', '_').toLowerCase());
	}
}
