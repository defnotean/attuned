package net.fabricmc.fabric.api.resource;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public interface IdentifiableResourceReloadListener extends ResourceManagerReloadListener {
	Identifier getFabricId();
}
