package dev.attuned.client.mixin;

import dev.attuned.client.AttunedThrownHarpoonRenderState;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownTridentRenderState.class)
public abstract class ThrownTridentRenderStateMixin implements AttunedThrownHarpoonRenderState {
	@Unique
	private boolean attuned$temporaryHarpoon;

	@Unique
	private final ItemStackRenderState attuned$item = new ItemStackRenderState();

	@Override
	public boolean attuned$isTemporaryHarpoon() {
		return this.attuned$temporaryHarpoon;
	}

	@Override
	public void attuned$setTemporaryHarpoon(boolean temporaryHarpoon) {
		this.attuned$temporaryHarpoon = temporaryHarpoon;
	}

	@Override
	public ItemStackRenderState attuned$item() {
		return this.attuned$item;
	}
}
