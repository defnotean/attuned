package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AssetCustomizerContractTest {
	private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
	private static final Path CUSTOMIZER = Path.of("tools/asset_customizer");
	private static final Path MANIFEST = CUSTOMIZER.resolve("asset-manifest.json");
	private static final Path INDEX = CUSTOMIZER.resolve("index.html");
	private static final Path SCRIPT = CUSTOMIZER.resolve("asset-customizer.js");
	private static final Path STYLES = CUSTOMIZER.resolve("styles.css");
	private static final Path SERVER = CUSTOMIZER.resolve("serve.py");
	private static final Path OFFSHORE_ASSETS =
		Path.of("docs/superpowers/assets/offshore-harpoon");
	private static final Path HARPOON_FOCUS_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/harpoon_focus.png");
	private static final Path OFFSHORE_HARPOON_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/offshore_harpoon.png");
	private static final Path OCEAN_RELIC_TRIDENT_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident.png");
	private static final Path OCEAN_RELIC_TRIDENT_PALETTE =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_voxel_palette.png");
	private static final Path OCEAN_RELIC_SOURCE =
		Path.of("docs/superpowers/assets/ocean-relic-trident/Meshy_AI_Ocean_Relic_Trident_0602120856_image-to-3d-texture_obj/Meshy_AI_Ocean_Relic_Trident_0602120856_image-to-3d-texture.obj");
	private static final Path OCEAN_RELIC_VOXEL_REPORT =
		Path.of("docs/superpowers/assets/ocean-relic-trident/ocean_relic_trident_voxel_report.json");
	private static final Path FROSTBOUND_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/frostbound_trident.png");
	private static final Path FROSTBOUND_REPORT =
		Path.of("docs/superpowers/assets/frostbound-trident/frostbound_trident_report.json");
	private static final Path FROSTBOUND_WRAPPED_SOURCE =
		Path.of("docs/superpowers/assets/frostbound-trident/frostbound_trident_wrapped_concept_source.png");
	private static final Path FROSTBOUND_WRAPPED =
		Path.of("docs/superpowers/assets/frostbound-trident/frostbound_trident_wrapped.png");
	private static final Path FROSTBOUND_SHAPE_ANALYSIS =
		Path.of("docs/superpowers/assets/frostbound-trident/frostbound_trident_shape_analysis.json");

	@Test
	void assetCustomizerIsLaunchableInteractiveAndSelfContained() throws IOException {
		assertTrue(Files.isRegularFile(INDEX), "Asset customizer should have an HTML entry point");
		assertTrue(Files.isRegularFile(SCRIPT), "Asset customizer should have a renderer script");
		assertTrue(Files.isRegularFile(STYLES), "Asset customizer should have local styles");
		assertTrue(Files.isRegularFile(SERVER), "Asset customizer should have a launch script");

		String html = read(INDEX);
		String script = read(SCRIPT);
		String server = read(SERVER);

		assertTrue(html.contains("previewCanvas"), "Customizer should render to a canvas");
		assertTrue(html.contains("assetSelect"), "Customizer should let us switch assets");
		assertTrue(html.contains("viewMode"), "Customizer should offer multiple preview modes");
		assertTrue(html.contains("value=\"firstperson\""), "Customizer should offer first-person held previews");
		assertTrue(html.contains("value=\"thirdperson\""), "Customizer should offer third-person held previews");
		assertTrue(html.contains("copyTransform"), "Customizer should expose transform-copy workflow");
		assertTrue(script.contains("fetchJson(\"asset-manifest.json\")"),
			"Customizer should load the manifest at runtime");
		assertTrue(script.contains("applyPresetForView"),
			"Customizer should map preview modes to Minecraft display presets");
		assertTrue(script.contains("firstperson_righthand"),
			"Customizer should use the same first-person transform key as Minecraft item models");
		assertTrue(script.contains("thirdperson_righthand"),
			"Customizer should use the same third-person transform key as Minecraft item models");
		assertTrue(script.contains("drawReadableGeneratedItem"),
			"Customizer should keep generated weapon sprites readable in held previews");
		assertTrue(script.contains("drawHeldItemScene"),
			"Customizer should frame held previews in first/third-person scenes");
		assertTrue(script.contains("drop-shadow(0 0 10px"),
			"Customizer should use a subtle thickness/glow cue instead of noisy per-pixel slabs");
		assertTrue(script.contains("drawBlockModel"),
			"Customizer should still support cuboid item models for future assets");
		assertTrue(script.contains("hasBlockElements()"),
			"Customizer should detect Blockbench-style cuboid item models");
		assertTrue(script.contains("faceShade"),
			"Customizer should give cuboid previews readable face shading");
		assertTrue(script.contains("togglePlay"),
			"Customizer should scrub/play animated Focus textures");
		assertTrue(script.contains("exportPng"),
			"Customizer should export preview screenshots");
		assertTrue(server.contains("ThreadingHTTPServer"),
			"Customizer launcher should serve through localhost without external dependencies");
		assertTrue(server.contains("tools/asset_customizer"),
			"Customizer launcher should open the customizer route");
	}

	@Test
	void manifestPointsAtRealAttunedAssets() throws IOException {
		JsonArray assets = JsonParser.parseString(read(MANIFEST)).getAsJsonArray();
		assertEquals(5, assets.size(), "Customizer should include Offshore assets, throw preview, and the Meshy conversion samples");

		boolean sawFocus = false;
		boolean sawHarpoon = false;
		boolean sawOceanRelic = false;
		boolean sawOceanRelicThrowing = false;
		boolean sawFrostbound = false;
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			String id = asset.get("id").getAsString();
			sawFocus |= "harpoon_focus".equals(id);
			sawHarpoon |= "offshore_harpoon".equals(id);
			sawOceanRelic |= "ocean_relic_trident".equals(id);
			sawOceanRelicThrowing |= "ocean_relic_trident_throwing".equals(id);
			sawFrostbound |= "frostbound_trident".equals(id);
			assertRelativeAssetExists(asset, "model");
			assertRelativeAssetExists(asset, "texture");
			assertRelativeAssetExists(asset, "definition");
			if (asset.has("data")) {
				assertRelativeAssetExists(asset, "data");
			}
			if (asset.has("source")) {
				assertRelativeAssetExists(asset, "source");
			}
			if (asset.has("sprite")) {
				assertRelativeAssetExists(asset, "sprite");
			}
		}
		assertTrue(sawFocus, "Customizer manifest should include Harpoon Focus");
		assertTrue(sawHarpoon, "Customizer manifest should include Offshore Harpoon");
		assertTrue(sawOceanRelic, "Customizer manifest should include the Ocean Relic Trident source model");
		assertTrue(sawOceanRelicThrowing, "Customizer manifest should include the Ocean Relic Trident throwing pose");
		assertTrue(sawFrostbound, "Customizer manifest should include the Meshy Frostbound Trident conversion");
	}

	@Test
	void offshoreArtKeepsPolishedSources() throws IOException {
		assertTrue(Files.isRegularFile(OFFSHORE_ASSETS.resolve("harpoon-focus-concept-sheet.png")),
			"Harpoon Focus should keep its polished sheet source");
		assertTrue(Files.isRegularFile(OFFSHORE_ASSETS.resolve("offshore-harpoon-concept-source.png")),
			"Offshore Harpoon should keep its polished source");
		assertTrue(!Files.exists(Path.of("tools/generate_offshore_assets.py")),
			"Offshore assets should not use the old hand-drawn PIL generator");
	}

	@Test
	void generatedOffshoreTexturesAreTransparentReadableAndAnimated() throws IOException {
		BufferedImage focus = ImageIO.read(HARPOON_FOCUS_TEXTURE.toFile());
		BufferedImage harpoon = ImageIO.read(OFFSHORE_HARPOON_TEXTURE.toFile());
		assertNotNull(focus, "Harpoon Focus texture should decode");
		assertNotNull(harpoon, "Offshore Harpoon texture should decode");

		assertEquals(64, focus.getWidth(), "Harpoon Focus should use standard Focus width");
		assertEquals(512, focus.getHeight(), "Harpoon Focus should keep eight frames");
		assertEquals(64, harpoon.getWidth(), "Offshore Harpoon should be a 64px item sprite");
		assertEquals(64, harpoon.getHeight(), "Offshore Harpoon should be a 64px item sprite");

		assertFrameAnimation(focus);
		assertTransparentCorners(frame(focus, 0));
		assertTransparentCorners(harpoon);
		assertNoVisibleChromaKey(focus);
		assertNoVisibleChromaKey(harpoon);
		assertVisibleFootprint(frame(focus, 0), 50, 55);
		assertVisibleFootprint(harpoon, 44, 56);
	}

	@Test
	void meshyFbxConversionProducesMinecraftItemSpriteAndReusableReport() throws IOException {
		Path converter = Path.of("tools/mesh_to_minecraft_item.py");
		assertTrue(Files.isRegularFile(converter), "Mesh conversion pipeline should be reusable");
		String source = read(converter);
		assertTrue(source.contains("bpy.ops.import_scene.fbx"),
			"Mesh conversion pipeline should import FBX exports");
		assertTrue(source.contains("bpy.ops.export_scene.gltf"),
			"Mesh conversion pipeline should preserve a normalized GLB source");
		assertTrue(source.contains("\"parent\": \"minecraft:item/generated\""),
			"Mesh conversion pipeline should emit Minecraft generated item models");

		assertTrue(Files.isRegularFile(OCEAN_RELIC_SOURCE),
			"Ocean Relic Trident should keep its Meshy OBJ source");
		assertTrue(Files.isRegularFile(OCEAN_RELIC_TRIDENT_PALETTE),
			"Ocean Relic Trident should ship a palette for its voxel cuboid model");
		assertTrue(Files.isRegularFile(OCEAN_RELIC_VOXEL_REPORT),
			"Ocean Relic Trident should keep a voxelization report");

		BufferedImage texture = ImageIO.read(FROSTBOUND_TEXTURE.toFile());
		BufferedImage oceanRelic = ImageIO.read(OCEAN_RELIC_TRIDENT_TEXTURE.toFile());
		BufferedImage oceanPalette = ImageIO.read(OCEAN_RELIC_TRIDENT_PALETTE.toFile());
		assertNotNull(texture, "Frostbound Trident texture should decode");
		assertNotNull(oceanRelic, "Ocean Relic Trident texture should decode");
		assertNotNull(oceanPalette, "Ocean Relic Trident palette should decode");
		assertEquals(64, texture.getWidth(), "Frostbound Trident should be a 64px item sprite");
		assertEquals(64, texture.getHeight(), "Frostbound Trident should be a 64px item sprite");
		assertEquals(64, oceanRelic.getWidth(), "Ocean Relic Trident should be a 64px item sprite");
		assertEquals(64, oceanRelic.getHeight(), "Ocean Relic Trident should be a 64px item sprite");
		assertEquals(16, oceanPalette.getWidth(), "Ocean Relic Trident palette should be compact");
		assertEquals(16, oceanPalette.getHeight(), "Ocean Relic Trident palette should be compact");
		assertTransparentCorners(texture);
		assertTransparentCorners(oceanRelic);
		assertVisibleFootprint(texture, 52, 32);
		assertVisibleFootprint(oceanRelic, 52, 32);

		JsonObject report = JsonParser.parseString(read(FROSTBOUND_REPORT)).getAsJsonObject();
		assertEquals(1, report.get("mesh_count").getAsInt(),
			"Meshy source should import as one source mesh");
		assertTrue(report.get("face_count").getAsInt() > 1000,
			"Meshy report should preserve source complexity diagnostics");
		assertTrue(report.get("normalized_material_count").getAsInt() >= 1,
			"Material-less Meshy sources should receive a fallback material before render/export");
	}

	@Test
	void generatedWeaponTransformsStayReadableInsteadOfEdgeOn() throws IOException {
		assertReadableGeneratedWeaponTransforms(
			Path.of("src/main/resources/assets/attuned/models/item/frostbound_trident.json"));
		assertReadableGeneratedWeaponTransforms(
			Path.of("src/main/resources/assets/attuned/models/item/offshore_harpoon.json"));
	}

	@Test
	void oceanRelicTridentUsesVoxelCuboidModel() throws IOException {
		Path modelPath = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident.json");
		Path throwingModelPath = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json");
		JsonObject model = JsonParser.parseString(read(modelPath)).getAsJsonObject();
		JsonObject throwingModel = JsonParser.parseString(read(throwingModelPath)).getAsJsonObject();
		JsonObject report = JsonParser.parseString(read(OCEAN_RELIC_VOXEL_REPORT)).getAsJsonObject();

		assertTrue(!model.has("parent"), "Voxel item model should not inherit flat generated rendering");
		assertEquals("attuned:item/ocean_relic_trident_voxel_palette",
			model.getAsJsonObject("textures").get("palette").getAsString(),
			"Voxel model should use the generated palette texture");
		JsonArray elements = model.getAsJsonArray("elements");
		assertTrue(elements.size() >= 36 && elements.size() <= 96,
			"Voxel trident should use a readable held silhouette instead of a noisy high-cuboid icon");
		assertEquals(elements.size(), report.get("cuboids").getAsInt(),
			"Voxel report should match the shipped cuboid count");
		assertEquals(elements.size(), throwingModel.getAsJsonArray("elements").size(),
			"Throwing model should reuse the same readable trident geometry");
		JsonObject heldDisplay = model.getAsJsonObject("display")
			.getAsJsonObject("thirdperson_righthand");
		assertEquals(60, heldDisplay.getAsJsonArray("rotation").get(1).getAsInt(),
			"Held third-person pose should use the vanilla trident hand angle so the grip stays at the wrist");
		assertTrue(heldDisplay.getAsJsonArray("translation").get(0).getAsDouble() >= 8.0D,
			"Held third-person pose should sit in the right hand instead of behind the player");
		assertTrue(heldDisplay.getAsJsonArray("translation").get(1).getAsDouble() >= 12.0D,
			"Held third-person pose should be raised to the hand anchor");
		assertTrue(heldDisplay.getAsJsonArray("translation").get(2).getAsDouble() <= 0.0D,
			"Held third-person pose should not float behind the shoulder");
		JsonObject throwingDisplay = throwingModel.getAsJsonObject("display")
			.getAsJsonObject("thirdperson_righthand");
		assertEquals(90, throwingDisplay.getAsJsonArray("rotation").get(1).getAsInt(),
			"Throwing pose should rotate the trident so the prongs point forward during wind-up");
		assertEquals(180, throwingDisplay.getAsJsonArray("rotation").get(2).getAsInt(),
			"Throwing pose should flip the cuboid trident so the prongs face forward during wind-up");
		assertTrue(throwingDisplay.getAsJsonArray("scale").get(0).getAsDouble() < 0.9D,
			"Throwing pose should stay compact enough to avoid clipping through the camera");
		assertEquals("curated_trident_silhouette_from_concept_palette",
			report.get("strategy").getAsString(),
			"Voxel report should document why the held model is curated from the concept palette");
		JsonArray bbox = report.getAsJsonArray("bbox_size");
		assertTrue(bbox.get(0).getAsDouble() >= 10.0D,
			"Held trident should have visibly separated side prongs");
		assertTrue(bbox.get(1).getAsDouble() >= 40.0D,
			"Held trident should be long enough to read as a trident in third person");
		assertTrue(bbox.get(2).getAsDouble() >= 3.0D,
			"Voxel trident should have enough depth to avoid looking like a flat sprite");
		assertTrue(read(Path.of("tools/build_ocean_relic_trident_model.py")).contains("build_elements"),
			"Voxel item pipeline should be reusable instead of hand-edited JSON only");
	}

	@Test
	void frostboundWrapperKeepsShapeAnalysisAndConceptSource() throws IOException {
		assertTrue(Files.isRegularFile(FROSTBOUND_WRAPPED_SOURCE),
			"Frostbound wrapper should keep its concept source");
		assertTrue(Files.isRegularFile(FROSTBOUND_WRAPPED),
			"Frostbound wrapper should keep the cleaned transparent source");
		assertTrue(Files.isRegularFile(FROSTBOUND_SHAPE_ANALYSIS),
			"Frostbound wrapper should keep its measured shape analysis");

		BufferedImage wrapped = ImageIO.read(FROSTBOUND_WRAPPED.toFile());
		assertNotNull(wrapped, "Cleaned wrapper source should decode");
		assertTransparentCorners(wrapped);
		assertNoVisibleChromaKey(wrapped);

		JsonObject analysis = JsonParser.parseString(read(FROSTBOUND_SHAPE_ANALYSIS)).getAsJsonObject();
		assertEquals("src/main/resources/assets/attuned/textures/item/frostbound_trident.png",
			analysis.get("minecraft_texture").getAsString(),
			"Shape analysis should point at the shipped Minecraft texture");
		double majorAxis = analysis.get("major_axis_degrees").getAsDouble();
		assertTrue(majorAxis >= -55.0D && majorAxis <= -40.0D,
			"Wrapped art should preserve the supplied trident's lower-left to upper-right diagonal axis");
		JsonArray bboxSize = analysis.getAsJsonArray("bbox_size");
		assertTrue(bboxSize.get(1).getAsInt() > bboxSize.get(0).getAsInt(),
			"Wrapped art should preserve a tall diagonal trident silhouette");
	}

	private static void assertRelativeAssetExists(JsonObject asset, String key) {
		Path path = CUSTOMIZER.resolve(asset.get(key).getAsString()).normalize();
		assertTrue(path.toAbsolutePath().normalize().startsWith(ROOT),
			"Customizer asset path should stay inside the repo: " + path);
		assertTrue(Files.isRegularFile(path),
			"Customizer manifest should point at an existing " + key + ": " + path);
	}

	private static void assertFrameAnimation(BufferedImage image) {
		for (int frame = 1; frame < 8; frame++) {
			int changed = 0;
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					if (image.getRGB(x, y) != image.getRGB(x, frame * 64 + y)) {
						changed++;
					}
				}
			}
			assertTrue(changed >= 16, "Every Harpoon Focus frame should visibly differ");
		}
	}

	private static BufferedImage frame(BufferedImage strip, int frame) {
		return strip.getSubimage(0, frame * 64, 64, 64);
	}

	private static void assertTransparentCorners(BufferedImage image) {
		assertEquals(0, alpha(image, 0, 0), "Generated asset should have a transparent corner");
		assertEquals(0, alpha(image, image.getWidth() - 1, 0), "Generated asset should have a transparent corner");
		assertEquals(0, alpha(image, 0, image.getHeight() - 1), "Generated asset should have a transparent corner");
		assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1),
			"Generated asset should have a transparent corner");
	}

	private static void assertNoVisibleChromaKey(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				int red = (argb >>> 16) & 0xFF;
				int green = (argb >>> 8) & 0xFF;
				int blue = argb & 0xFF;
				assertTrue(alpha <= 16 || green <= 150 || red >= 90 || blue >= 120,
					"Generated asset should not contain visible chroma-key pixels");
			}
		}
	}

	private static void assertVisibleFootprint(BufferedImage image, int minWidth, int minHeight) {
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (alpha(image, x, y) > 24) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		assertTrue(maxX - minX + 1 >= minWidth, "Generated asset should fill enough icon width");
		assertTrue(maxY - minY + 1 >= minHeight, "Generated asset should fill enough icon height");
	}

	private static void assertReadableGeneratedWeaponTransforms(Path modelPath) throws IOException {
		JsonObject model = JsonParser.parseString(read(modelPath)).getAsJsonObject();
		assertEquals("minecraft:item/generated", model.get("parent").getAsString(),
			"Generated weapon preview contract only applies to generated item sprites: " + modelPath);
		JsonObject display = model.getAsJsonObject("display");
		assertReadableDisplay(display.getAsJsonObject("firstperson_righthand"), modelPath + " firstperson_righthand");
		assertReadableDisplay(display.getAsJsonObject("thirdperson_righthand"), modelPath + " thirdperson_righthand");
	}

	private static void assertReadableDisplay(JsonObject display, String label) {
		JsonArray rotation = display.getAsJsonArray("rotation");
		assertEquals(0, rotation.get(1).getAsInt(),
			"Generated weapon should not rotate edge-on around Y in held views: " + label);
		JsonArray scale = display.getAsJsonArray("scale");
		assertTrue(scale.get(0).getAsDouble() >= 0.7D,
			"Generated weapon should stay large enough to read in held views: " + label);
	}

	private static int alpha(BufferedImage image, int x, int y) {
		return (image.getRGB(x, y) >>> 24) & 0xFF;
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
