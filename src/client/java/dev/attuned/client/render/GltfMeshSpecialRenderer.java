package dev.attuned.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import dev.attuned.Attuned;
import dev.attuned.client.mixin.ItemRendererBlockEntityAccessor;
import dev.attuned.content.behavior.HarpoonBehavior;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Older-generation (Minecraft 1.19.2) port of the Ocean Relic Trident glTF special renderer.
 *
 * <p>The modern special-model SPI ({@code SpecialModelRenderers} / {@code NoDataSpecialModelRenderer}
 * / {@code minecraft:special}) does not exist on this branch, so instead of registering a
 * special-model codec the parsed mesh is drawn through Fabric's
 * {@link BuiltinItemRendererRegistry}. The shared GLB mesh and dynamic textures are still loaded
 * by {@link AttunedGltfModels}, so the renderer is a {@link GltfModelReceiver} exactly like the
 * modern build. The temporary Offshore Harpoon is a vanilla {@link Items#TRIDENT} stack, so the
 * renderer guards on {@link HarpoonBehavior#isTemporaryHarpoon(ItemStack)} and delegates ordinary
 * tridents back to the vanilla built-in renderer.
 */
public final class GltfMeshSpecialRenderer
		implements BuiltinItemRendererRegistry.DynamicItemRenderer, GltfModelReceiver {
	private static final ResourceLocation MODEL =
		new ResourceLocation(Attuned.MOD_ID, "gltf/ocean_relic_trident.glb");
	private static final ResourceLocation TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/item/ocean_relic_trident_blockbench.png");
	private static boolean initialized;

	private final ResourceLocation model;
	private final Optional<ResourceLocation> texture;
	private AttunedGltfModels.RenderedGltfModel renderedModel;

	private GltfMeshSpecialRenderer(ResourceLocation model, Optional<ResourceLocation> texture) {
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
		GltfMeshSpecialRenderer renderer =
			new GltfMeshSpecialRenderer(MODEL, Optional.of(TEXTURE));
		BuiltinItemRendererRegistry.INSTANCE.register(Items.TRIDENT, renderer);
	}

	@Override
	public ResourceLocation getModelLocation() {
		return this.model;
	}

	@Override
	public void onReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
		this.renderedModel = renderedModel;
	}

	@Override
	public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
			MultiBufferSource bufferSource, int light, int overlay) {
		if (!HarpoonBehavior.isTemporaryHarpoon(stack)) {
			// Ordinary vanilla tridents must keep their built-in 3D renderer. Render through the
			// vanilla BlockEntityWithoutLevelRenderer directly so we never re-enter this renderer.
			ItemRendererBlockEntityAccessor accessor =
				(ItemRendererBlockEntityAccessor) Minecraft.getInstance().getItemRenderer();
			accessor.attuned$blockEntityRenderer()
				.renderByItem(stack, mode, poseStack, bufferSource, light, overlay);
			return;
		}

		AttunedGltfModels.RenderedGltfModel mesh = renderedModel();
		if (mesh == null || mesh.primitives().isEmpty()) {
			return;
		}

		PoseStack.Pose pose = poseStack.last();
		for (AttunedGltfModels.RenderedPrimitive primitive : mesh.primitives()) {
			List<AttunedGltfModels.Triangle> triangles = primitive.triangles();
			if (triangles.isEmpty()) {
				continue;
			}
			ResourceLocation resolvedTexture = this.texture.orElse(primitive.texture());
			VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(resolvedTexture));
			renderMesh(triangles, pose, vertexConsumer, light, overlay);
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
		Matrix4f matrix = pose.pose();
		Matrix3f normalMatrix = pose.normal();
		for (AttunedGltfModels.Triangle triangle : mesh) {
			emit(vertexConsumer, matrix, normalMatrix, triangle.a(), light, overlay);
			emit(vertexConsumer, matrix, normalMatrix, triangle.b(), light, overlay);
			emit(vertexConsumer, matrix, normalMatrix, triangle.c(), light, overlay);
			// Minecraft entity cutout buffers consume quads. Repeating C turns each glTF triangle
			// into a degenerate quad instead of stitching unrelated triangles together.
			emit(vertexConsumer, matrix, normalMatrix, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, Matrix4f matrix, Matrix3f normalMatrix,
			AttunedGltfModels.Vertex vertex, int light, int overlay) {
		vertexConsumer
			.vertex(matrix, vertex.position().x(), vertex.position().y(), vertex.position().z())
			.color(0xFFFFFFFF)
			.uv(vertex.u(), vertex.v())
			.overlayCoords(overlay)
			.uv2(light)
			.normal(normalMatrix, vertex.normal().x(), vertex.normal().y(), vertex.normal().z())
			.endVertex();
	}
}
