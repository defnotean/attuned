package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Guards the image-generated source art and Minecraft-sized Satchel textures. */
class SatchelGeneratedAssetContractTest {
	private static final Path SOURCE_ITEM =
		Path.of("docs/superpowers/assets/satchel-of-foci/satchel-item-imagegen-source.png");
	private static final Path SOURCE_GUI =
		Path.of("docs/superpowers/assets/satchel-of-foci/satchel-gui-imagegen-source.png");
	private static final Path ITEM =
		Path.of("src/main/resources/assets/attuned/textures/item/satchel_of_foci.png");
	private static final Path GUI =
		Path.of("src/main/resources/assets/attuned/textures/gui/satchel.png");

	@Test
	void satchelAssetsKeepImageGeneratedSourcesAndPinnedMinecraftSizes() throws IOException {
		assertTrue(Files.isRegularFile(SOURCE_ITEM), "Satchel item source art should be kept for future art passes.");
		assertTrue(Files.isRegularFile(SOURCE_GUI), "Satchel GUI source art should be kept for future art passes.");

		BufferedImage item = ImageIO.read(ITEM.toFile());
		BufferedImage gui = ImageIO.read(GUI.toFile());
		assertNotNull(item, "Satchel item texture must be a readable PNG.");
		assertNotNull(gui, "Satchel GUI texture must be a readable PNG.");
		assertEquals(16, item.getWidth(), "Satchel item texture must stay Minecraft item sized.");
		assertEquals(16, item.getHeight(), "Satchel item texture must stay Minecraft item sized.");
		assertEquals(176, gui.getWidth(), "Satchel GUI texture width must stay pinned.");
		assertEquals(166, gui.getHeight(), "Satchel GUI texture height must stay pinned.");
		assertTrue(nonTransparentPixels(item) > 32, "Satchel item should not be blank after downscaling.");
	}

	private static int nonTransparentPixels(BufferedImage image) {
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (((image.getRGB(x, y) >>> 24) & 0xff) > 0) {
					count++;
				}
			}
		}
		return count;
	}
}
