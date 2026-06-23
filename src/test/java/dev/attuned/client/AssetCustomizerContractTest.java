package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AssetCustomizerContractTest {
	private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
	private static final Path CUSTOMIZER = Path.of("tools/asset_customizer");
	private static final Path MANIFEST = CUSTOMIZER.resolve("asset-manifest.json");
	private static final Path GUI_FIXTURES = CUSTOMIZER.resolve("gui-fixtures.json");
	private static final Path INDEX = CUSTOMIZER.resolve("index.html");
	private static final Path SCRIPT = CUSTOMIZER.resolve("asset-customizer.js");
	private static final Path STYLES = CUSTOMIZER.resolve("styles.css");
	private static final Path SERVER = CUSTOMIZER.resolve("serve.py");
	private static final Path GUI_PREVIEW_RENDERER = Path.of("tools/render_gui_previews.py");
	private static final Path MINECRAFT_RENDER_PREVIEW = Path.of("tools/minecraft_render_preview");
	private static final Path MINECRAFT_RENDER_PREVIEW_INDEX = MINECRAFT_RENDER_PREVIEW.resolve("index.html");
	private static final Path MINECRAFT_RENDER_PREVIEW_SCRIPT =
		MINECRAFT_RENDER_PREVIEW.resolve("minecraft-render-preview.js");
	private static final Path MINECRAFT_RENDER_PREVIEW_STYLES = MINECRAFT_RENDER_PREVIEW.resolve("styles.css");
	private static final Path MINECRAFT_RENDER_PREVIEW_SERVER = MINECRAFT_RENDER_PREVIEW.resolve("serve.py");
	private static final Path MINECRAFT_RENDER_PREVIEW_THREE =
		MINECRAFT_RENDER_PREVIEW.resolve("vendor/three.module.js");
	private static final Path MINECRAFT_RENDER_PREVIEW_ORBIT =
		MINECRAFT_RENDER_PREVIEW.resolve("vendor/controls/OrbitControls.js");
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
	private static final Path OCEAN_RELIC_TRIDENT_BLOCKBENCH_MODEL =
		Path.of("src/main/resources/assets/attuned/blockbench/ocean_relic_trident.bbmodel");
	private static final Path OCEAN_RELIC_TRIDENT_GLTF_MODEL =
		Path.of("src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb");
	private static final Path OCEAN_RELIC_TRIDENT_BLOCKBENCH_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_blockbench.png");
	private static final Path OCEAN_RELIC_TRIDENT_BLOCKBENCH_TEXTURE_META =
		Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_blockbench.png.mcmeta");
	private static final Path OCEAN_RELIC_TRIDENT_INVENTORY_MODEL =
		Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident_inventory.json");
	private static final Path OCEAN_RELIC_TRIDENT_ITEM_DEFINITION =
		Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident.json");
	private static final Path OCEAN_RELIC_TRIDENT_PROJECTILE_DEFINITION =
		Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident_projectile.json");
	private static final Path CLIENT_MIXIN_CONFIG = Path.of("src/client/resources/attuned.client.mixins.json");
	private static final Path BLOCKBENCH_SPECIAL_RENDERER =
		Path.of("src/client/java/dev/attuned/client/render/BlockbenchMeshSpecialRenderer.java");
	private static final Path GLTF_SPECIAL_RENDERER =
		Path.of("src/client/java/dev/attuned/client/render/GltfMeshSpecialRenderer.java");
	private static final Path GLTF_MODEL_MANAGER =
		Path.of("src/client/java/dev/attuned/client/render/AttunedGltfModels.java");
	private static final Path GLTF_MODEL_RECEIVER =
		Path.of("src/client/java/dev/attuned/client/render/GltfModelReceiver.java");
	private static final Path FROSTBOUND_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/item/frostbound_trident.png");

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
		assertTrue(html.contains("guiSelect"), "Customizer should let us switch GUI preview fixtures");
		assertTrue(html.contains("viewMode"), "Customizer should offer multiple preview modes");
		assertTrue(html.contains("value=\"gui\""), "Customizer should offer an out-of-game GUI preview mode");
		assertTrue(html.contains("value=\"firstperson\""), "Customizer should offer first-person held previews");
		assertTrue(html.contains("value=\"thirdperson\""), "Customizer should offer third-person held previews");
		assertTrue(html.contains("copyTransform"), "Customizer should expose transform-copy workflow");
		assertTrue(script.contains("fetchJson(\"asset-manifest.json\")"),
			"Customizer should load the manifest at runtime");
		assertTrue(script.contains("fetchJson(\"gui-fixtures.json\")"),
			"Customizer should load GUI preview fixtures at runtime");
		assertTrue(script.contains("applyPresetForView"),
			"Customizer should map preview modes to Minecraft display presets");
		assertTrue(script.contains("selectGuiFixture"),
			"Customizer should switch between GUI preview fixtures without reloading the page");
		assertTrue(script.contains("drawGuiPreview"),
			"Customizer should render real GUI textures to the canvas");
		assertTrue(script.contains("const guiInteractiveControls = [\"zoom\", \"offsetX\", \"offsetY\"]"),
			"GUI preview mode should keep pan and zoom controls enabled for inspection.");
		assertTrue(script.contains("canvas.addEventListener(\"pointerdown\", startGuiDrag)"),
			"GUI preview canvas should support click-and-drag positioning.");
		assertTrue(script.contains("canvas.addEventListener(\"wheel\", resizeGuiPreview)"),
			"GUI preview canvas should support wheel resizing.");
		assertTrue(script.contains("function startGuiDrag"),
			"GUI preview should track the start of drag gestures.");
		assertTrue(script.contains("function updateGuiDrag"),
			"GUI preview should update pan controls while dragging.");
		assertTrue(script.contains("function resizeGuiPreview"),
			"GUI preview should resize from pointer wheel gestures.");
		assertTrue(script.contains("function resetGuiView"),
			"GUI preview should reset pan and zoom without affecting item model transforms.");
		assertTrue(script.contains("function buildEditableGuiBoxes"),
			"GUI preview should expand fixture slots and regions into editable boxes.");
		assertTrue(script.contains("function hitTestGuiBox"),
			"GUI preview should hit-test individual overlay boxes before panning the whole canvas.");
		assertTrue(script.contains("function updateGuiBoxDrag"),
			"GUI preview should drag selected overlay boxes.");
		assertTrue(script.contains("function updateGuiBoxResize"),
			"GUI preview should resize selected overlay boxes.");
		assertTrue(script.contains("if ((guiDrag || guiBoxDrag) && event.pointerId != null)"),
			"GUI preview should release pointer capture after both canvas pans and individual box drags.");
		assertTrue(script.contains("function drawGuiBoxHandles"),
			"GUI preview should draw resize handles on the selected overlay box.");
		assertTrue(script.contains("selectedGuiBox"),
			"GUI preview should track which individual overlay box is selected.");
		assertTrue(script.contains("drawGuiSlotOverlay"),
			"Customizer should overlay Minecraft slot hitboxes on GUI previews");
		assertTrue(script.contains("drawGuiSampleItems"),
			"Customizer should draw deterministic sample inventory items for alignment checks");
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
	void minecraftRenderPreviewLoadsActualGltfHarpoonAssets() throws IOException {
		assertTrue(Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_INDEX),
			"Minecraft render preview should have an HTML entry point");
		assertTrue(Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_SCRIPT),
			"Minecraft render preview should have a WebGL renderer script");
		assertTrue(Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_STYLES),
			"Minecraft render preview should have local styles");
		assertTrue(Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_SERVER),
			"Minecraft render preview should have a localhost launcher");
		assertTrue(Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_THREE)
				&& Files.isRegularFile(MINECRAFT_RENDER_PREVIEW_ORBIT),
			"Minecraft render preview should vendor its WebGL runtime for offline localhost debugging");

		String html = read(MINECRAFT_RENDER_PREVIEW_INDEX);
		String script = read(MINECRAFT_RENDER_PREVIEW_SCRIPT);
		String server = read(MINECRAFT_RENDER_PREVIEW_SERVER);
		assertTrue(html.contains("renderCanvas"),
			"Minecraft render preview should render to a WebGL canvas");
		assertTrue(html.contains("./vendor/three.module.js") && html.contains("./vendor/"),
			"Minecraft render preview should use local Three.js modules instead of a CDN");
		assertTrue(html.contains("UV Y mode") && html.contains("Filtering") && html.contains("Normal mode"),
			"Minecraft render preview should expose the render-path controls needed to debug mesh artifacts");
		assertTrue(script.contains("ocean_relic_trident.glb")
				&& script.contains("ocean_relic_trident_blockbench.png"),
			"Minecraft render preview should load the actual shipped GLB model and texture");
		assertTrue(script.contains("BufferGeometry") && script.contains("MeshLambertMaterial"),
			"Minecraft render preview should render the real triangle mesh through WebGL");
		assertTrue(script.contains("parseGlbModel") && script.contains("readIndexAccessor")
				&& script.contains("uvMode"),
			"Minecraft render preview should let us compare GLB mesh buffers and UV conventions");
		assertTrue(server.contains("ThreadingHTTPServer") && server.contains("tools/minecraft_render_preview"),
			"Minecraft render preview should launch on localhost from the repo root");
	}

	@Test
	void manifestPointsAtRealAttunedAssets() throws IOException {
		JsonArray assets = JsonParser.parseString(read(MANIFEST)).getAsJsonArray();
		assertEquals(5, assets.size(), "Customizer should include Offshore assets, throw preview, and model conversion samples");

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
			if (asset.has("sprite")) {
				assertRelativeAssetExists(asset, "sprite");
			}
		}
		assertTrue(sawFocus, "Customizer manifest should include Harpoon Focus");
		assertTrue(sawHarpoon, "Customizer manifest should include Offshore Harpoon");
		assertTrue(sawOceanRelic, "Customizer manifest should include the Ocean Relic Trident model");
		assertTrue(sawOceanRelicThrowing, "Customizer manifest should include the Ocean Relic Trident throwing pose");
		assertTrue(sawFrostbound, "Customizer manifest should include the Frostbound Trident conversion");
	}

	@Test
	void guiPreviewFixturesPointAtRealMenusAndOfflineRenderer() throws IOException {
		assertTrue(Files.isRegularFile(GUI_FIXTURES), "Customizer should have GUI preview fixtures");
		assertTrue(Files.isRegularFile(GUI_PREVIEW_RENDERER), "Repo should have an offline GUI screenshot renderer");
		String renderer = read(GUI_PREVIEW_RENDERER);
		assertTrue(renderer.contains("gui-fixtures.json"),
			"Offline renderer should share the browser preview fixture data");
		assertTrue(renderer.contains("build/gui-previews"),
			"Offline renderer should write previews to a build output directory");
		assertTrue(renderer.contains("render_fixture"),
			"Offline renderer should render each fixture independently");
		assertTrue(renderer.contains("draw_slot_overlay"),
			"Offline renderer should draw slot hitboxes for layout inspection");
		assertTrue(renderer.contains("draw_sample_items"),
			"Offline renderer should draw deterministic sample items for layout inspection");

		JsonArray fixtures = JsonParser.parseString(read(GUI_FIXTURES)).getAsJsonArray();
		assertEquals(4, fixtures.size(), "GUI preview should cover altar, reweaving altar, satchel, and journal");
		boolean sawAltar = false;
		boolean sawReweaving = false;
		boolean sawSatchel = false;
		boolean sawJournal = false;
		for (JsonElement element : fixtures) {
			JsonObject fixture = element.getAsJsonObject();
			String id = fixture.get("id").getAsString();
			sawAltar |= "altar".equals(id);
			sawReweaving |= "reweaving".equals(id);
			sawSatchel |= "satchel".equals(id);
			sawJournal |= "journal".equals(id);

			Path texture = assertRelativeCustomizerFileExists(fixture.get("texture").getAsString());
			BufferedImage image = ImageIO.read(texture.toFile());
			assertNotNull(image, "GUI fixture texture should decode: " + texture);
			assertEquals(fixture.get("width").getAsInt(), image.getWidth(),
				"GUI fixture width should match the shipped texture: " + id);
			assertEquals(fixture.get("height").getAsInt(), image.getHeight(),
				"GUI fixture height should match the shipped texture: " + id);
			assertTrue(fixture.has("regions"), "GUI fixture should expose named inspection regions: " + id);
			if (!"journal".equals(id)) {
				assertTrue(fixture.getAsJsonArray("slotGroups").size() >= 1,
					"Container GUI fixtures should include slot group overlays: " + id);
			}
			if ("satchel".equals(id)) {
				JsonArray regions = fixture.getAsJsonArray("regions");
				assertRegion(regions, "Previous preset", 8, 72, 14, 12);
				assertRegion(regions, "Equipped slot selectors", 24, 72, 65, 12);
				assertRegion(regions, "Selected preset", 94, 74, 36, 10);
				assertRegion(regions, "Preset index", 132, 74, 18, 10);
				assertRegion(regions, "Next preset", 154, 72, 14, 12);
			}
		}
		assertTrue(sawAltar, "GUI preview should include the Attunement Altar");
		assertTrue(sawReweaving, "GUI preview should include the Reweaving Altar");
		assertTrue(sawSatchel, "GUI preview should include the Satchel of Foci");
		assertTrue(sawJournal, "GUI preview should include the Attunement Journal");
	}

	@Test
	void altarInventoryPreviewCoordinatesStayAlignedWithMenuAndGenerator() throws IOException {
		String altarMenu = read(Path.of("src/main/java/dev/attuned/menu/AltarMenu.java"));
		int inventoryX = sourceIntConstant(altarMenu, "INVENTORY_X");
		int inventoryY = sourceIntConstant(altarMenu, "INVENTORY_Y");

		JsonObject altar = guiFixture("altar");
		JsonObject inventoryGroup = slotGroup(altar, "Inventory");
		JsonObject hotbarGroup = slotGroup(altar, "Hotbar");
		assertEquals(inventoryX, inventoryGroup.get("x").getAsInt(),
			"Altar preview inventory X should match the real menu slot coordinate");
		assertEquals(inventoryY, inventoryGroup.get("y").getAsInt(),
			"Altar preview inventory Y should match the real menu slot coordinate");
		assertEquals(inventoryX, hotbarGroup.get("x").getAsInt(),
			"Altar preview hotbar X should match the real menu slot coordinate");
		assertEquals(inventoryY + 58, hotbarGroup.get("y").getAsInt(),
			"Altar preview hotbar Y should match AbstractContainerMenu.addStandardInventorySlots");

		String generator = read(Path.of("tools/generate_ui_art.py"));
		String altarGenerator = sourceBetween(generator, "def altar():", "\ndef focus_panel():");
		Matcher inventory = Pattern.compile(
			"slot_well\\(draw,\\s*(\\d+)\\s*\\+\\s*col\\s*\\*\\s*18,\\s*(\\d+)\\s*\\+\\s*row\\s*\\*\\s*18\\)")
			.matcher(altarGenerator);
		assertTrue(inventory.find(), "Altar art generator should paint the player inventory wells");
		assertEquals(inventoryX, Integer.parseInt(inventory.group(1)),
			"Generated altar inventory art should use the real menu X coordinate");
		assertEquals(inventoryY, Integer.parseInt(inventory.group(2)),
			"Generated altar inventory art should use the real menu Y coordinate");

		Matcher hotbar = Pattern.compile(
			"slot_well\\(draw,\\s*(\\d+)\\s*\\+\\s*col\\s*\\*\\s*18,\\s*(\\d+)\\)")
			.matcher(altarGenerator);
		assertTrue(hotbar.find(), "Altar art generator should paint the hotbar wells");
		assertEquals(inventoryX, Integer.parseInt(hotbar.group(1)),
			"Generated altar hotbar art should use the real menu X coordinate");
		assertEquals(inventoryY + 58, Integer.parseInt(hotbar.group(2)),
			"Generated altar hotbar art should use AbstractContainerMenu.addStandardInventorySlots spacing");
	}

	@Test
	void guiPreviewBoxEditsExportAReusableFixtureAndRendererHonorsIndividualSlots() throws IOException {
		String script = read(SCRIPT);
		String renderer = read(GUI_PREVIEW_RENDERER);

		assertTrue(script.contains("function fixtureFromEditableGuiBoxes"),
			"GUI preview edits should be exportable as a reusable fixture JSON object.");
		assertTrue(script.contains("fixture: fixtureFromEditableGuiBoxes()"),
			"The GUI-mode output should include the full edited fixture, not only the selected box.");
		assertTrue(script.contains("for (const slot of fixture.slots || [])"),
			"The browser preview should reload explicit per-box slot fixtures.");
		assertTrue(script.contains("slots: editableGuiBoxes.filter((box) => box.kind === \"slot\")"),
			"The exported fixture should preserve individually moved/resized slot boxes.");
		assertTrue(script.contains("regions: editableGuiBoxes.filter((box) => box.kind === \"region\")"),
			"The exported fixture should preserve individually moved/resized region boxes.");
		assertTrue(renderer.contains("for slot in fixture.get(\"slots\", [])"),
			"The offline preview renderer should render explicit per-box slot fixtures.");
		assertTrue(renderer.contains("slot.get(\"w\", 16)") && renderer.contains("slot.get(\"h\", 16)"),
			"The offline preview renderer should honor resized slot boxes.");
	}

	@Test
	void guiPreviewHitTestingPrioritizesIndividualSlotsOverBroadRegions() throws IOException {
		String script = read(SCRIPT);

		assertTrue(script.contains("for (const box of guiHitTestOrder())"),
			"GUI preview hit testing should use an explicit priority order instead of raw draw order.");
		assertTrue(script.contains("function guiHitTestOrder"),
			"GUI preview should centralize hit-test ordering.");
		assertTrue(script.contains("function guiBoxHitPriority"),
			"GUI preview should rank individual editable box kinds.");
		assertTrue(script.contains("return box.kind === \"slot\" ? 2 : 1"),
			"Individual slot boxes should be hit-tested before broad inspection regions.");
		assertTrue(!script.contains("for (const box of [...editableGuiBoxes].reverse())"),
			"Broad regions appended after slots should not steal clicks from individual slot boxes.");
	}

	@Test
	void manifestThrowingPoseUsesProjectileItemDefinition() throws IOException {
		JsonObject throwing = manifestAsset("ocean_relic_trident_throwing");
		Path definition = CUSTOMIZER.resolve(throwing.get("definition").getAsString()).normalize();
		JsonObject definitionRoot = JsonParser.parseString(read(definition)).getAsJsonObject();

		assertEquals(OCEAN_RELIC_TRIDENT_PROJECTILE_DEFINITION.normalize(), definition,
			"Throwing preview should edit the projectile item definition, not the held trident definition");
		assertGltfSpecial(definitionRoot.getAsJsonObject("model"),
			"attuned:item/ocean_relic_trident_throwing");
	}

	@Test
	void offshoreArtUsesShippedAssetsOnly() throws IOException {
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
	void meshConversionProducesMinecraftItemSpriteAndReusableReport() throws IOException {
		Path converter = Path.of("tools/mesh_to_minecraft_item.py");
		assertTrue(Files.isRegularFile(converter), "Mesh conversion pipeline should be reusable");
		String source = read(converter);
		assertTrue(source.contains("bpy.ops.import_scene.fbx"),
			"Mesh conversion pipeline should import FBX exports");
		assertTrue(source.contains("bpy.ops.export_scene.gltf"),
			"Mesh conversion pipeline should preserve a normalized GLB source");
		assertTrue(source.contains("\"parent\": \"minecraft:item/generated\""),
			"Mesh conversion pipeline should emit Minecraft generated item models");

		assertTrue(Files.isRegularFile(OCEAN_RELIC_TRIDENT_PALETTE),
			"Ocean Relic Trident should ship a palette for its voxel cuboid model");

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
	}

	@Test
	void generatedWeaponTransformsStayReadableInsteadOfEdgeOn() throws IOException {
		assertReadableGeneratedWeaponTransforms(
			Path.of("src/main/resources/assets/attuned/models/item/offshore_harpoon.json"));
	}

	@Test
	void frostboundTridentUsesAReadableVoxelCuboidModel() throws IOException {
		// Frostbound graduated from a flat generated sprite to a real voxel model;
		// its readability contract now mirrors the Ocean Relic's: real cuboids on a
		// palette texture, plus the hand-tuned trident display poses.
		Path modelPath = Path.of("src/main/resources/assets/attuned/models/item/frostbound_trident.json");
		JsonObject model = JsonParser.parseString(read(modelPath)).getAsJsonObject();
		assertTrue(!model.has("parent"),
			"Frostbound should render as a real cuboid voxel model, not a flat generated sprite");
		assertEquals("attuned:item/frostbound_trident_voxel_palette",
			model.getAsJsonObject("textures").get("palette").getAsString(),
			"Frostbound should use its generated ice palette texture");
		assertEquals("attuned:item/frostbound_trident_voxel_palette",
			model.getAsJsonObject("textures").get("particle").getAsString(),
			"Frostbound should define a particle texture to avoid missing-texture warnings");
		assertTrue(model.getAsJsonArray("elements").size() >= 30,
			"Frostbound should contain a real cuboid trident silhouette");
		assertCuboidCoordinatesInMinecraftBounds(modelPath, model.getAsJsonArray("elements"));
		JsonObject display = model.getAsJsonObject("display");
		assertTrue(display.has("gui") && display.has("firstperson_righthand")
				&& display.has("thirdperson_righthand"),
			"Frostbound should keep the hand-tuned trident display transforms");
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/assets/attuned/textures/item/frostbound_trident_voxel_palette.png")),
			"Frostbound's ice palette texture should ship with the model");
	}

	@Test
	void oceanRelicTridentUsesGltfMeshForTemporaryHarpoon() throws IOException {
		Path modelPath = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident.json");
		Path throwingModelPath = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json");
		JsonObject model = JsonParser.parseString(read(modelPath)).getAsJsonObject();
		JsonObject throwingModel = JsonParser.parseString(read(throwingModelPath)).getAsJsonObject();
		JsonObject inventoryModel = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_INVENTORY_MODEL)).getAsJsonObject();
		JsonObject itemDefinition = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_ITEM_DEFINITION)).getAsJsonObject();
		JsonObject projectileDefinition = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_PROJECTILE_DEFINITION)).getAsJsonObject();
		JsonObject blockbench = JsonParser.parseString(read(OCEAN_RELIC_TRIDENT_BLOCKBENCH_MODEL)).getAsJsonObject();
		JsonObject manifest = manifestAsset("ocean_relic_trident");
		JsonObject throwingManifest = manifestAsset("ocean_relic_trident_throwing");

		// The asset customizer now edits the editable Blockbench source mesh that backs the
		// owned glTF/Blockbench special renderer instead of the old voxel cuboid model.
		assertEquals("blockbench", manifest.get("kind").getAsString(),
			"Customizer should keep the editable Ocean Relic harpoon source as a Blockbench model");
		assertEquals("../../src/main/resources/assets/attuned/blockbench/ocean_relic_trident.bbmodel",
			manifest.get("model").getAsString(),
			"Customizer should edit the Blockbench source mesh for the held harpoon");
		assertEquals("blockbench", throwingManifest.get("kind").getAsString(),
			"Customizer throwing preview should use the same editable Blockbench source");
		assertEquals("minecraft:item/generated", inventoryModel.get("parent").getAsString(),
			"Inventory model should use a flat item sprite instead of the bulky held mesh");
		assertEquals("attuned:item/ocean_relic_trident",
			inventoryModel.getAsJsonObject("textures").get("layer0").getAsString(),
			"Inventory model should reuse the richer existing flat trident sprite instead of the separate inventory icon");

		// Minecraft 1.18.2 has no item-model data component or minecraft:special SPI, so the held
		// trident keeps its legacy minecraft:model item definition; the owned mesh is drawn through
		// Fabric's BuiltinItemRendererRegistry instead.
		String itemDefinitionText = itemDefinition.toString();
		assertTrue(itemDefinitionText.contains("minecraft:display_context"),
			"Item definition should select a separate GUI model by display context");
		assertTrue(itemDefinitionText.contains("ocean_relic_trident_inventory"),
			"GUI display context should use the flat inventory icon");
		assertTrue(itemDefinitionText.contains("ground") && itemDefinitionText.contains("fixed"),
			"Inventory-style display contexts should use the flat icon instead of the bulky held mesh");
		JsonObject fallback = itemDefinition.getAsJsonObject("model").getAsJsonObject("fallback");
		assertEquals("attuned:item/ocean_relic_trident",
			fallback.getAsJsonObject("on_false").get("model").getAsString(),
			"Relaxed held state should keep its legacy item model on this generation");
		assertEquals("attuned:item/ocean_relic_trident_throwing",
			fallback.getAsJsonObject("on_true").get("model").getAsString(),
			"Throw wind-up should keep its legacy item model on this generation");
		assertEquals("attuned:item/ocean_relic_trident_throwing",
			projectileDefinition.getAsJsonObject("model").get("model").getAsString(),
			"Thrown harpoon renderer should resolve a custom projectile model instead of vanilla trident art");

		// The held/throwing base models keep their hand-tuned display transforms; the mesh renderer
		// follows the vanilla trident coordinate frame so those transforms keep placing it in the hand.
		assertTrue(model.has("display") && throwingModel.has("display"),
			"Special renderer base models should keep the hand-tuned display transforms");
		JsonObject heldDisplay = model.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand");
		assertScaleBetween(heldDisplay, 0.5D, 0.6D,
			"Held third-person scale should resize the trident to a player-hand readable size");
		JsonObject throwingDisplay = throwingModel.getAsJsonObject("display")
			.getAsJsonObject("thirdperson_righthand");
		assertEquals(90, throwingDisplay.getAsJsonArray("rotation").get(1).getAsInt(),
			"Throwing pose should rotate the trident so the prongs point forward during wind-up");
		assertEquals(180, throwingDisplay.getAsJsonArray("rotation").get(2).getAsInt(),
			"Throwing pose should flip the trident so the prongs face forward during wind-up");

		// The shipped runtime mesh assets.
		assertTrue(Files.isRegularFile(OCEAN_RELIC_TRIDENT_GLTF_MODEL),
			"Actual temporary harpoon should ship a compact GLB runtime mesh");
		byte[] glb = Files.readAllBytes(OCEAN_RELIC_TRIDENT_GLTF_MODEL);
		assertTrue(glb.length > 300_000 && glb.length < 500_000,
			"Runtime GLB should keep mesh buffers without the original embedded 54 MB textures");
		assertEquals(0x46546C67, littleEndianInt(glb, 0),
			"Runtime GLB should keep the glTF binary magic");
		assertEquals(2, littleEndianInt(glb, 4),
			"Runtime GLB should be a glTF 2.0 binary");

		assertEquals("free", blockbench.getAsJsonObject("meta").get("model_format").getAsString(),
			"Blockbench source should stay available as the editable source mesh");
		JsonArray bbElements = blockbench.getAsJsonArray("elements");
		assertEquals(1, bbElements.size(), "Blockbench export should preserve the single trident mesh");
		JsonObject mesh = bbElements.get(0).getAsJsonObject();
		assertEquals("mesh", mesh.get("type").getAsString(),
			"Actual temporary harpoon should not be converted back into cuboid wrapper geometry");
		assertTrue(mesh.getAsJsonObject("vertices").size() >= 9000,
			"Blockbench harpoon should preserve the detailed model vertices");
		assertTrue(mesh.getAsJsonObject("faces").size() >= 6000,
			"Blockbench harpoon should preserve the detailed model faces");
		assertTrue(Files.isRegularFile(OCEAN_RELIC_TRIDENT_BLOCKBENCH_TEXTURE),
			"Blockbench harpoon texture should ship beside the item textures");
		String textureMeta = read(OCEAN_RELIC_TRIDENT_BLOCKBENCH_TEXTURE_META);
		assertTrue(textureMeta.contains("\"clamp\": true") && textureMeta.contains("\"blur\": false"),
			"Harpoon texture should clamp and avoid blur while sampled through custom mesh UVs");

		// The owned renderer wiring, ported to the legacy 1.18.2 client API.
		String renderer = read(GLTF_SPECIAL_RENDERER);
		String modelManager = read(GLTF_MODEL_MANAGER);
		String modelReceiver = read(GLTF_MODEL_RECEIVER);
		String blockbenchRenderer = read(BLOCKBENCH_SPECIAL_RENDERER);
		String client = read(Path.of("src/client/java/dev/attuned/client/AttunedClient.java"));
		assertTrue(renderer.contains("gltf/ocean_relic_trident.glb"),
			"Client should register an Attuned glTF mesh renderer for the Ocean Relic harpoon");
		assertTrue(renderer.contains("implements GltfModelReceiver")
				&& renderer.contains("getModelLocation()")
				&& renderer.contains("onReceiveSharedModel"),
			"Renderer should follow MCglTF's model receiver pattern");
		assertTrue(renderer.contains("BuiltinItemRendererRegistry.INSTANCE.register"),
			"Older-gen renderer should draw through Fabric's BuiltinItemRendererRegistry");
		assertTrue(renderer.contains("RenderType.entityCutout"),
			"Renderer should submit the mesh through an entity cutout buffer");
		assertTrue(renderer.contains("isTemporaryHarpoon"),
			"Renderer should only override Attuned temporary harpoons, not every vanilla trident");
		assertTrue(renderer.contains("TridentModel"),
			"Renderer should fall back to the vanilla trident model for unmarked tridents");
		assertTrue(modelReceiver.contains("ResourceLocation getModelLocation()")
				&& modelReceiver.contains("onReceiveSharedModel")
				&& modelReceiver.contains("isReceiveSharedModel"),
			"Attuned should keep an MCglTF-style receiver contract");
		assertTrue(modelManager.contains("SimpleSynchronousResourceReloadListener")
				&& modelManager.contains("ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)")
				&& modelManager.contains("registerReloadListener(this)"),
			"Attuned should load shared glTF models through a client resource reload listener");
		assertTrue(modelManager.contains("GLB_MAGIC") && modelManager.contains("JSON_CHUNK")
				&& modelManager.contains("BIN_CHUNK"),
			"Model manager should parse GLB chunks directly");
		assertTrue(modelManager.contains("\"POSITION\"") && modelManager.contains("\"NORMAL\"")
				&& modelManager.contains("\"TEXCOORD_0\"") && modelManager.contains("MODE_TRIANGLES"),
			"Model manager should load the glTF mesh attributes used by the Ocean Relic export");
		assertTrue(modelManager.contains("baseColorTexture")
				&& modelManager.contains("NativeImage.read")
				&& modelManager.contains("DynamicTexture")
				&& modelManager.contains("data:"),
			"Model manager should support glTF material base-color textures, including embedded images");
		assertTrue(modelManager.contains("translation") && modelManager.contains("rotation")
				&& modelManager.contains("scale"),
			"Model manager should process scene-node transforms instead of requiring one hard-coded asset orientation");
		assertTrue(renderer.contains("Repeating C") && renderer.contains("triangle.c(), light, overlay);"),
			"Renderer should convert glTF triangles into degenerate quads for Minecraft's cutout buffers");
		assertTrue(!renderer.contains("GL11") && !renderer.contains("GL20") && !renderer.contains("glDraw"),
			"Attuned's custom version should not copy MCglTF's old raw OpenGL rendering path");
		assertTrue(blockbenchRenderer.contains("Repeating C"),
			"Legacy Blockbench renderer should keep the same triangle-to-quad safety fix");
		assertTrue(!renderer.contains("OCEAN_RELIC_TARGET") && !renderer.contains("mapAxis"),
			"Renderer should not remap the GLB mesh into the old cuboid wrapper bounds");
		assertTrue(client.contains("GltfMeshSpecialRenderer.init()"),
			"Client initializer should register the glTF special renderer before item models load");
	}

	@Test
	void temporaryHarpoonProjectileUsesCustomThrownModelRenderer() throws IOException {
		String clientMixins = read(CLIENT_MIXIN_CONFIG);
		assumeTrue(clientMixins.contains("ThrownTridentRendererMixin"),
			"Minecraft 1.20.6 maintenance builds use the vanilla thrown-trident renderer.");
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
			"Projectile renderer should render the Blockbench harpoon directly instead of GUI/ground inventory transforms");
		assertTrue(stateMixin.contains("ItemStackRenderState"),
			"Thrown trident render state should carry an item render state for the custom Blockbench harpoon");
	}

	private static void assertRelativeAssetExists(JsonObject asset, String key) {
		Path path = CUSTOMIZER.resolve(asset.get(key).getAsString()).normalize();
		assertTrue(path.toAbsolutePath().normalize().startsWith(ROOT),
			"Customizer asset path should stay inside the repo: " + path);
		assertTrue(Files.isRegularFile(path),
			"Customizer manifest should point at an existing " + key + ": " + path);
	}

	private static void assertGltfSpecial(JsonObject model, String base) {
		String type = model.get("type").getAsString();
		if ("minecraft:special".equals(type)) {
			assertEquals(base, model.get("base").getAsString(),
				"Special renderer should preserve the expected base model for transforms");
			JsonObject special = model.getAsJsonObject("model");
			assertEquals("attuned:gltf_mesh", special.get("type").getAsString(),
				"Special renderer should use Attuned's glTF mesh renderer");
			assertEquals("attuned:gltf/ocean_relic_trident.glb", special.get("model").getAsString(),
				"Special renderer should load the compact GLB runtime model");
			assertEquals("attuned:textures/item/ocean_relic_trident_mesh.png", special.get("texture").getAsString(),
				"Special renderer should use the exported game-scale texture");
			return;
		}
		assertEquals("minecraft:model", type,
			"Legacy 1.18.2 item definitions should keep normal model entries while the trident item renderer supplies the GLB mesh.");
		assertEquals(base, model.get("model").getAsString(),
			"Legacy model definition should preserve the expected transform model.");
	}

	private static Path assertRelativeCustomizerFileExists(String relativePath) {
		Path path = CUSTOMIZER.resolve(relativePath).normalize();
		assertTrue(path.toAbsolutePath().normalize().startsWith(ROOT),
			"Customizer path should stay inside the repo: " + path);
		assertTrue(Files.isRegularFile(path),
			"Customizer path should point at an existing file: " + path);
		return path;
	}

	private static void assertRegion(JsonArray regions, String name, int x, int y, int w, int h) {
		for (JsonElement element : regions) {
			JsonObject region = element.getAsJsonObject();
			if (!name.equals(region.get("name").getAsString())) {
				continue;
			}
			assertEquals(x, region.get("x").getAsInt(), name + " x");
			assertEquals(y, region.get("y").getAsInt(), name + " y");
			assertEquals(w, region.get("w").getAsInt(), name + " width");
			assertEquals(h, region.get("h").getAsInt(), name + " height");
			return;
		}
		throw new AssertionError("Missing GUI fixture region: " + name);
	}

	private static JsonObject guiFixture(String id) throws IOException {
		JsonArray fixtures = JsonParser.parseString(read(GUI_FIXTURES)).getAsJsonArray();
		for (JsonElement element : fixtures) {
			JsonObject fixture = element.getAsJsonObject();
			if (id.equals(fixture.get("id").getAsString())) {
				return fixture;
			}
		}
		throw new AssertionError("Missing GUI fixture: " + id);
	}

	private static JsonObject slotGroup(JsonObject fixture, String name) {
		for (JsonElement element : fixture.getAsJsonArray("slotGroups")) {
			JsonObject group = element.getAsJsonObject();
			if (name.equals(group.get("name").getAsString())) {
				return group;
			}
		}
		throw new AssertionError("Missing slot group: " + name);
	}

	private static int sourceIntConstant(String source, String name) {
		Matcher matcher = Pattern.compile(
			"\\b" + Pattern.quote(name) + "\\s*=\\s*(\\d+)\\s*;")
			.matcher(source);
		assertTrue(matcher.find(), "Missing integer constant " + name);
		return Integer.parseInt(matcher.group(1));
	}

	private static String sourceBetween(String source, String start, String end) {
		int startIndex = source.indexOf(start);
		assertTrue(startIndex >= 0, "Missing source start marker " + start);
		int endIndex = source.indexOf(end, startIndex + start.length());
		assertTrue(endIndex >= 0, "Missing source end marker " + end);
		return source.substring(startIndex, endIndex);
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

	private static double[] elementSpan(JsonArray elements) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (JsonElement element : elements) {
			JsonObject cuboid = element.getAsJsonObject();
			JsonArray from = cuboid.getAsJsonArray("from");
			JsonArray to = cuboid.getAsJsonArray("to");
			minX = Math.min(minX, Math.min(from.get(0).getAsDouble(), to.get(0).getAsDouble()));
			minY = Math.min(minY, Math.min(from.get(1).getAsDouble(), to.get(1).getAsDouble()));
			minZ = Math.min(minZ, Math.min(from.get(2).getAsDouble(), to.get(2).getAsDouble()));
			maxX = Math.max(maxX, Math.max(from.get(0).getAsDouble(), to.get(0).getAsDouble()));
			maxY = Math.max(maxY, Math.max(from.get(1).getAsDouble(), to.get(1).getAsDouble()));
			maxZ = Math.max(maxZ, Math.max(from.get(2).getAsDouble(), to.get(2).getAsDouble()));
		}
		return new double[] { maxX - minX, maxY - minY, maxZ - minZ };
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

	private static int littleEndianInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xFF)
			| ((bytes[offset + 1] & 0xFF) << 8)
			| ((bytes[offset + 2] & 0xFF) << 16)
			| ((bytes[offset + 3] & 0xFF) << 24);
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static JsonObject manifestAsset(String id) throws IOException {
		JsonArray assets = JsonParser.parseString(read(MANIFEST)).getAsJsonArray();
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			if (id.equals(asset.get("id").getAsString())) {
				return asset;
			}
		}
		throw new AssertionError("Missing customizer manifest asset: " + id);
	}
}
