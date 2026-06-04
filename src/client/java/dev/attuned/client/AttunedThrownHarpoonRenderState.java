package dev.attuned.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Client-side extension data used to replace vanilla thrown-trident rendering for temporary Offshore Harpoons. */
public interface AttunedThrownHarpoonRenderState {
	boolean attuned$isTemporaryHarpoon();

	void attuned$setTemporaryHarpoon(boolean temporaryHarpoon);

	ItemStackRenderState attuned$item();
}
