package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.combat.Apex;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HudAssetConsistencyTest {
	private static final Path COMBAT_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");
	private static final Path HUD_SPRITES =
		Path.of("src/main/resources/assets/attuned/textures/gui/sprites/hud");
	private static final Path HUD_BACKPLATE =
		Path.of("src/main/resources/assets/attuned/textures/gui/hud_backplate.png");

	@Test
	void combatHudUsesTextureSpritesForGemsAndStateMarkers() throws IOException {
		String source = Files.readString(COMBAT_HUD, StandardCharsets.UTF_8);

		assertTrue(!source.contains("graphics.fill("),
			"Combat HUD should use PNG sprites instead of manually filled pixel shapes");
		assertTrue(source.contains("affinity_fury"));
		assertTrue(source.contains("capstone.name().toLowerCase(Locale.ROOT)"));
		assertTrue(source.contains("hud_empowered_ring"));
		assertTrue(source.contains("hud_resonance_fill"));
		assertSprite("affinity_fury.png", 64, 64);
		assertSprite("affinity_bastion.png", 64, 64);
		assertSprite("affinity_zephyr.png", 64, 64);
		assertSprite("affinity_holy.png", 64, 64);
		assertSprite("affinity_discord.png", 64, 64);
		assertSprite("affinity_neutral.png", 64, 64);
		for (Apex.Capstone capstone : Apex.Capstone.values()) {
			assertSprite(capstone.name().toLowerCase(Locale.ROOT) + ".png", 64, 64);
		}
		assertSprite("hud_empowered_ring.png", 64, 64);
		assertSprite("hud_target_ring.png", 64, 64);
		assertSprite("hud_neutralized_overlay.png", 64, 64);
		assertSprite("hud_link_neutral.png", 8, 4);
		assertSprite("hud_link_empowered.png", 8, 4);
		assertSprite("hud_link_neutralized.png", 8, 4);
		assertSprite("hud_resonance_track.png", 14, 3);
		assertSprite("hud_resonance_fill.png", 14, 3);
		assertImageSize(HUD_BACKPLATE, 50, 24);
	}

	private static void assertSprite(String name, int width, int height) throws IOException {
		assertImageSize(HUD_SPRITES.resolve(name), width, height);
	}

	private static void assertImageSize(Path file, int width, int height) throws IOException {
		BufferedImage image = ImageIO.read(file.toFile());
		assertNotNull(image, "HUD asset should be a readable PNG: " + file);
		assertEquals(width, image.getWidth(), "Unexpected HUD asset width: " + file);
		assertEquals(height, image.getHeight(), "Unexpected HUD asset height: " + file);
	}
}
