package dev.attuned.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.attuned.Attuned;
import java.io.ByteArrayInputStream;
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
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Owned GLB v2 parser plus client resource-reload listener; shares the rendered
 * mesh across receivers. Ported from the Minecraft 26.2 reference renderer to the
 * legacy 1.20.6 client API: {@link ResourceLocation}, the single-argument
 * {@link DynamicTexture} constructor, {@link NativeImage#read(InputStream)}, and
 * the synchronous {@link SimpleSynchronousResourceReloadListener} contract. The
 * special-model SPI ({@code SpecialModelRenderers}/{@code minecraft:special}) is
 * absent on this generation, so the mesh is rendered through Fabric's
 * {@code BuiltinItemRendererRegistry} instead (see {@link GltfMeshSpecialRenderer}).
 *
 * <p>Relative to the 1.18.2 sibling port this generation reads resources through
 * {@link Resource#open()} and {@link ResourceManager#getResourceOrThrow} instead of
 * the removed {@code Resource.getInputStream()}/single-result {@code getResource}.
 */
public final class AttunedGltfModels implements SimpleSynchronousResourceReloadListener {
	private static final AttunedGltfModels INSTANCE = new AttunedGltfModels();
	private static final ResourceLocation RELOAD_ID =
		new ResourceLocation(Attuned.MOD_ID, "gltf_models");
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
	private final Map<ResourceLocation, RenderedGltfModel> renderedModels = new HashMap<>();
	private final List<ResourceLocation> dynamicTextures = new ArrayList<>();
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

	public Optional<RenderedGltfModel> getOrLoad(ResourceLocation modelLocation) {
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
	public ResourceLocation getFabricId() {
		return RELOAD_ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		Set<ResourceLocation> modelLocations = receiverModelLocations();
		Map<ResourceLocation, RenderedGltfModel> loaded = loadAll(resourceManager, modelLocations);
		clearDynamicTextures();
		synchronized (this) {
			this.renderedModels.clear();
			this.renderedModels.putAll(loaded);
			for (Map.Entry<ResourceLocation, RenderedGltfModel> entry : loaded.entrySet()) {
				notifyReceivers(entry.getKey(), entry.getValue());
			}
		}
	}

	private synchronized Set<ResourceLocation> receiverModelLocations() {
		Set<ResourceLocation> locations = new LinkedHashSet<>();
		for (GltfModelReceiver receiver : this.receivers) {
			locations.add(receiver.getModelLocation());
		}
		return locations;
	}

	private Map<ResourceLocation, RenderedGltfModel> loadAll(ResourceManager resourceManager,
			Set<ResourceLocation> modelLocations) {
		Map<ResourceLocation, RenderedGltfModel> loaded = new HashMap<>();
		for (ResourceLocation modelLocation : modelLocations) {
			try {
				loaded.put(modelLocation, load(resourceManager, modelLocation));
			} catch (IOException | IllegalArgumentException | IllegalStateException ex) {
				Attuned.LOGGER.warn("Unable to load glTF model {} during resource reload", modelLocation, ex);
			}
		}
		return loaded;
	}

	private void notifyReceivers(ResourceLocation modelLocation, RenderedGltfModel renderedModel) {
		for (GltfModelReceiver receiver : this.receivers) {
			if (modelLocation.equals(receiver.getModelLocation()) && receiver.isReceiveSharedModel(renderedModel)) {
				receiver.onReceiveSharedModel(renderedModel);
			}
		}
	}

	private void clearDynamicTextures() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		for (ResourceLocation texture : this.dynamicTextures) {
			textureManager.release(texture);
		}
		this.dynamicTextures.clear();
	}

	private RenderedGltfModel load(ResourceManager resourceManager, ResourceLocation modelLocation) throws IOException {
		Resource resource = resourceManager.getResourceOrThrow(modelLocation);
		try (InputStream input = resource.open()) {
			return readGlb(modelLocation, input.readAllBytes());
		}
	}

	private RenderedGltfModel readGlb(ResourceLocation modelLocation, byte[] bytes) {
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

	private RenderedGltfModel readScene(ResourceLocation modelLocation, JsonObject root, ByteBuffer bin) {
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
			collectSceneMeshes(nodes, nodeIndex.getAsInt(), Mat4.identity(), meshIndices);
		}
		require(!meshIndices.isEmpty(), "glTF scene does not reference any meshes");
		return meshIndices;
	}

	private static void collectSceneMeshes(JsonArray nodes, int nodeIndex,
			Mat4 parentTransform, Set<NodeMesh> meshIndices) {
		require(nodeIndex >= 0 && nodeIndex < nodes.size(), "glTF node index is out of range");
		JsonObject node = nodes.get(nodeIndex).getAsJsonObject();
		Mat4 transform = parentTransform.multiply(nodeTransform(node));
		if (node.has("mesh")) {
			meshIndices.add(new NodeMesh(node.get("mesh").getAsInt(), transform));
		}
		if (node.has("children")) {
			for (JsonElement child : node.getAsJsonArray("children")) {
				collectSceneMeshes(nodes, child.getAsInt(), transform, meshIndices);
			}
		}
	}

	private static Mat4 nodeTransform(JsonObject node) {
		if (node.has("matrix")) {
			JsonArray matrix = node.getAsJsonArray("matrix");
			require(matrix.size() == 16, "glTF node matrix must contain 16 values");
			float[] columnMajor = new float[16];
			for (int index = 0; index < 16; index++) {
				columnMajor[index] = matrix.get(index).getAsFloat();
			}
			return Mat4.fromColumnMajor(columnMajor);
		}

		Mat4 transform = Mat4.identity();
		if (node.has("translation")) {
			JsonArray translation = node.getAsJsonArray("translation");
			transform = transform.multiply(Mat4.translation(translation.get(0).getAsFloat(),
				translation.get(1).getAsFloat(), translation.get(2).getAsFloat()));
		}
		if (node.has("rotation")) {
			JsonArray rotation = node.getAsJsonArray("rotation");
			transform = transform.multiply(Mat4.quaternion(rotation.get(0).getAsFloat(),
				rotation.get(1).getAsFloat(), rotation.get(2).getAsFloat(), rotation.get(3).getAsFloat()));
		}
		if (node.has("scale")) {
			JsonArray scale = node.getAsJsonArray("scale");
			transform = transform.multiply(Mat4.scale(scale.get(0).getAsFloat(),
				scale.get(1).getAsFloat(), scale.get(2).getAsFloat()));
		}
		return transform;
	}

	private RenderedPrimitive readPrimitive(ResourceLocation modelLocation, JsonObject root, ByteBuffer bin,
			JsonObject primitive, Mat4 transform) {
		require(intOr(primitive, "mode", MODE_TRIANGLES) == MODE_TRIANGLES,
			"Only glTF TRIANGLES primitives are supported");
		JsonObject attributes = primitive.getAsJsonObject("attributes");
		require(attributes != null && attributes.has("POSITION") && attributes.has("TEXCOORD_0"),
			"glTF primitive must contain POSITION and TEXCOORD_0 attributes");
		require(primitive.has("indices"), "glTF primitive must be indexed");

		float[][] positions = readVec3Accessor(root, bin, attributes.get("POSITION").getAsInt());
		float[][] normals = attributes.has("NORMAL")
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
			float[] faceNormal = normals == null
				? normal(positions[aIndex], positions[bIndex], positions[cIndex], transform)
				: null;
			triangles.add(new Triangle(
				vertex(positions, normals, uvs, aIndex, faceNormal, transform),
				vertex(positions, normals, uvs, bIndex, faceNormal, transform),
				vertex(positions, normals, uvs, cIndex, faceNormal, transform)));
		}
		ResourceLocation texture = primitiveTexture(modelLocation, root, bin, primitive)
			.orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
		return new RenderedPrimitive(texture, List.copyOf(triangles));
	}

	private Optional<ResourceLocation> primitiveTexture(ResourceLocation modelLocation, JsonObject root,
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

	private Optional<ResourceLocation> textureImage(ResourceLocation modelLocation, JsonObject root,
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

	private ResourceLocation registerEmbeddedTexture(ResourceLocation modelLocation, JsonObject root,
			ByteBuffer bin, int bufferViewIndex, int imageIndex) {
		ByteView view = view(root, bufferViewIndex);
		requireAvailable(bin, view.byteOffset(), view.byteLength());
		byte[] imageBytes = new byte[view.byteLength()];
		ByteBuffer source = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		source.position(view.byteOffset());
		source.get(imageBytes);
		return registerImageBytes(dynamicTextureId(modelLocation, imageIndex), imageBytes);
	}

	private Optional<ResourceLocation> imageUriTexture(ResourceLocation modelLocation, String uri, int imageIndex) {
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
		return Optional.of(new ResourceLocation(modelLocation.getNamespace(), prefix + uri));
	}

	private ResourceLocation registerImageBytes(ResourceLocation textureId, byte[] bytes) {
		try (InputStream input = new ByteArrayInputStream(bytes)) {
			NativeImage image = NativeImage.read(input);
			Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(image));
			synchronized (this) {
				this.dynamicTextures.add(textureId);
			}
			return textureId;
		} catch (IOException ex) {
			Attuned.LOGGER.warn("Unable to decode embedded glTF texture {}", textureId, ex);
			return TextureManager.INTENTIONAL_MISSING_TEXTURE;
		}
	}

	private static ResourceLocation dynamicTextureId(ResourceLocation modelLocation, int imageIndex) {
		String path = "gltf_dynamic/" + modelLocation.getNamespace() + "/"
			+ modelLocation.getPath().replace('.', '_') + "/image_" + imageIndex;
		return new ResourceLocation(Attuned.MOD_ID, path);
	}

	private static Vertex vertex(float[][] positions, float[][] normals, float[] uvs,
			int index, float[] faceNormal, Mat4 transform) {
		require(index >= 0 && index < positions.length, "glTF index is outside POSITION accessor");
		require(index * 2 + 1 < uvs.length, "glTF index is outside TEXCOORD_0 accessor");
		float[] position = transform.transformPosition(positions[index]);
		float[] normal = normals != null && index < normals.length
			? transform.transformDirection(normals[index])
			: new float[] {faceNormal[0], faceNormal[1], faceNormal[2]};
		float lengthSquared = normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2];
		if (lengthSquared <= 1.0E-8F) {
			normal = new float[] {0.0F, 1.0F, 0.0F};
		} else {
			float inverse = (float) (1.0 / Math.sqrt(lengthSquared));
			normal = new float[] {normal[0] * inverse, normal[1] * inverse, normal[2] * inverse};
		}
		return new Vertex(position[0], position[1], position[2],
			uvs[index * 2], uvs[index * 2 + 1], normal[0], normal[1], normal[2]);
	}

	private static float[][] readVec3Accessor(JsonObject root, ByteBuffer bin, int accessorIndex) {
		Accessor accessor = accessor(root, accessorIndex);
		require(accessor.componentType() == COMPONENT_FLOAT && "VEC3".equals(accessor.type()),
			"Expected a float VEC3 accessor");
		ByteView view = view(root, accessor.bufferView());
		float[][] values = new float[accessor.count()][];
		int stride = view.strideOr(12);
		for (int index = 0; index < accessor.count(); index++) {
			int offset = view.byteOffset() + accessor.byteOffset() + index * stride;
			requireAvailable(bin, offset, 12);
			values[index] = new float[] {
				bin.getFloat(offset),
				bin.getFloat(offset + 4),
				bin.getFloat(offset + 8)};
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

	private static float[] normal(float[] a, float[] b, float[] c, Mat4 transform) {
		float[] ta = transform.transformPosition(a);
		float[] tb = transform.transformPosition(b);
		float[] tc = transform.transformPosition(c);
		float abx = tb[0] - ta[0];
		float aby = tb[1] - ta[1];
		float abz = tb[2] - ta[2];
		float acx = tc[0] - ta[0];
		float acy = tc[1] - ta[1];
		float acz = tc[2] - ta[2];
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
	}

	public record RenderedPrimitive(ResourceLocation texture, List<Triangle> triangles) {
	}

	public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
	}

	public record Triangle(Vertex a, Vertex b, Vertex c) {
	}

	private record NodeMesh(int meshIndex, Mat4 transform) {
	}

	private record Accessor(int bufferView, int byteOffset, int componentType, int count, String type) {
	}

	private record ByteView(int byteOffset, int byteLength, int byteStride) {
		private int strideOr(int fallback) {
			return this.byteStride > 0 ? this.byteStride : fallback;
		}
	}

	/**
	 * Minimal column-major 4x4 transform used while baking scene-node transforms.
	 * The parser keeps its own tiny matrix instead of depending on the Mojang/JOML
	 * math types (those are only used at the {@code VertexConsumer} boundary).
	 */
	private static final class Mat4 {
		// Column-major storage: m[column * 4 + row].
		private final float[] m;

		private Mat4(float[] m) {
			this.m = m;
		}

		private static Mat4 identity() {
			float[] m = new float[16];
			m[0] = 1.0F;
			m[5] = 1.0F;
			m[10] = 1.0F;
			m[15] = 1.0F;
			return new Mat4(m);
		}

		private static Mat4 fromColumnMajor(float[] columnMajor) {
			return new Mat4(columnMajor.clone());
		}

		private static Mat4 translation(float x, float y, float z) {
			Mat4 result = identity();
			result.m[12] = x;
			result.m[13] = y;
			result.m[14] = z;
			return result;
		}

		private static Mat4 scale(float x, float y, float z) {
			Mat4 result = identity();
			result.m[0] = x;
			result.m[5] = y;
			result.m[10] = z;
			return result;
		}

		private static Mat4 quaternion(float x, float y, float z, float w) {
			float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
			if (length > 1.0E-8F) {
				float inverse = 1.0F / length;
				x *= inverse;
				y *= inverse;
				z *= inverse;
				w *= inverse;
			}
			float xx = x * x;
			float yy = y * y;
			float zz = z * z;
			float xy = x * y;
			float xz = x * z;
			float yz = y * z;
			float wx = w * x;
			float wy = w * y;
			float wz = w * z;
			float[] m = new float[16];
			m[0] = 1.0F - 2.0F * (yy + zz);
			m[1] = 2.0F * (xy + wz);
			m[2] = 2.0F * (xz - wy);
			m[4] = 2.0F * (xy - wz);
			m[5] = 1.0F - 2.0F * (xx + zz);
			m[6] = 2.0F * (yz + wx);
			m[8] = 2.0F * (xz + wy);
			m[9] = 2.0F * (yz - wx);
			m[10] = 1.0F - 2.0F * (xx + yy);
			m[15] = 1.0F;
			return new Mat4(m);
		}

		private Mat4 multiply(Mat4 other) {
			float[] result = new float[16];
			for (int column = 0; column < 4; column++) {
				for (int row = 0; row < 4; row++) {
					float sum = 0.0F;
					for (int k = 0; k < 4; k++) {
						sum += this.m[k * 4 + row] * other.m[column * 4 + k];
					}
					result[column * 4 + row] = sum;
				}
			}
			return new Mat4(result);
		}

		private float[] transformPosition(float[] position) {
			float x = position[0];
			float y = position[1];
			float z = position[2];
			return new float[] {
				this.m[0] * x + this.m[4] * y + this.m[8] * z + this.m[12],
				this.m[1] * x + this.m[5] * y + this.m[9] * z + this.m[13],
				this.m[2] * x + this.m[6] * y + this.m[10] * z + this.m[14]};
		}

		private float[] transformDirection(float[] direction) {
			float x = direction[0];
			float y = direction[1];
			float z = direction[2];
			return new float[] {
				this.m[0] * x + this.m[4] * y + this.m[8] * z,
				this.m[1] * x + this.m[5] * y + this.m[9] * z,
				this.m[2] * x + this.m[6] * y + this.m[10] * z};
		}
	}
}
