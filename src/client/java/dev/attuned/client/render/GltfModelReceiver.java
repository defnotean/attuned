package dev.attuned.client.render;

import net.minecraft.resources.Identifier;

public interface GltfModelReceiver {
	Identifier getModelLocation();

	default void onReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
	}

	default boolean isReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
		return true;
	}
}
