package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SatchelScreenContractTest {
	private static final Path SCREEN = Path.of("src/client/java/dev/attuned/client/screen/SatchelScreen.java");
	private static final Path SCREENS = Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path TEXTURE = Path.of("src/main/resources/assets/attuned/textures/gui/satchel.png");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void satchelScreenUsesForkRenderHooksAndTexture() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("extends AbstractContainerScreen<SatchelMenu>"),
			"SatchelScreen should be a container screen over the satchel menu.");
		assertTrue(screen.contains("extractBackground(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork background render hook, not vanilla renderBg.");
		assertTrue(screen.contains("extractLabels(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork label render hook.");
		assertTrue(screen.contains("textures/gui/satchel.png"),
			"SatchelScreen must reference its background texture.");
		assertTrue(read(SCREENS).contains("MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new)"),
			"The satchel screen must be registered against its menu type.");
	}

	@Test
	void satchelScreenExposesPresetActionsAndReadsSyncedPresets() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("new SavePresetPayload"), "Save button should send a SavePresetPayload.");
		assertTrue(screen.contains("new ApplyPresetPayload"), "Apply button should send an ApplyPresetPayload.");
		assertTrue(screen.contains("new DeletePresetPayload"), "Delete button should send a DeletePresetPayload.");
		assertTrue(screen.contains("new ImportPresetPayload"), "Import button should send an ImportPresetPayload.");
		assertTrue(screen.contains("BuildShareCodec.encode"), "Share should encode the selected build for clipboard export.");
		assertTrue(screen.contains("BuildShareCodec.decode"), "Import should decode clipboard text before sending.");
		assertTrue(screen.contains("keyboardHandler.setClipboard"), "Share should copy the encoded build to the clipboard.");
		assertTrue(screen.contains("keyboardHandler.getClipboard"), "Import should read the clipboard.");
		assertTrue(screen.contains("screen.attuned.preset.share"), "Share button label.");
		assertTrue(screen.contains("screen.attuned.preset.import"), "Import button label.");
		assertTrue(screen.contains("minecraft.gui.setOverlayMessage"), "Share/import feedback should use the action bar overlay.");
		assertTrue(screen.contains("ClientPlayNetworking.send"), "Preset buttons should send over client networking.");
		assertTrue(screen.contains("AttunedAttachments.getPresets"),
			"The screen should read presets from the synced attachment each frame (no caching).");
	}

	@Test
	void fociMoveByNativeDragNotBlindClicks() throws IOException {
		String screen = read(SCREEN);
		assertFalse(screen.contains("MoveFocusPayload"),
			"Foci now move via native slot drag-and-drop, so the screen no longer sends per-click move payloads.");
		assertFalse(screen.contains("selectedEquippedSlot"),
			"Drag-and-drop targets a slot directly, so there is no single selected equipped slot.");
		assertFalse(screen.contains("satchelSlotAt"),
			"The screen no longer hit-tests satchel slots to send blind click moves.");
	}

	@Test
	void equippedFocusColumnIsDrawnAsRealDropTargets() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("SatchelMenu.EQUIPPED_X"),
			"The equipped column should mirror the menu's equipped slot geometry so drops line up.");
		assertTrue(screen.contains("drawWell("),
			"The equipped Focus slots, drawn beside the window, need slot wells.");
		assertTrue(screen.contains("screen.attuned.equipped"),
			"The equipped section should be labelled.");
	}

	@Test
	void savedBuildsRenderAsAClickableSelectableList() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("refreshBuildButtons"),
			"Each saved build should become a clickable button, rebuilt when the synced list changes.");
		assertTrue(screen.contains("selectBuild("),
			"Clicking a build name should select that build.");
		assertTrue(screen.contains("selectedIndex = index"),
			"Selecting a build should set the selected index used by Apply/Delete.");
		assertTrue(screen.contains("screen.attuned.builds"),
			"The builds section should be labelled.");
		assertTrue(screen.contains("() -> selectedIndex == index"),
			"The selected build button should highlight.");
	}

	@Test
	void savedBuildsDisplaySetupMetadataOnHover() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("drawBuildMetadataTooltip(graphics, mouseX, mouseY)"),
			"The labels pass should show setup metadata when a saved build row is hovered.");
		assertTrue(screen.contains("private void drawBuildMetadataTooltip"),
			"Metadata hover rendering should live in a small helper beside the item preview.");
		assertTrue(screen.contains("hoveredBuildIndex(mouseX, mouseY)"),
			"Metadata tooltips should use the same row hit test as the build preview.");
		assertTrue(screen.contains("buildMetadataTooltip(presets.get(hovered))"),
			"The hover helper should render metadata for the actual hovered preset.");
		assertTrue(screen.contains("graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY)"),
			"Saved-build metadata should use the fork tooltip API instead of drawing free-floating text.");
		assertTrue(screen.contains("private static List<Component> buildMetadataTooltip(FocusPreset preset)"),
			"Tooltip line construction should be isolated for role/note/warning coverage.");
		assertTrue(screen.contains("preset.role()"),
			"Saved setup role metadata should be shown when present.");
		assertTrue(screen.contains("preset.note()"),
			"Saved setup note metadata should be shown when present.");
		assertTrue(screen.contains("preset.preferredPartySize()"),
			"Saved setup party-size metadata should be shown when present.");
		assertTrue(screen.contains("preset.warnings()"),
			"Saved setup warnings should be shown when present.");
		assertTrue(screen.contains("preset.requires()"),
			"Build-code requires metadata should be visible as advisory missing-content context.");
		assertTrue(screen.contains("ChatFormatting.YELLOW"),
			"Warnings should be visually distinct from ordinary metadata.");
	}

	@Test
	void buildsCanBeNamedBeforeSaving() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("new EditBox"),
			"There should be a text field for naming a build.");
		assertTrue(screen.contains("this.nameField.getValue()"),
			"Save should use the typed build name.");
		assertTrue(screen.contains("setMaxLength"),
			"The name field should cap length to match the preset name limit.");
		assertTrue(screen.contains("canConsumeInput"),
			"Typing in the name field must not trigger inventory hotkeys or close the screen.");
	}

	@Test
	void satchelSaveFallsBackToDistinctDefaultNamesUntilThePresetCap() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("nextPresetName()"),
			"Save should fall back to a live, unused default name when the name field is blank.");
		assertTrue(screen.contains("private String nextPresetName()"),
			"Default build naming should live in a small helper for contract coverage.");
		assertTrue(screen.contains("Set<String> usedNames"),
			"Build naming should compare against the synced build names.");
		assertTrue(screen.contains("AttunedAttachments.MAX_PRESETS"),
			"Build naming should honor the shared preset cap.");
		assertTrue(screen.contains("\"Build \" + i"),
			"Additional default builds should get numbered names.");
		assertFalse(screen.contains("new SavePresetPayload(\"Build\")"),
			"The client should not always send the same build name.");
	}

	@Test
	void satchelTitleIsNotPaintedOverThePanels() throws IOException {
		String screen = read(SCREEN);
		assertFalse(screen.contains("graphics.text(this.font, this.title"),
			"The reliquary screen should not paint the window title over the side panels.");
	}

	@Test
	void satchelTextureHasPinnedSize() throws IOException {
		assertTrue(Files.isRegularFile(TEXTURE), "Satchel GUI texture must exist.");
		BufferedImage image = ImageIO.read(TEXTURE.toFile());
		assertNotNull(image, "Satchel GUI texture must be a readable PNG.");
		assertEquals(176, image.getWidth(), "Satchel texture width must be pinned.");
		assertEquals(166, image.getHeight(), "Satchel texture height must be pinned.");
	}

	@Test
	void presetLangKeysExist() throws IOException {
		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("screen.attuned.preset.save"), "Save label.");
		assertTrue(lang.has("screen.attuned.preset.share"), "Share label.");
		assertTrue(lang.has("screen.attuned.preset.import"), "Import label.");
		assertTrue(lang.has("screen.attuned.preset.shared"), "Share success string.");
		assertTrue(lang.has("screen.attuned.preset.imported"), "Import success string.");
		assertTrue(lang.has("screen.attuned.preset.import_failed"), "Import failure string.");
		assertTrue(lang.has("screen.attuned.preset.apply"), "Apply label.");
		assertTrue(lang.has("screen.attuned.preset.delete"), "Delete label.");
		assertTrue(lang.has("screen.attuned.preset.missing"), "Missing-focus feedback string.");
		assertTrue(lang.has("screen.attuned.equipped"), "Equipped section label.");
		assertTrue(lang.has("screen.attuned.builds"), "Builds section label.");
		assertTrue(lang.has("screen.attuned.builds.empty"), "Empty-builds hint.");
		assertTrue(lang.has("screen.attuned.builds.name"), "Build name field label.");
		assertTrue(lang.has("screen.attuned.builds.name_hint"), "Build name field hint.");
		assertTrue(lang.has("screen.attuned.preset.metadata.role"), "Setup role tooltip label.");
		assertTrue(lang.has("screen.attuned.preset.metadata.note"), "Setup note tooltip label.");
		assertTrue(lang.has("screen.attuned.preset.metadata.party_size"), "Setup party-size tooltip label.");
		assertTrue(lang.has("screen.attuned.preset.metadata.warning"), "Setup warning tooltip label.");
		assertTrue(lang.has("screen.attuned.preset.metadata.requires"), "Setup requires tooltip label.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
