package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Build hotkeys: three unbound keybinds send {@link QuickApplyPresetPayload}
 * for preset indices 0-2. Unlike the menu apply, the quick path must not
 * require the reliquary screen to be open — the server sources the reliquary
 * from the player's inventory instead.
 */
class QuickApplyPresetContractTest {
	private static final Path PAYLOAD = Path.of("src/main/java/dev/attuned/menu/QuickApplyPresetPayload.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/PresetNetworking.java");
	private static final Path KEYBINDS = Path.of("src/client/java/dev/attuned/client/AttunedKeybinds.java");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void quickApplyPayloadCarriesOnlyAnIndex() throws IOException {
		String payload = read(PAYLOAD);
		assertTrue(payload.contains("record QuickApplyPresetPayload(int index)"),
			"Quick apply carries only a preset index.");
		assertTrue(payload.contains("ByteBufCodecs.VAR_INT"),
			"The index serializes as a var-int.");
	}

	@Test
	void quickApplyIsRegisteredAndDoesNotRequireTheOpenSatchelMenu() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("PayloadTypeRegistry.serverboundPlay().register(QuickApplyPresetPayload.TYPE")
				|| net.contains("PayloadTypeRegistry.playC2S().register(QuickApplyPresetPayload.TYPE"),
			"Quick apply payload should be registered serverbound.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(QuickApplyPresetPayload.TYPE"),
			"Quick apply should have a server receiver.");
		assertTrue(net.contains("private static void quickApplyPreset(ServerPlayer player, QuickApplyPresetPayload payload)"),
			"Quick apply should have its own handler.");
		assertTrue(net.contains("applyPresetCore(player, payload.index(), inventorySatchelState(player))"),
			"Quick apply should reuse the shared apply core with an inventory-sourced reliquary.");
		assertTrue(net.contains("private static SatchelState inventorySatchelState(ServerPlayer player)"),
			"Quick apply should source the first Focus Reliquary from the player's inventory.");
		assertTrue(!net.contains("hasOpenLiveSatchel(player)\n\t\t\t&& quickApply"),
			"Quick apply must not be gated on the open satchel menu.");
	}

	@Test
	void quickApplySharesTheMenuApplyCoreAndCooldown() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel)"),
			"Menu apply and quick apply should share one validated core.");
		assertTrue(net.contains("applyPresetCore(player, payload.index(), satchel)"),
			"Menu apply should route through the shared core.");
		assertTrue(net.contains("now - last < APPLY_COOLDOWN_TICKS"),
			"The shared core keeps the apply cooldown so hotkey spam cannot churn inventories.");
		assertTrue(net.contains("screen.attuned.preset.applied"),
			"Applies should confirm the applied build by name in the overlay.");
	}

	@Test
	void buildHotkeysAreRegisteredUnboundAndSendQuickApply() throws IOException {
		String keybinds = read(KEYBINDS);
		assertTrue(keybinds.contains("key.attuned.apply_build_"),
			"Apply-build keybinds should be registered.");
		assertTrue(keybinds.contains("applyBuildKeys = new KeyMapping[3]"),
			"Three apply-build keybinds should exist.");
		assertTrue(keybinds.contains("InputConstants.UNKNOWN"),
			"Apply-build keybinds should be unbound by default.");
		assertTrue(keybinds.contains("new QuickApplyPresetPayload(i)"),
			"Apply-build keys should send the quick apply payload for their index.");
		assertTrue(keybinds.contains("if (client.player != null)"),
			"Buffered clicks after disconnect should not send quick apply packets.");

		String lang = read(LANG);
		assertTrue(lang.contains("\"key.attuned.apply_build_1\"")
				&& lang.contains("\"key.attuned.apply_build_2\"")
				&& lang.contains("\"key.attuned.apply_build_3\""),
			"Each apply-build keybind should have a translation.");
		assertTrue(lang.contains("\"screen.attuned.preset.applied\""),
			"The applied-build overlay message should have a translation.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
