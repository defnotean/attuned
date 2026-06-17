package dev.attuned.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.attuned.Attuned;
import dev.attuned.client.mixin.SpecialModelRenderersAccessor;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class BlockbenchMeshSpecialRenderer implements NoDataSpecialModelRenderer {
	private static final Identifier TYPE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "blockbench_mesh");
	private static final float MODEL_UNIT = 1.0F / 16.0F;
	private static boolean initialized;

	private final Identifier model;
	private final Identifier texture;
	private List<Triangle> triangles;

	private BlockbenchMeshSpecialRenderer(Identifier model, Identifier texture) {
		this.model = model;
		this.texture = texture;
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		SpecialModelRenderersAccessor.attuned$idMapper().put(TYPE, Unbaked.MAP_CODEC);
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			int light, int overlay, boolean foil, int outlineColor) {
		List<Triangle> mesh = triangles();
		if (mesh.isEmpty()) {
			return;
		}

		submitNodeCollector.order(0).submitCustomGeometry(
			poseStack,
			RenderTypes.entityCutout(this.texture),
			(pose, vertexConsumer) -> renderMesh(mesh, pose, vertexConsumer, light, overlay));
		if (foil) {
			submitNodeCollector.order(1).submitCustomGeometry(
				poseStack,
				RenderTypes.entityGlint(),
				(pose, vertexConsumer) -> renderMesh(mesh, pose, vertexConsumer, light, overlay));
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
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
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(this.model)) {
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
		Map<String, Vector3f> smoothNormals = "smooth".equals(string(mesh, "shading"))
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
			Vector3f aPosition = position(vertices, aId);
			Vector3f bPosition = position(vertices, bId);
			Vector3f cPosition = position(vertices, cId);
			Vector3f faceNormal = normal(aPosition, bPosition, cPosition);
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

	private static Vector3f position(JsonObject vertices, String id) {
		JsonArray position = vertices.getAsJsonArray(id);
		return new Vector3f(
			position.get(0).getAsFloat() * MODEL_UNIT,
			position.get(1).getAsFloat() * MODEL_UNIT,
			position.get(2).getAsFloat() * MODEL_UNIT);
	}

	private static Vertex vertex(Vector3f position, JsonObject uvs, String id,
			float textureWidth, float textureHeight, Vector3fc normal) {
		JsonArray uv = uvs.getAsJsonArray(id);
		return new Vertex(
			position,
			uv.get(0).getAsFloat() / textureWidth,
			blockbenchV(uv, textureHeight),
			new Vector3f(normal));
	}

	private static float blockbenchV(JsonArray uv, float textureHeight) {
		// The bbmodel file stores UV Y from the top of the texture, matching Minecraft model UVs.
		return uv.get(1).getAsFloat() / textureHeight;
	}

	private static Map<String, Vector3f> smoothNormals(JsonObject vertices, JsonObject faces) {
		Map<String, Vector3f> sums = new HashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
			JsonObject face = entry.getValue().getAsJsonObject();
			JsonArray vertexIds = face.getAsJsonArray("vertices");
			if (vertexIds.size() != 3) {
				continue;
			}
			Vector3f faceNormal = normal(
				position(vertices, vertexIds.get(0).getAsString()),
				position(vertices, vertexIds.get(1).getAsString()),
				position(vertices, vertexIds.get(2).getAsString()));
			for (JsonElement vertexId : vertexIds) {
				String id = vertexId.getAsString();
				sums.computeIfAbsent(id, ignored -> new Vector3f()).add(faceNormal);
				counts.merge(id, 1, Integer::sum);
			}
		}
		for (Map.Entry<String, Vector3f> entry : sums.entrySet()) {
			entry.getValue().div(counts.get(entry.getKey()));
			if (entry.getValue().lengthSquared() > 1.0E-8F) {
				entry.getValue().normalize();
			}
		}
		return sums;
	}

	private static Vector3f normal(Vector3fc a, Vector3fc b, Vector3fc c) {
		Vector3f ab = new Vector3f(b).sub(a);
		Vector3f ac = new Vector3f(c).sub(a);
		Vector3f normal = ab.cross(ac);
		if (normal.lengthSquared() < 1.0E-8F) {
			return new Vector3f(0.0F, 1.0F, 0.0F);
		}
		return normal.normalize();
	}

	private static void renderMesh(List<Triangle> mesh, PoseStack.Pose pose,
			VertexConsumer vertexConsumer, int light, int overlay) {
		for (Triangle triangle : mesh) {
			emit(vertexConsumer, pose, triangle.a(), light, overlay);
			emit(vertexConsumer, pose, triangle.b(), light, overlay);
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
			// Minecraft 26.2 item/entity cutout buffers consume quads. Repeating C
			// keeps legacy Blockbench triangle meshes from being stitched together.
			emit(vertexConsumer, pose, triangle.c(), light, overlay);
		}
	}

	private static void emit(VertexConsumer vertexConsumer, PoseStack.Pose pose,
			Vertex vertex, int light, int overlay) {
		vertexConsumer.addVertex(pose, vertex.position())
			.setColor(0xFFFFFFFF)
			.setUv(vertex.u(), vertex.v())
			.setOverlay(overlay)
			.setLight(light)
			.setNormal(pose, vertex.normal().x(), vertex.normal().y(), vertex.normal().z());
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

	public record Unbaked(Identifier model, Identifier texture) implements NoDataSpecialModelRenderer.Unbaked {
		private static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
			Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)
		).apply(instance, Unbaked::new));

		@Override
		public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public BlockbenchMeshSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new BlockbenchMeshSpecialRenderer(this.model, this.texture);
		}
	}

	private record Vertex(Vector3f position, float u, float v, Vector3f normal) {
	}

	private record Triangle(Vertex a, Vertex b, Vertex c) {
	}
}
