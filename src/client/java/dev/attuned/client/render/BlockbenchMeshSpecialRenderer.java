package dev.attuned.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.attuned.Attuned;
import dev.attuned.client.render.AttunedGltfModels.V3;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Older-generation (Minecraft 1.20.1) port of the Blockbench (.bbmodel) mesh renderer.
 *
 * <p>This branch has no special-model SPI, so the renderer is a Fabric
 * {@link BuiltinItemRendererRegistry.DynamicItemRenderer} drawing through
 * {@link RenderType#entityCutout(ResourceLocation)} with the legacy chained {@link VertexConsumer}
 * API. The Ocean Relic Trident ships its runtime mesh as a GLB (see {@link GltfMeshSpecialRenderer}),
 * so this loader is kept for direct .bbmodel drops and shares the same triangle-to-quad safety fix.
 */
public final class BlockbenchMeshSpecialRenderer
		implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final float MODEL_UNIT = 1.0F / 16.0F;

	private final ResourceLocation model;
	private final ResourceLocation texture;
	private List<Triangle> triangles;

	public BlockbenchMeshSpecialRenderer(ResourceLocation model, ResourceLocation texture) {
		this.model = model;
		this.texture = texture;
	}

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack,
			MultiBufferSource bufferSource, int light, int overlay) {
		List<Triangle> mesh = triangles();
		if (mesh.isEmpty()) {
			return;
		}
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(this.texture));
		renderMesh(mesh, pose, vertexConsumer, light, overlay);
	}

	public void getExtents(java.util.function.Consumer<V3> output) {
		for (Triangle triangle : triangles()) {
			output.accept(triangle.a().position());
			output.accept(triangle.b().position());
			output.accept(triangle.c().position());
		}
	}

	private List<Triangle> triangles() {
		List<Triangle> loaded = this.triangles;
		if (loaded == null) {
			loaded = loadTriangles();
			this.triangles = loaded;
		}
		return loaded;
	}

	private List<Triangle> loadTriangles() {
		try {
			Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(this.model);
			try (Reader reader = resource.openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonObject resolution = root.getAsJsonObject("resolution");
				float textureWidth = dimension(resolution, "width");
				float textureHeight = dimension(resolution, "height");
				List<Triangle> loaded = new ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("elements")) {
					JsonObject mesh = element.getAsJsonObject();
					if (!"mesh".equals(string(mesh, "type"))) {
						continue;
					}
					appendMeshTriangles(loaded, mesh, textureWidth, textureHeight);
				}
				return List.copyOf(loaded);
			}
		} catch (IOException | IllegalStateException ex) {
			Attuned.LOGGER.warn("Unable to load Blockbench mesh {} for special item rendering", this.model, ex);
			return List.of();
		}
	}

	private static void appendMeshTriangles(List<Triangle> loaded, JsonObject mesh,
			float textureWidth, float textureHeight) {
		JsonObject vertices = mesh.getAsJsonObject("vertices");
		JsonObject faces = mesh.getAsJsonObject("faces");
		Map<String, V3> smoothNormals = "smooth".equals(string(mesh, "shading"))
			? smoothNormals(vertices, faces)
			: Map.of();
		for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
			JsonObject face = entry.getValue().getAsJsonObject();
			JsonArray vertexIds = face.getAsJsonArray("vertices");
			if (vertexIds.size() != 3) {
				continue;
			}
			String aId = vertexIds.get(0).getAsString();
			String bId = vertexIds.get(1).getAsString();
			String cId = vertexIds.get(2).getAsString();
			V3 aPosition = position(vertices, aId);
			V3 bPosition = position(vertices, bId);
			V3 cPosition = position(vertices, cId);
			V3 faceNormal = normal(aPosition, bPosition, cPosition);
			JsonObject uvs = face.getAsJsonObject("uv");
			Vertex a = vertex(aPosition, uvs, aId, textureWidth, textureHeight,
				smoothNormals.getOrDefault(aId, faceNormal));
			Vertex b = vertex(bPosition, uvs, bId, textureWidth, textureHeight,
				smoothNormals.getOrDefault(bId, faceNormal));
			Vertex c = vertex(cPosition, uvs, cId, textureWidth, textureHeight,
				smoothNormals.getOrDefault(cId, faceNormal));
			loaded.add(new Triangle(a, b, c));
		}
	}

	private static V3 position(JsonObject vertices, String id) {
		JsonArray position = vertices.getAsJsonArray(id);
		return new V3(
			position.get(0).getAsFloat() * MODEL_UNIT,
			position.get(1).getAsFloat() * MODEL_UNIT,
			position.get(2).getAsFloat() * MODEL_UNIT);
	}

	private static Vertex vertex(V3 position, JsonObject uvs, String id,
			float textureWidth, float textureHeight, V3 normal) {
		JsonArray uv = uvs.getAsJsonArray(id);
		return new Vertex(
			position,
			uv.get(0).getAsFloat() / textureWidth,
			blockbenchV(uv, textureHeight),
			normal);
	}

	private static float blockbenchV(JsonArray uv, float textureHeight) {
		// The bbmodel file stores UV Y from the top of the texture, matching Minecraft model UVs.
		return uv.get(1).getAsFloat() / textureHeight;
	}

	private static Map<String, V3> smoothNormals(JsonObject vertices, JsonObject faces) {
		Map<String, float[]> sums = new HashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
			JsonObject face = entry.getValue().getAsJsonObject();
			JsonArray vertexIds = face.getAsJsonArray("vertices");
			if (vertexIds.size() != 3) {
				continue;
			}
			V3 faceNormal = normal(
				position(vertices, vertexIds.get(0).getAsString()),
				position(vertices, vertexIds.get(1).getAsString()),
				position(vertices, vertexIds.get(2).getAsString()));
			for (JsonElement vertexId : vertexIds) {
				String id = vertexId.getAsString();
				float[] sum = sums.computeIfAbsent(id, ignored -> new float[3]);
				sum[0] += faceNormal.x();
				sum[1] += faceNormal.y();
				sum[2] += faceNormal.z();
				counts.merge(id, 1, Integer::sum);
			}
		}
		Map<String, V3> result = new HashMap<>();
		for (Map.Entry<String, float[]> entry : sums.entrySet()) {
			float[] sum = entry.getValue();
			int count = counts.get(entry.getKey());
			result.put(entry.getKey(),
				new V3(sum[0] / count, sum[1] / count, sum[2] / count).normalize());
		}
		return result;
	}

	private static V3 normal(V3 a, V3 b, V3 c) {
		V3 ab = b.sub(a);
		V3 ac = c.sub(a);
		V3 normal = ab.cross(ac);
		if (normal.lengthSquared() < 1.0E-8F) {
			return new V3(0.0F, 1.0F, 0.0F);
		}
		return normal.normalize();
	}

	private static void renderMesh(List<Triangle> mesh, PoseStack.Pose pose,
			VertexConsumer vertexConsumer, int light, int overlay) {
		Matrix4f matrix = pose.pose();
		Matrix3f normalMatrix = pose.normal();
		for (Triangle triangle : mesh) {
			emit(vertexConsumer, matrix, normalMatrix, triangle.a(), light, overlay);
			emit(vertexConsumer, matrix, normalMatrix, triangle.b(), light, overlay);
			emit(vertexConsumer, matrix, normalMatrix, triangle.c(), light, overlay);
			// Minecraft entity cutout buffers consume quads. Repeating C keeps legacy Blockbench
			// triangle meshes from being stitched together.
			emit(vertexConsumer, matrix, normalMatrix, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, Matrix4f matrix, Matrix3f normalMatrix,
			Vertex vertex, int light, int overlay) {
		vertexConsumer
			.vertex(matrix, vertex.position().x(), vertex.position().y(), vertex.position().z())
			.color(0xFFFFFFFF)
			.uv(vertex.u(), vertex.v())
			.overlayCoords(overlay)
			.uv2(light)
			.normal(normalMatrix, vertex.normal().x(), vertex.normal().y(), vertex.normal().z())
			.endVertex();
	}

	private static float dimension(JsonObject resolution, String key) {
		if (resolution == null || !resolution.has(key)) {
			return 16.0F;
		}
		return Math.max(1.0F, resolution.get(key).getAsFloat());
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) ? object.get(key).getAsString() : "";
	}

	private record Vertex(V3 position, float u, float v, V3 normal) {
	}

	private record Triangle(Vertex a, Vertex b, Vertex c) {
	}
}
