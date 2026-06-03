package dev.attuned.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.attuned.Attuned;
import dev.attuned.client.mixin.RenderTypeInvoker;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Render types used by Tremor's ore reveal. */
public final class TremorOreRenderTypes {
	private static final RenderPipeline ORE_OUTLINE_PIPELINE = oreOutlinePipeline();
	private static final RenderType ORE_OUTLINE = RenderTypeInvoker.attuned$create(
		"attuned_tremor_ore_outline",
		RenderSetup.builder(ORE_OUTLINE_PIPELINE)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	private TremorOreRenderTypes() {}

	public static RenderType oreOutline() {
		return ORE_OUTLINE;
	}

	private static RenderPipeline oreOutlinePipeline() {
		RenderPipeline base = RenderPipelines.LINES_TRANSLUCENT;
		RenderPipeline.Snippet lineSnippet = new RenderPipeline.Snippet(
			Optional.of(base.getVertexShader()),
			Optional.of(base.getFragmentShader()),
			Optional.of(base.getShaderDefines()),
			Optional.of(base.getSamplers()),
			Optional.of(base.getUniforms()),
			Optional.of(base.getColorTargetState()),
			Optional.of(new DepthStencilState(CompareOp.ALWAYS_PASS, false)),
			Optional.of(base.getPolygonMode()),
			Optional.of(base.isCull()),
			Optional.of(base.getVertexFormat()),
			Optional.of(base.getVertexFormatMode()));

		return RenderPipeline.builder(lineSnippet)
			.withLocation(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "pipeline/tremor_ore_outline"))
			.build();
	}
}
