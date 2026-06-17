package dev.attuned.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.attuned.Attuned;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Loads the editable Blockbench (.bbmodel) mesh and renders it through the 1.21.1
 * builder {@link VertexConsumer} API. The modern special-model SPI is absent on
 * this generation, so this renderer draws through
 * {@link MultiBufferSource#getBuffer(RenderType)} with
 * {@link RenderType#entityCutout(ResourceLocation)} instead of a
 * {@code NoDataSpecialModelRenderer}.
 */
public final class BlockbenchMeshSpecialRenderer {
	private static final float MODEL_UNIT = 1.0F / 16.0F;

	private final ResourceLocation model;
	private final ResourceLocation texture;
	private List<Triangle> triangles;

	public BlockbenchMeshSpecialRenderer(ResourceLocation model, ResourceLocation texture) {
		this.model = model;
		this.texture = texture;
	}

	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, boolean foil) {
		List<Triangle> mesh = triangles();
		if (mesh.isEmpty()) {
			return;
		}

		PoseStack.Pose pose = poseStack.last();
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(this.texture));
		renderMesh(mesh, pose, consumer, light, overlay);
		if (foil) {
			VertexConsumer glint = bufferSource.getBuffer(RenderType.entityGlintDirect());
			renderMesh(mesh, pose, glint, light, overlay);
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
		Resource resource = Minecraft.getInstance().getResourceManager().getResource(this.model).orElse(null);
		if (resource == null) {
			Attuned.LOGGER.warn("Missing Blockbench mesh {} for special item rendering", this.model);
			return List.of();
		}
		try (InputStream input = resource.open();
				Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
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
		} catch (IOException | IllegalStateException ex) {
			Attuned.LOGGER.warn("Unable to load Blockbench mesh {} for special item rendering", this.model, ex);
			return List.of();
		}
	}

	private static void appendMeshTriangles(List<Triangle> loaded, JsonObject mesh,
			float textureWidth, float textureHeight) {
		JsonObject vertices = mesh.getAsJsonObject("vertices");
		JsonObject faces = mesh.getAsJsonObject("faces");
		Map<String, float[]> smoothNormals = "smooth".equals(string(mesh, "shading"))
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
			float[] aPosition = position(vertices, aId);
			float[] bPosition = position(vertices, bId);
			float[] cPosition = position(vertices, cId);
			float[] faceNormal = normal(aPosition, bPosition, cPosition);
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

	private static float[] position(JsonObject vertices, String id) {
		JsonArray position = vertices.getAsJsonArray(id);
		return new float[] {
			position.get(0).getAsFloat() * MODEL_UNIT,
			position.get(1).getAsFloat() * MODEL_UNIT,
			position.get(2).getAsFloat() * MODEL_UNIT};
	}

	private static Vertex vertex(float[] position, JsonObject uvs, String id,
			float textureWidth, float textureHeight, float[] normal) {
		JsonArray uv = uvs.getAsJsonArray(id);
		return new Vertex(
			position[0], position[1], position[2],
			uv.get(0).getAsFloat() / textureWidth,
			blockbenchV(uv, textureHeight),
			normal[0], normal[1], normal[2]);
	}

	private static float blockbenchV(JsonArray uv, float textureHeight) {
		// The bbmodel file stores UV Y from the top of the texture, matching Minecraft model UVs.
		return uv.get(1).getAsFloat() / textureHeight;
	}

	private static Map<String, float[]> smoothNormals(JsonObject vertices, JsonObject faces) {
		Map<String, float[]> sums = new HashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
			JsonObject face = entry.getValue().getAsJsonObject();
			JsonArray vertexIds = face.getAsJsonArray("vertices");
			if (vertexIds.size() != 3) {
				continue;
			}
			float[] faceNormal = normal(
				position(vertices, vertexIds.get(0).getAsString()),
				position(vertices, vertexIds.get(1).getAsString()),
				position(vertices, vertexIds.get(2).getAsString()));
			for (JsonElement vertexId : vertexIds) {
				String id = vertexId.getAsString();
				float[] sum = sums.computeIfAbsent(id, ignored -> new float[3]);
				sum[0] += faceNormal[0];
				sum[1] += faceNormal[1];
				sum[2] += faceNormal[2];
				counts.merge(id, 1, Integer::sum);
			}
		}
		for (Map.Entry<String, float[]> entry : sums.entrySet()) {
			float[] sum = entry.getValue();
			int count = counts.get(entry.getKey());
			sum[0] /= count;
			sum[1] /= count;
			sum[2] /= count;
			float lengthSquared = sum[0] * sum[0] + sum[1] * sum[1] + sum[2] * sum[2];
			if (lengthSquared > 1.0E-8F) {
				float inverse = (float) (1.0 / Math.sqrt(lengthSquared));
				sum[0] *= inverse;
				sum[1] *= inverse;
				sum[2] *= inverse;
			}
		}
		return sums;
	}

	private static float[] normal(float[] a, float[] b, float[] c) {
		float abx = b[0] - a[0];
		float aby = b[1] - a[1];
		float abz = b[2] - a[2];
		float acx = c[0] - a[0];
		float acy = c[1] - a[1];
		float acz = c[2] - a[2];
		float nx = aby * acz - abz * acy;
		float ny = abz * acx - abx * acz;
		float nz = abx * acy - aby * acx;
		float lengthSquared = nx * nx + ny * ny + nz * nz;
		if (lengthSquared < 1.0E-8F) {
			return new float[] {0.0F, 1.0F, 0.0F};
		}
		float inverse = (float) (1.0 / Math.sqrt(lengthSquared));
		return new float[] {nx * inverse, ny * inverse, nz * inverse};
	}

	private static void renderMesh(List<Triangle> mesh, PoseStack.Pose pose,
			VertexConsumer vertexConsumer, int light, int overlay) {
		for (Triangle triangle : mesh) {
			emit(vertexConsumer, pose, triangle.a(), light, overlay);
			emit(vertexConsumer, pose, triangle.b(), light, overlay);
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
			// Minecraft item/entity cutout buffers consume quads. Repeating C
			// keeps legacy Blockbench triangle meshes from being stitched together.
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, PoseStack.Pose pose,
			Vertex vertex, int light, int overlay) {
		// 1.21.1 builder VertexConsumer: addVertex(pose, ..) and setNormal(pose, ..)
		// fold the JOML pose/normal matrices in for us, so no com.mojang.math types.
		vertexConsumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
			.setColor(255, 255, 255, 255)
			.setUv(vertex.u(), vertex.v())
			.setOverlay(overlay)
			.setLight(light)
			.setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
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

	private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
	}

	private record Triangle(Vertex a, Vertex b, Vertex c) {
	}
}
