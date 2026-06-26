package net.fabricmc.fabric.api.resource;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public interface IdentifiableResourceReloadListener extends PreparableReloadListener {
	Identifier getFabricId();
}
