package dev.attuned.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.attuned.Attuned;
import dev.attuned.client.mixin.SpecialModelRenderersAccessor;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

public final class GltfMeshSpecialRenderer implements NoDataSpecialModelRenderer, GltfModelReceiver {
	private static final Identifier TYPE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "gltf_mesh");
	private static boolean initialized;

	private final Identifier model;
	private final Optional<Identifier> texture;
	private AttunedGltfModels.RenderedGltfModel renderedModel;

	private GltfMeshSpecialRenderer(Identifier model, Optional<Identifier> texture) {
		this.model = model;
		this.texture = texture;
		AttunedGltfModels.getInstance().addGltfModelReceiver(this);
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedGltfModels.getInstance().init();
		SpecialModelRenderersAccessor.attuned$idMapper().put(TYPE, Unbaked.MAP_CODEC);
	}

	@Override
	public Identifier getModelLocation() {
		return this.model;
	}

	@Override
	public void onReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
		this.renderedModel = renderedModel;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			int light, int overlay, boolean foil, int outlineColor) {
		AttunedGltfModels.RenderedGltfModel mesh = renderedModel();
		if (mesh == null || mesh.primitives().isEmpty()) {
			return;
		}

		int order = 0;
		for (AttunedGltfModels.RenderedPrimitive primitive : mesh.primitives()) {
			List<AttunedGltfModels.Triangle> triangles = primitive.triangles();
			if (triangles.isEmpty()) {
				continue;
			}
			Identifier resolvedTexture = this.texture.orElse(primitive.texture());
			submitNodeCollector.order(order++).submitCustomGeometry(
				poseStack,
				RenderTypes.entityCutout(resolvedTexture),
				(pose, vertexConsumer) -> renderMesh(triangles, pose, vertexConsumer, light, overlay));
			if (foil) {
				submitNodeCollector.order(order++).submitCustomGeometry(
					poseStack,
					RenderTypes.entityGlint(),
					(pose, vertexConsumer) -> renderMesh(triangles, pose, vertexConsumer, light, overlay));
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		AttunedGltfModels.RenderedGltfModel mesh = renderedModel();
		if (mesh != null) {
			mesh.getExtents(output);
		}
	}

	private AttunedGltfModels.RenderedGltfModel renderedModel() {
		if (this.renderedModel == null) {
			this.renderedModel = AttunedGltfModels.getInstance().getOrLoad(this.model).orElse(null);
		}
		return this.renderedModel;
	}

	private static void renderMesh(List<AttunedGltfModels.Triangle> mesh, PoseStack.Pose pose,
			VertexConsumer vertexConsumer, int light, int overlay) {
		for (AttunedGltfModels.Triangle triangle : mesh) {
			emit(vertexConsumer, pose, triangle.a(), light, overlay);
			emit(vertexConsumer, pose, triangle.b(), light, overlay);
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
			// Minecraft 26.2 item/entity cutout buffers consume quads. Repeating C
			// turns each glTF triangle into a degenerate quad instead of stitching
			// unrelated triangles together.
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, PoseStack.Pose pose,
			AttunedGltfModels.Vertex vertex, int light, int overlay) {
		vertexConsumer.addVertex(pose, vertex.position())
			.setColor(0xFFFFFFFF)
			.setUv(vertex.u(), vertex.v())
			.setOverlay(overlay)
			.setLight(light)
			.setNormal(pose, vertex.normal().x(), vertex.normal().y(), vertex.normal().z());
	}

	public record Unbaked(Identifier model, Optional<Identifier> texture)
			implements NoDataSpecialModelRenderer.Unbaked {
		private static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
			Identifier.CODEC.optionalFieldOf("texture").forGetter(Unbaked::texture)
		).apply(instance, Unbaked::new));

		@Override
		public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public GltfMeshSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new GltfMeshSpecialRenderer(this.model, this.texture);
		}
	}
}
