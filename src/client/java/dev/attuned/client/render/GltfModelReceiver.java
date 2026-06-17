package dev.attuned.client.render;

import net.minecraft.resources.ResourceLocation;

public interface GltfModelReceiver {
	ResourceLocation getModelLocation();

	default void onReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
	}

	default boolean isReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
		return true;
	}
}
