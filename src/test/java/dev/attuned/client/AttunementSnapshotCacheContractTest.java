package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunementSnapshotCacheContractTest {
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path COMBAT_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");
	private static final Path FOCUS_PANEL =
		Path.of("src/client/java/dev/attuned/client/FocusPanel.java");
	private static final Path ALTAR_SCREEN =
		Path.of("src/client/java/dev/attuned/client/screen/AltarScreen.java");

	@Test
	void attunementReadoutExposesPerTickLocalPlayerCache() throws IOException {
		String source = read(READOUT);

		assertTrue(source.contains("public static Snapshot cached(Player player)"),
			"Client UI should share one local-player readout snapshot per tick.");
		assertTrue(source.contains("tickCount"),
			"The cached readout should be invalidated by the player's client tick.");
		assertTrue(source.contains("cachedPlayerId"),
			"The cached readout should be invalidated across respawns/player swaps.");
		assertTrue(source.contains("cachedSnapshot = snapshot(player)"),
			"The cached accessor should refresh through the canonical snapshot builder.");
	}

	@Test
	void clientSurfacesRouteThroughSharedReadoutSnapshot() throws IOException {
		String fociHud = read(FOCI_HUD);
		String combatHud = read(COMBAT_HUD);
		String focusPanel = read(FOCUS_PANEL);
		String altarScreen = read(ALTAR_SCREEN);

		assertTrue(fociHud.contains("AttunementReadout.cached("),
			"The Foci HUD should reuse the local-player cached readout.");
		assertTrue(combatHud.contains("AttunementReadout.cached("),
			"The Combat HUD should reuse the local-player cached readout.");
		assertTrue(focusPanel.contains("AttunementReadout.cached("),
			"The Focus panel should reuse the local-player cached readout.");
		assertTrue(altarScreen.contains("AttunementReadout.cached("),
			"The Attunement Altar screen should reuse the local-player cached readout.");
		assertTrue(combatHud.contains("AttunementReadout.snapshot(targetPlayer)"),
			"Targeted players should be resolved through one fresh snapshot rather than local-player cache.");
		assertFalse(combatHud.contains("Set<Affinity> distinctAffinities = EnumSet.noneOf(Affinity.class);"),
			"CombatHud should not keep its duplicate own-stance affinity aggregation.");
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
