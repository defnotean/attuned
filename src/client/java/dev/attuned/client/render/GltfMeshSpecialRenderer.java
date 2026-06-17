package dev.attuned.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import dev.attuned.Attuned;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Owned {@code attuned:gltf_mesh} renderer ported to the legacy 1.18.2 client API.
 *
 * <p>The modern special-model SPI ({@code SpecialModelRenderers} /
 * {@code NoDataSpecialModelRenderer} / {@code minecraft:special}) does not exist on
 * this generation, so the shared {@link AttunedGltfModels} mesh is drawn through
 * Fabric's {@link BuiltinItemRendererRegistry}. The temporary harpoon is a vanilla
 * {@link Items#TRIDENT} stack carrying Attuned marker NBT (see
 * {@code HarpoonBehavior}), so the dispatcher is registered against the trident:
 * marked stacks render the glTF mesh, every other trident falls back to the vanilla
 * {@link TridentModel}.
 */
public final class GltfMeshSpecialRenderer implements GltfModelReceiver {
	private static final ResourceLocation MODEL =
		new ResourceLocation(Attuned.MOD_ID, "gltf/ocean_relic_trident.glb");
	private static final ResourceLocation TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/item/ocean_relic_trident_blockbench.png");
	private static final String HARPOON_ROOT_KEY = "AttunedHarpoon";
	private static final String HARPOON_MARKER_KEY = "marker";
	private static final String HARPOON_MARKER_ID = "attuned:offshore_harpoon";
	private static boolean initialized;

	private final ResourceLocation model;
	private final Optional<ResourceLocation> texture;
	private AttunedGltfModels.RenderedGltfModel renderedModel;
	private TridentModel vanillaTridentModel;

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
		GltfMeshSpecialRenderer renderer = new GltfMeshSpecialRenderer(MODEL, Optional.of(TEXTURE));
		BuiltinItemRendererRegistry.INSTANCE.register(Items.TRIDENT,
			(stack, mode, matrices, vertexConsumers, light, overlay) ->
				renderer.renderItem(stack, matrices, vertexConsumers, light, overlay));
	}

	@Override
	public ResourceLocation getModelLocation() {
		return this.model;
	}

	@Override
	public void onReceiveSharedModel(AttunedGltfModels.RenderedGltfModel renderedModel) {
		this.renderedModel = renderedModel;
	}

	private void renderItem(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
			int light, int overlay) {
		if (isTemporaryHarpoon(stack)) {
			renderMesh(poseStack, bufferSource, light, overlay, stack.hasFoil());
		} else {
			renderVanillaTrident(stack, poseStack, bufferSource, light, overlay);
		}
	}

	private void renderMesh(PoseStack poseStack, MultiBufferSource bufferSource,
			int light, int overlay, boolean foil) {
		AttunedGltfModels.RenderedGltfModel mesh = renderedModel();
		if (mesh == null || mesh.primitives().isEmpty()) {
			renderVanillaTridentModel(poseStack, bufferSource, light, overlay, foil);
			return;
		}

		poseStack.pushPose();
		// Match the vanilla trident BlockEntityWithoutLevelRenderer coordinate frame so the
		// shipped held/ground/thrown display transforms keep placing the mesh in the hand.
		poseStack.scale(1.0F, -1.0F, -1.0F);
		PoseStack.Pose pose = poseStack.last();
		for (AttunedGltfModels.RenderedPrimitive primitive : mesh.primitives()) {
			List<AttunedGltfModels.Triangle> triangles = primitive.triangles();
			if (triangles.isEmpty()) {
				continue;
			}
			ResourceLocation resolvedTexture = this.texture.orElse(primitive.texture());
			VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(resolvedTexture));
			renderTriangles(triangles, pose, consumer, light, overlay);
			if (foil) {
				VertexConsumer glint = bufferSource.getBuffer(RenderType.entityGlintDirect());
				renderTriangles(triangles, pose, glint, light, overlay);
			}
		}
		poseStack.popPose();
	}

	private void renderVanillaTrident(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
			int light, int overlay) {
		renderVanillaTridentModel(poseStack, bufferSource, light, overlay, stack.hasFoil());
	}

	private void renderVanillaTridentModel(PoseStack poseStack, MultiBufferSource bufferSource,
			int light, int overlay, boolean foil) {
		TridentModel tridentModel = vanillaTridentModel();
		if (tridentModel == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(bufferSource,
			tridentModel.renderType(TridentModel.TEXTURE), false, foil);
		tridentModel.renderToBuffer(poseStack, consumer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
		poseStack.popPose();
	}

	private AttunedGltfModels.RenderedGltfModel renderedModel() {
		if (this.renderedModel == null) {
			this.renderedModel = AttunedGltfModels.getInstance().getOrLoad(this.model).orElse(null);
		}
		return this.renderedModel;
	}

	private TridentModel vanillaTridentModel() {
		if (this.vanillaTridentModel == null) {
			try {
				this.vanillaTridentModel = new TridentModel(
					Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.TRIDENT));
			} catch (RuntimeException ex) {
				Attuned.LOGGER.warn("Unable to bake the vanilla trident fallback model", ex);
			}
		}
		return this.vanillaTridentModel;
	}

	private static boolean isTemporaryHarpoon(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.TRIDENT)) {
			return false;
		}
		CompoundTag root = stack.getTag();
		if (root == null || !root.contains(HARPOON_ROOT_KEY)) {
			return false;
		}
		CompoundTag tag = root.getCompound(HARPOON_ROOT_KEY);
		return HARPOON_MARKER_ID.equals(tag.contains(HARPOON_MARKER_KEY) ? tag.getString(HARPOON_MARKER_KEY) : "");
	}

	private static void renderTriangles(List<AttunedGltfModels.Triangle> mesh, PoseStack.Pose pose,
			VertexConsumer vertexConsumer, int light, int overlay) {
		for (AttunedGltfModels.Triangle triangle : mesh) {
			emit(vertexConsumer, pose, triangle.a(), light, overlay);
			emit(vertexConsumer, pose, triangle.b(), light, overlay);
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
			// Minecraft item/entity cutout buffers consume quads. Repeating C
			// turns each glTF triangle into a degenerate quad instead of stitching
			// unrelated triangles together.
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, PoseStack.Pose pose,
			AttunedGltfModels.Vertex vertex, int light, int overlay) {
		Matrix4f position = pose.pose();
		Matrix3f normalMatrix = pose.normal();
		vertexConsumer.vertex(position, vertex.x(), vertex.y(), vertex.z())
			.color(255, 255, 255, 255)
			.uv(vertex.u(), vertex.v())
			.overlayCoords(overlay)
			.uv2(light)
			.normal(normalMatrix, vertex.nx(), vertex.ny(), vertex.nz())
			.endVertex();
	}
}
