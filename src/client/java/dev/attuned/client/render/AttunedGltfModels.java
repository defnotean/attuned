package dev.attuned.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.attuned.Attuned;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AttunedGltfModels implements IdentifiableResourceReloadListener {
	private static final AttunedGltfModels INSTANCE = new AttunedGltfModels();
	private static final Identifier RELOAD_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "gltf_models");
	private static final int GLB_MAGIC = 0x46546C67;
	private static final int GLB_VERSION = 2;
	private static final int JSON_CHUNK = 0x4E4F534A;
	private static final int BIN_CHUNK = 0x004E4942;
	private static final int MODE_TRIANGLES = 4;
	private static final int COMPONENT_UNSIGNED_BYTE = 5121;
	private static final int COMPONENT_UNSIGNED_SHORT = 5123;
	private static final int COMPONENT_UNSIGNED_INT = 5125;
	private static final int COMPONENT_FLOAT = 5126;

	private final List<GltfModelReceiver> receivers = new ArrayList<>();
	private final Map<Identifier, RenderedGltfModel> renderedModels = new HashMap<>();
	private final List<Identifier> dynamicTextures = new ArrayList<>();
	private boolean initialized;

	private AttunedGltfModels() {
	}

	public static AttunedGltfModels getInstance() {
		return INSTANCE;
	}

	public synchronized void init() {
		if (this.initialized) {
			return;
		}
		this.initialized = true;
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this);
	}

	public synchronized void addGltfModelReceiver(GltfModelReceiver receiver) {
		if (!this.receivers.contains(receiver)) {
			this.receivers.add(receiver);
		}
		RenderedGltfModel renderedModel = this.renderedModels.get(receiver.getModelLocation());
		if (renderedModel != null && receiver.isReceiveSharedModel(renderedModel)) {
			receiver.onReceiveSharedModel(renderedModel);
		}
	}

	public synchronized boolean removeGltfModelReceiver(GltfModelReceiver receiver) {
		return this.receivers.remove(receiver);
	}

	public Optional<RenderedGltfModel> getOrLoad(Identifier modelLocation) {
		synchronized (this) {
			RenderedGltfModel cached = this.renderedModels.get(modelLocation);
			if (cached != null) {
				return Optional.of(cached);
			}
		}

		try {
			RenderedGltfModel loaded = load(Minecraft.getInstance().getResourceManager(), modelLocation);
			synchronized (this) {
				this.renderedModels.put(modelLocation, loaded);
				notifyReceivers(modelLocation, loaded);
			}
			return Optional.of(loaded);
		} catch (IOException | IllegalArgumentException | IllegalStateException ex) {
			Attuned.LOGGER.warn("Unable to load glTF model {}", modelLocation, ex);
			return Optional.empty();
		}
	}

	@Override
	public Identifier getFabricId() {
		return RELOAD_ID;
	}

	@Override
	public CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState,
			Executor preparationExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier,
			Executor applyExecutor) {
		ResourceManager resourceManager = sharedState.resourceManager();
		Map<Identifier, List<GltfModelReceiver>> receiversByModel = receiversByModel();
		return CompletableFuture.supplyAsync(() -> loadAll(resourceManager, receiversByModel.keySet()),
				preparationExecutor)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(loaded -> {
				clearDynamicTextures();
				synchronized (this) {
					this.renderedModels.clear();
					this.renderedModels.putAll(loaded);
					for (Map.Entry<Identifier, RenderedGltfModel> entry : loaded.entrySet()) {
						notifyReceivers(entry.getKey(), entry.getValue());
					}
				}
			}, applyExecutor);
	}

	private synchronized Map<Identifier, List<GltfModelReceiver>> receiversByModel() {
		Map<Identifier, List<GltfModelReceiver>> grouped = new HashMap<>();
		for (GltfModelReceiver receiver : this.receivers) {
			grouped.computeIfAbsent(receiver.getModelLocation(), ignored -> new ArrayList<>()).add(receiver);
		}
		return grouped;
	}

	private Map<Identifier, RenderedGltfModel> loadAll(ResourceManager resourceManager, Set<Identifier> modelLocations) {
		Map<Identifier, RenderedGltfModel> loaded = new HashMap<>();
		for (Identifier modelLocation : modelLocations) {
			try {
				loaded.put(modelLocation, load(resourceManager, modelLocation));
			} catch (IOException | IllegalArgumentException | IllegalStateException ex) {
				Attuned.LOGGER.warn("Unable to load glTF model {} during resource reload", modelLocation, ex);
			}
		}
		return loaded;
	}

	private void notifyReceivers(Identifier modelLocation, RenderedGltfModel renderedModel) {
		for (GltfModelReceiver receiver : this.receivers) {
			if (modelLocation.equals(receiver.getModelLocation()) && receiver.isReceiveSharedModel(renderedModel)) {
				receiver.onReceiveSharedModel(renderedModel);
			}
		}
	}

	private void clearDynamicTextures() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		for (Identifier texture : this.dynamicTextures) {
			textureManager.release(texture);
		}
		this.dynamicTextures.clear();
	}

	private RenderedGltfModel load(ResourceManager resourceManager, Identifier modelLocation) throws IOException {
		try (InputStream input = resourceManager.open(modelLocation)) {
			return readGlb(modelLocation, input.readAllBytes());
		}
	}

	private RenderedGltfModel readGlb(Identifier modelLocation, byte[] bytes) {
		ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		require(file.remaining() >= 12, "GLB header is missing");
		require(file.getInt(0) == GLB_MAGIC, "GLB magic is invalid");
		require(file.getInt(4) == GLB_VERSION, "Only GLB v2 is supported");
		require(file.getInt(8) == bytes.length, "GLB declared length does not match file size");

		JsonObject root = null;
		ByteBuffer bin = null;
		int offset = 12;
		while (offset < bytes.length) {
			require(offset + 8 <= bytes.length, "GLB chunk header is truncated");
			int chunkLength = file.getInt(offset);
			int chunkType = file.getInt(offset + 4);
			int chunkStart = offset + 8;
			int chunkEnd = chunkStart + chunkLength;
			require(chunkLength >= 0 && chunkEnd <= bytes.length, "GLB chunk length is invalid");
			if (chunkType == JSON_CHUNK) {
				String json = new String(bytes, chunkStart, chunkLength, StandardCharsets.UTF_8).trim();
				root = JsonParser.parseString(json).getAsJsonObject();
			} else if (chunkType == BIN_CHUNK) {
				bin = slice(bytes, chunkStart, chunkLength);
			}
			offset = chunkEnd;
		}
		require(root != null, "GLB is missing a JSON chunk");
		require(bin != null, "GLB is missing a BIN chunk");
		return readScene(modelLocation, root, bin);
	}

	private static ByteBuffer slice(byte[] bytes, int offset, int length) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length).slice();
		return buffer.order(ByteOrder.LITTLE_ENDIAN);
	}

	private RenderedGltfModel readScene(Identifier modelLocation, JsonObject root, ByteBuffer bin) {
		List<RenderedPrimitive> primitives = new ArrayList<>();
		for (NodeMesh nodeMesh : sceneMeshes(root)) {
			JsonObject mesh = array(root, "meshes").get(nodeMesh.meshIndex()).getAsJsonObject();
			for (JsonElement element : array(mesh, "primitives")) {
				primitives.add(readPrimitive(modelLocation, root, bin, element.getAsJsonObject(), nodeMesh.transform()));
			}
		}
		require(!primitives.isEmpty(), "glTF scene does not contain renderable primitives");
		return new RenderedGltfModel(List.copyOf(primitives));
	}

	private static Set<NodeMesh> sceneMeshes(JsonObject root) {
		Set<NodeMesh> meshIndices = new LinkedHashSet<>();
		JsonArray nodes = array(root, "nodes");
		JsonArray scenes = array(root, "scenes");
		int sceneIndex = intOr(root, "scene", 0);
		require(sceneIndex >= 0 && sceneIndex < scenes.size(), "glTF scene index is out of range");
		JsonObject scene = scenes.get(sceneIndex).getAsJsonObject();
		for (JsonElement nodeIndex : array(scene, "nodes")) {
			collectSceneMeshes(nodes, nodeIndex.getAsInt(), new Matrix4f(), meshIndices);
		}
		require(!meshIndices.isEmpty(), "glTF scene does not reference any meshes");
		return meshIndices;
	}

	private static void collectSceneMeshes(JsonArray nodes, int nodeIndex,
			Matrix4f parentTransform, Set<NodeMesh> meshIndices) {
		require(nodeIndex >= 0 && nodeIndex < nodes.size(), "glTF node index is out of range");
		JsonObject node = nodes.get(nodeIndex).getAsJsonObject();
		Matrix4f transform = new Matrix4f(parentTransform).mul(nodeTransform(node));
		if (node.has("mesh")) {
			meshIndices.add(new NodeMesh(node.get("mesh").getAsInt(), transform));
		}
		if (node.has("children")) {
			for (JsonElement child : node.getAsJsonArray("children")) {
				collectSceneMeshes(nodes, child.getAsInt(), transform, meshIndices);
			}
		}
	}

	private static Matrix4f nodeTransform(JsonObject node) {
		if (node.has("matrix")) {
			JsonArray matrix = node.getAsJsonArray("matrix");
			require(matrix.size() == 16, "glTF node matrix must contain 16 values");
			return new Matrix4f(
				matrix.get(0).getAsFloat(), matrix.get(1).getAsFloat(),
				matrix.get(2).getAsFloat(), matrix.get(3).getAsFloat(),
				matrix.get(4).getAsFloat(), matrix.get(5).getAsFloat(),
				matrix.get(6).getAsFloat(), matrix.get(7).getAsFloat(),
				matrix.get(8).getAsFloat(), matrix.get(9).getAsFloat(),
				matrix.get(10).getAsFloat(), matrix.get(11).getAsFloat(),
				matrix.get(12).getAsFloat(), matrix.get(13).getAsFloat(),
				matrix.get(14).getAsFloat(), matrix.get(15).getAsFloat());
		}

		Matrix4f transform = new Matrix4f();
		if (node.has("translation")) {
			JsonArray translation = node.getAsJsonArray("translation");
			transform.translate(translation.get(0).getAsFloat(), translation.get(1).getAsFloat(),
				translation.get(2).getAsFloat());
		}
		if (node.has("rotation")) {
			JsonArray rotation = node.getAsJsonArray("rotation");
			transform.rotate(new Quaternionf(rotation.get(0).getAsFloat(), rotation.get(1).getAsFloat(),
				rotation.get(2).getAsFloat(), rotation.get(3).getAsFloat()));
		}
		if (node.has("scale")) {
			JsonArray scale = node.getAsJsonArray("scale");
			transform.scale(scale.get(0).getAsFloat(), scale.get(1).getAsFloat(), scale.get(2).getAsFloat());
		}
		return transform;
	}

	private RenderedPrimitive readPrimitive(Identifier modelLocation, JsonObject root, ByteBuffer bin,
			JsonObject primitive, Matrix4f transform) {
		require(intOr(primitive, "mode", MODE_TRIANGLES) == MODE_TRIANGLES,
			"Only glTF TRIANGLES primitives are supported");
		JsonObject attributes = primitive.getAsJsonObject("attributes");
		require(attributes != null && attributes.has("POSITION") && attributes.has("TEXCOORD_0"),
			"glTF primitive must contain POSITION and TEXCOORD_0 attributes");
		require(primitive.has("indices"), "glTF primitive must be indexed");

		Vector3f[] positions = readVec3Accessor(root, bin, attributes.get("POSITION").getAsInt());
		Vector3f[] normals = attributes.has("NORMAL")
			? readVec3Accessor(root, bin, attributes.get("NORMAL").getAsInt())
			: null;
		float[] uvs = readVec2Accessor(root, bin, attributes.get("TEXCOORD_0").getAsInt());
		int[] indices = readIndexAccessor(root, bin, primitive.get("indices").getAsInt());
		require(indices.length % 3 == 0, "glTF triangle index count must be divisible by 3");

		List<Triangle> triangles = new ArrayList<>();
		for (int index = 0; index < indices.length; index += 3) {
			int aIndex = indices[index];
			int bIndex = indices[index + 1];
			int cIndex = indices[index + 2];
			Vector3f faceNormal = normals == null
				? normal(positions[aIndex], positions[bIndex], positions[cIndex], transform)
				: null;
			triangles.add(new Triangle(
				vertex(positions, normals, uvs, aIndex, faceNormal, transform),
				vertex(positions, normals, uvs, bIndex, faceNormal, transform),
				vertex(positions, normals, uvs, cIndex, faceNormal, transform)));
		}
		Identifier texture = primitiveTexture(modelLocation, root, bin, primitive)
			.orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
		return new RenderedPrimitive(texture, List.copyOf(triangles));
	}

	private Optional<Identifier> primitiveTexture(Identifier modelLocation, JsonObject root,
			ByteBuffer bin, JsonObject primitive) {
		if (!primitive.has("material") || !root.has("materials")) {
			return Optional.empty();
		}
		JsonArray materials = root.getAsJsonArray("materials");
		int materialIndex = primitive.get("material").getAsInt();
		if (materialIndex < 0 || materialIndex >= materials.size()) {
			return Optional.empty();
		}
		JsonObject material = materials.get(materialIndex).getAsJsonObject();
		if (!material.has("pbrMetallicRoughness")) {
			return Optional.empty();
		}
		JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
		if (!pbr.has("baseColorTexture")) {
			return Optional.empty();
		}
		JsonObject baseColorTexture = pbr.getAsJsonObject("baseColorTexture");
		return textureImage(modelLocation, root, bin, baseColorTexture.get("index").getAsInt());
	}

	private Optional<Identifier> textureImage(Identifier modelLocation, JsonObject root,
			ByteBuffer bin, int textureIndex) {
		if (!root.has("textures") || !root.has("images")) {
			return Optional.empty();
		}
		JsonArray textures = root.getAsJsonArray("textures");
		if (textureIndex < 0 || textureIndex >= textures.size()) {
			return Optional.empty();
		}
		JsonObject texture = textures.get(textureIndex).getAsJsonObject();
		if (!texture.has("source")) {
			return Optional.empty();
		}
		JsonArray images = root.getAsJsonArray("images");
		int imageIndex = texture.get("source").getAsInt();
		if (imageIndex < 0 || imageIndex >= images.size()) {
			return Optional.empty();
		}
		JsonObject image = images.get(imageIndex).getAsJsonObject();
		if (image.has("bufferView")) {
			return Optional.of(registerEmbeddedTexture(modelLocation, root, bin,
				image.get("bufferView").getAsInt(), imageIndex));
		}
		if (image.has("uri")) {
			return imageUriTexture(modelLocation, image.get("uri").getAsString(), imageIndex);
		}
		return Optional.empty();
	}

	private Identifier registerEmbeddedTexture(Identifier modelLocation, JsonObject root,
			ByteBuffer bin, int bufferViewIndex, int imageIndex) {
		ByteView view = view(root, bufferViewIndex);
		requireAvailable(bin, view.byteOffset(), view.byteLength());
		byte[] imageBytes = new byte[view.byteLength()];
		ByteBuffer source = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		source.position(view.byteOffset());
		source.get(imageBytes);
		return registerImageBytes(dynamicTextureId(modelLocation, imageIndex), imageBytes);
	}

	private Optional<Identifier> imageUriTexture(Identifier modelLocation, String uri, int imageIndex) {
		if (uri.startsWith("data:")) {
			int comma = uri.indexOf(',');
			if (comma < 0 || !uri.substring(0, comma).contains(";base64")) {
				return Optional.empty();
			}
			byte[] bytes = Base64.getDecoder().decode(uri.substring(comma + 1));
			return Optional.of(registerImageBytes(dynamicTextureId(modelLocation, imageIndex), bytes));
		}
		if (uri.contains("..") || uri.contains(":")) {
			return Optional.empty();
		}
		int slash = modelLocation.getPath().lastIndexOf('/');
		String prefix = slash >= 0 ? modelLocation.getPath().substring(0, slash + 1) : "";
		return Optional.of(Identifier.fromNamespaceAndPath(modelLocation.getNamespace(), prefix + uri));
	}

	private Identifier registerImageBytes(Identifier textureId, byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(bytes);
			Minecraft.getInstance().getTextureManager().register(textureId,
				new DynamicTexture(() -> textureId.toString(), image));
			synchronized (this) {
				this.dynamicTextures.add(textureId);
			}
			return textureId;
		} catch (IOException ex) {
			Attuned.LOGGER.warn("Unable to decode embedded glTF texture {}", textureId, ex);
			return TextureManager.INTENTIONAL_MISSING_TEXTURE;
		}
	}

	private static Identifier dynamicTextureId(Identifier modelLocation, int imageIndex) {
		String path = "gltf_dynamic/" + modelLocation.getNamespace() + "/"
			+ modelLocation.getPath().replace('.', '_') + "/image_" + imageIndex;
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID, path);
	}

	private static Vertex vertex(Vector3f[] positions, Vector3f[] normals, float[] uvs,
			int index, Vector3f faceNormal, Matrix4f transform) {
		require(index >= 0 && index < positions.length, "glTF index is outside POSITION accessor");
		require(index * 2 + 1 < uvs.length, "glTF index is outside TEXCOORD_0 accessor");
		Vector3f position = transform.transformPosition(new Vector3f(positions[index]));
		Vector3f normal = normals != null && index < normals.length
			? transform.transformDirection(new Vector3f(normals[index]))
			: new Vector3f(faceNormal);
		if (normal.lengthSquared() <= 1.0E-8F) {
			normal.set(0.0F, 1.0F, 0.0F);
		} else {
			normal.normalize();
		}
		return new Vertex(position, uvs[index * 2], uvs[index * 2 + 1], normal);
	}

	private static Vector3f[] readVec3Accessor(JsonObject root, ByteBuffer bin, int accessorIndex) {
		Accessor accessor = accessor(root, accessorIndex);
		require(accessor.componentType() == COMPONENT_FLOAT && "VEC3".equals(accessor.type()),
			"Expected a float VEC3 accessor");
		ByteView view = view(root, accessor.bufferView());
		Vector3f[] values = new Vector3f[accessor.count()];
		int stride = view.strideOr(12);
		for (int index = 0; index < accessor.count(); index++) {
			int offset = view.byteOffset() + accessor.byteOffset() + index * stride;
			requireAvailable(bin, offset, 12);
			values[index] = new Vector3f(
				bin.getFloat(offset),
				bin.getFloat(offset + 4),
				bin.getFloat(offset + 8));
		}
		return values;
	}

	private static float[] readVec2Accessor(JsonObject root, ByteBuffer bin, int accessorIndex) {
		Accessor accessor = accessor(root, accessorIndex);
		require(accessor.componentType() == COMPONENT_FLOAT && "VEC2".equals(accessor.type()),
			"Expected a float VEC2 accessor");
		ByteView view = view(root, accessor.bufferView());
		float[] values = new float[accessor.count() * 2];
		int stride = view.strideOr(8);
		for (int index = 0; index < accessor.count(); index++) {
			int offset = view.byteOffset() + accessor.byteOffset() + index * stride;
			requireAvailable(bin, offset, 8);
			values[index * 2] = bin.getFloat(offset);
			values[index * 2 + 1] = bin.getFloat(offset + 4);
		}
		return values;
	}

	private static int[] readIndexAccessor(JsonObject root, ByteBuffer bin, int accessorIndex) {
		Accessor accessor = accessor(root, accessorIndex);
		require("SCALAR".equals(accessor.type()), "Expected a scalar index accessor");
		ByteView view = view(root, accessor.bufferView());
		int componentSize = componentSize(accessor.componentType());
		int stride = view.strideOr(componentSize);
		int[] values = new int[accessor.count()];
		for (int index = 0; index < accessor.count(); index++) {
			int offset = view.byteOffset() + accessor.byteOffset() + index * stride;
			requireAvailable(bin, offset, componentSize);
			values[index] = switch (accessor.componentType()) {
				case COMPONENT_UNSIGNED_BYTE -> Byte.toUnsignedInt(bin.get(offset));
				case COMPONENT_UNSIGNED_SHORT -> Short.toUnsignedInt(bin.getShort(offset));
				case COMPONENT_UNSIGNED_INT -> unsignedInt(bin.getInt(offset));
				default -> throw new IllegalArgumentException("Unsupported glTF index component type");
			};
		}
		return values;
	}

	private static Accessor accessor(JsonObject root, int index) {
		JsonArray accessors = array(root, "accessors");
		require(index >= 0 && index < accessors.size(), "glTF accessor index is out of range");
		JsonObject accessor = accessors.get(index).getAsJsonObject();
		require(!accessor.has("sparse"), "Sparse glTF accessors are not supported");
		return new Accessor(
			accessor.get("bufferView").getAsInt(),
			intOr(accessor, "byteOffset", 0),
			accessor.get("componentType").getAsInt(),
			accessor.get("count").getAsInt(),
			accessor.get("type").getAsString());
	}

	private static ByteView view(JsonObject root, int index) {
		JsonArray views = array(root, "bufferViews");
		require(index >= 0 && index < views.size(), "glTF bufferView index is out of range");
		JsonObject view = views.get(index).getAsJsonObject();
		return new ByteView(
			intOr(view, "byteOffset", 0),
			view.get("byteLength").getAsInt(),
			intOr(view, "byteStride", 0));
	}

	private static int componentSize(int componentType) {
		return switch (componentType) {
			case COMPONENT_UNSIGNED_BYTE -> 1;
			case COMPONENT_UNSIGNED_SHORT -> 2;
			case COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4;
			default -> throw new IllegalArgumentException("Unsupported glTF component type: " + componentType);
		};
	}

	private static int unsignedInt(int value) {
		long unsigned = Integer.toUnsignedLong(value);
		require(unsigned <= Integer.MAX_VALUE, "glTF index is too large for this renderer");
		return (int) unsigned;
	}

	private static Vector3f normal(Vector3fc a, Vector3fc b, Vector3fc c, Matrix4f transform) {
		Vector3f ta = transform.transformPosition(new Vector3f(a));
		Vector3f tb = transform.transformPosition(new Vector3f(b));
		Vector3f tc = transform.transformPosition(new Vector3f(c));
		Vector3f ab = tb.sub(ta);
		Vector3f ac = tc.sub(ta);
		Vector3f normal = ab.cross(ac);
		if (normal.lengthSquared() < 1.0E-8F) {
			return new Vector3f(0.0F, 1.0F, 0.0F);
		}
		return normal.normalize();
	}

	private static JsonArray array(JsonObject object, String key) {
		require(object.has(key) && object.get(key).isJsonArray(), "glTF JSON is missing array: " + key);
		return object.getAsJsonArray(key);
	}

	private static int intOr(JsonObject object, String key, int fallback) {
		return object.has(key) ? object.get(key).getAsInt() : fallback;
	}

	private static void requireAvailable(ByteBuffer buffer, int offset, int length) {
		require(offset >= 0 && length >= 0 && offset + length <= buffer.capacity(),
			"glTF binary accessor reads outside the BIN chunk");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	public record RenderedGltfModel(List<RenderedPrimitive> primitives) {
		public void getExtents(java.util.function.Consumer<Vector3fc> output) {
			for (RenderedPrimitive primitive : this.primitives) {
				for (Triangle triangle : primitive.triangles()) {
					output.accept(triangle.a().position());
					output.accept(triangle.b().position());
					output.accept(triangle.c().position());
				}
			}
		}
	}

	public record RenderedPrimitive(Identifier texture, List<Triangle> triangles) {
	}

	public record Vertex(Vector3f position, float u, float v, Vector3f normal) {
	}

	public record Triangle(Vertex a, Vertex b, Vertex c) {
	}

	private record NodeMesh(int meshIndex, Matrix4f transform) {
	}

	private record Accessor(int bufferView, int byteOffset, int componentType, int count, String type) {
	}

	private record ByteView(int byteOffset, int byteLength, int byteStride) {
		private int strideOr(int fallback) {
			return this.byteStride > 0 ? this.byteStride : fallback;
		}
	}
}
