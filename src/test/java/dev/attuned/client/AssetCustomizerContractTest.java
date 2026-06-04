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
	private static final Path OCEAN_RELIC_TRIDENT_INVENTORY_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_inventory.png");
	private static final Path OCEAN_RELIC_TRIDENT_PALETTE =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_voxel_palette.png");
	private static final Path OCEAN_RELIC_TRIDENT_INVENTORY_MODEL =
		Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident_inventory.json");
	private static final Path OCEAN_RELIC_TRIDENT_ITEM_DEFINITION =
		Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident.json");
	private static final Path OCEAN_RELIC_TRIDENT_PROJECTILE_DEFINITION =
		Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident_projectile.json");
	private static final Path CLIENT_MIXIN_CONFIG = Path.of("src/client/resources/attuned.client.mixins.json");
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
		assertTrue(!Files.exists(OCEAN_RELIC_TRIDENT_INVENTORY_TEXTURE),
			"Ocean Relic inventory should reuse the richer existing trident sprite instead of shipping a separate inventory icon");
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
		JsonObject inventoryModel = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_INVENTORY_MODEL)).getAsJsonObject();
		JsonObject itemDefinition = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_ITEM_DEFINITION)).getAsJsonObject();
		JsonObject projectileDefinition = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_PROJECTILE_DEFINITION)).getAsJsonObject();
		JsonObject report = JsonParser.parseString(read(OCEAN_RELIC_VOXEL_REPORT)).getAsJsonObject();

		assertTrue(!model.has("parent"), "Voxel item model should not inherit flat generated rendering");
		assertEquals("attuned:item/ocean_relic_trident_voxel_palette",
			model.getAsJsonObject("textures").get("palette").getAsString(),
			"Voxel model should use the generated palette texture");
		assertEquals("minecraft:item/generated", inventoryModel.get("parent").getAsString(),
			"Inventory model should use a flat item sprite instead of the bulky cuboid model");
		assertEquals("attuned:item/ocean_relic_trident",
			inventoryModel.getAsJsonObject("textures").get("layer0").getAsString(),
			"Inventory model should reuse the richer existing flat trident sprite instead of the separate inventory icon");
		String itemDefinitionText = itemDefinition.toString();
		assertTrue(itemDefinitionText.contains("minecraft:display_context"),
			"Item definition should select a separate GUI model by display context");
		assertTrue(itemDefinitionText.contains("ocean_relic_trident_inventory"),
			"GUI display context should use the flat inventory icon");
		assertTrue(itemDefinitionText.contains("ground") && itemDefinitionText.contains("fixed"),
			"Inventory-style display contexts should use the flat icon instead of the bulky held cuboid model");
		assertEquals("attuned:item/ocean_relic_trident_throwing",
			projectileDefinition.getAsJsonObject("model").get("model").getAsString(),
			"Thrown harpoon renderer should resolve a custom projectile model instead of vanilla trident art");
		JsonArray elements = model.getAsJsonArray("elements");
		assertCuboidCoordinatesInMinecraftBounds(modelPath, elements);
		assertCuboidCoordinatesInMinecraftBounds(throwingModelPath, throwingModel.getAsJsonArray("elements"));
		assertTrue(elements.size() >= 36 && elements.size() <= 96,
			"Voxel trident should use a readable Minecraft-style silhouette without exceeding the compact cuboid budget");
		assertEquals(elements.size(), report.get("cuboids").getAsInt(),
			"Voxel report should match the shipped cuboid count");
		assertEquals(elements.size(), throwingModel.getAsJsonArray("elements").size(),
			"Throwing model should reuse the same readable trident geometry");
		JsonObject display = model.getAsJsonObject("display");
		JsonObject heldDisplay = display.getAsJsonObject("thirdperson_righthand");
		assertEquals(60, heldDisplay.getAsJsonArray("rotation").get(1).getAsInt(),
			"Held third-person pose should keep the vanilla trident hand angle");
		assertTranslationBetween(heldDisplay, 0, -0.75D, 0.25D,
			"Held third-person X should tuck the grip inward to the player's visible hand instead of sitting outside the arm");
		assertTranslationBetween(heldDisplay, 1, 0.75D, 2.25D,
			"Held third-person Y should sit at the player's gripped hand instead of riding up by the shoulder/head");
		assertTranslationBetween(heldDisplay, 2, -3.25D, -1.75D,
			"Held third-person Z should pull the grip out to the hand plane instead of clipping through the torso");
		assertTranslationBetween(display.getAsJsonObject("firstperson_righthand"), 1, 5.0D, 7.0D,
			"Held first-person Y should pull the long cuboid grip down into the hand");
		assertScaleBetween(heldDisplay, 0.5D, 0.6D,
			"Held third-person scale should resize the cuboid trident to a player-hand readable size");
		JsonObject throwingDisplay = throwingModel.getAsJsonObject("display")
			.getAsJsonObject("thirdperson_righthand");
		assertEquals(90, throwingDisplay.getAsJsonArray("rotation").get(1).getAsInt(),
			"Throwing pose should rotate the trident so the prongs point forward during wind-up");
		assertEquals(180, throwingDisplay.getAsJsonArray("rotation").get(2).getAsInt(),
			"Throwing pose should flip the cuboid trident so the prongs face forward during wind-up");
		assertTranslationBetween(throwingDisplay, 0, 8.5D, 10.5D,
			"Throwing third-person X should extend the wind-up grip out to the player's hand instead of stopping at the elbow");
		assertTranslationBetween(throwingDisplay, 1, -6.25D, -4.25D,
			"Throwing third-person Y should place the wind-up grip on the lowered hand plane instead of the elbow bend");
		assertTranslationBetween(throwingDisplay, 2, 0.0D, 1.5D,
			"Throwing third-person Z should pull the wind-up grip forward from the elbow/backline to the hand");
		assertScaleBetween(throwingDisplay, 0.5D, 0.6D,
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
		assertTrue(bbox.get(2).getAsDouble() <= 6.0D,
			"Voxel trident should stay slim enough for hand-held item rendering");
		String generator = read(Path.of("tools/build_ocean_relic_trident_model.py"));
		assertTrue(generator.contains("build_elements"),
			"Voxel item pipeline should be reusable instead of hand-edited JSON only");
		assertTrue(!generator.contains("write_inventory_sprite"),
			"Pipeline should not redraw a lower-quality inventory icon when the richer existing trident sprite is available");
		assertTrue(generator.contains("build_projectile_item_definition"),
			"Generator should emit a projectile item definition for the custom thrown harpoon renderer");
	}

	@Test
	void temporaryHarpoonProjectileUsesCustomThrownModelRenderer() throws IOException {
		String clientMixins = read(CLIENT_MIXIN_CONFIG);
		String rendererMixin = read(Path.of("src/client/java/dev/attuned/client/mixin/ThrownTridentRendererMixin.java"));
		String stateMixin = read(Path.of("src/client/java/dev/attuned/client/mixin/ThrownTridentRenderStateMixin.java"));
		assertTrue(clientMixins.contains("ThrownTridentRendererMixin"),
			"Client mixins should replace vanilla thrown-trident rendering for temporary harpoons");
		assertTrue(clientMixins.contains("ThrownTridentRenderStateMixin"),
			"Client mixins should attach custom item render state to thrown trident render states");
		assertTrue(rendererMixin.contains("HarpoonBehavior.isTemporaryHarpoon"),
			"Renderer should only override Attuned temporary harpoons, not every vanilla trident");
		assertTrue(rendererMixin.contains("ocean_relic_trident_projectile"),
			"Thrown harpoon should resolve Attuned's projectile item definition");
		assertTrue(rendererMixin.contains("ci.cancel()"),
			"Custom projectile renderer should cancel vanilla trident model submission");
		assertTrue(rendererMixin.contains("ItemDisplayContext.NONE"),
			"Projectile renderer should render the custom cuboid spear directly instead of GUI/ground inventory transforms");
		assertTrue(stateMixin.contains("ItemStackRenderState"),
			"Thrown trident render state should carry an item render state for the custom cuboid spear");
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

	private static void assertTransparentBorder(BufferedImage image, int border) {
		for (int i = 0; i < image.getWidth(); i++) {
			for (int inset = 0; inset < border; inset++) {
				assertEquals(0, alpha(image, i, inset), "Inventory icon should keep top transparent padding");
				assertEquals(0, alpha(image, i, image.getHeight() - 1 - inset),
					"Inventory icon should keep bottom transparent padding");
			}
		}
		for (int i = 0; i < image.getHeight(); i++) {
			for (int inset = 0; inset < border; inset++) {
				assertEquals(0, alpha(image, inset, i), "Inventory icon should keep left transparent padding");
				assertEquals(0, alpha(image, image.getWidth() - 1 - inset, i),
					"Inventory icon should keep right transparent padding");
			}
		}
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

	private static void assertNearestNeighbor2x(BufferedImage image) {
		assertTrue(image.getWidth() % 2 == 0 && image.getHeight() % 2 == 0,
			"Pixel-art inventory icon should have even dimensions for 2x nearest-neighbor scaling");
		for (int y = 0; y < image.getHeight(); y += 2) {
			for (int x = 0; x < image.getWidth(); x += 2) {
				int argb = image.getRGB(x, y);
				assertEquals(argb, image.getRGB(x + 1, y), "Inventory icon should keep hard 2x pixels");
				assertEquals(argb, image.getRGB(x, y + 1), "Inventory icon should keep hard 2x pixels");
				assertEquals(argb, image.getRGB(x + 1, y + 1), "Inventory icon should keep hard 2x pixels");
			}
		}
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

	private static void assertTranslationBetween(JsonObject display, int index, double min, double max, String message) {
		double value = display.getAsJsonArray("translation").get(index).getAsDouble();
		assertTrue(value >= min && value <= max, message + ": " + value);
	}

	private static void assertScaleBetween(JsonObject display, double min, double max, String message) {
		JsonArray scale = display.getAsJsonArray("scale");
		for (int index = 0; index < scale.size(); index++) {
			double value = scale.get(index).getAsDouble();
			assertTrue(value >= min && value <= max, message + " axis " + index + ": " + value);
		}
	}

	private static void assertCuboidCoordinatesInMinecraftBounds(Path modelPath, JsonArray elements) {
		for (JsonElement element : elements) {
			JsonObject cuboid = element.getAsJsonObject();
			assertCoordinateTripletInMinecraftBounds(modelPath, cuboid, "from");
			assertCoordinateTripletInMinecraftBounds(modelPath, cuboid, "to");
		}
	}

	private static void assertCoordinateTripletInMinecraftBounds(Path modelPath, JsonObject cuboid, String key) {
		JsonArray coordinates = cuboid.getAsJsonArray(key);
		String name = cuboid.has("name") ? cuboid.get("name").getAsString() : "<unnamed>";
		for (int index = 0; index < coordinates.size(); index++) {
			double value = coordinates.get(index).getAsDouble();
			assertTrue(value >= -16.0D && value <= 32.0D,
				modelPath + " cuboid " + name + " " + key + "[" + index
					+ "] should stay within Minecraft's [-16, 32] element bounds but was " + value);
		}
	}

	private static int alpha(BufferedImage image, int x, int y) {
		return (image.getRGB(x, y) >>> 24) & 0xFF;
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
