package dev.attuned.client;

import net.minecraft.client.renderer.RenderType;

/** Render types used by Tremor's ore reveal. */
public final class TremorOreRenderTypes {
	private TremorOreRenderTypes() {}

	public static RenderType oreOutline() {
		return RenderType.lines();
	}
}
