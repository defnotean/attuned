package dev.attuned.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Render types used by Tremor's ore reveal. */
public final class TremorOreRenderTypes {
	private TremorOreRenderTypes() {}

	public static RenderType oreOutline() {
		return RenderTypes.linesTranslucent();
	}
}
