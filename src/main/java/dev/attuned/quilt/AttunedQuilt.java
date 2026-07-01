package dev.attuned.quilt;

import dev.attuned.Attuned;
import net.fabricmc.api.ModInitializer;

public final class AttunedQuilt implements ModInitializer {
	@Override
	public void onInitialize() {
		new Attuned().onInitialize();
	}
}