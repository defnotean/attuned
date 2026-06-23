package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for Foci whose promise is faster shift/crouch movement. */
class SneakingSpeedFocusContractTest {
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");

	@Test
	void shiftSpeedFociAddRealSneakingSpeedPoints() throws IOException {
		assertSneakingSpeedModifier("softstep_focus.json", 0.35);
		assertSneakingSpeedModifier("nullveil_focus.json", 0.20);
	}

	private static void assertSneakingSpeedModifier(String fileName, double amount) throws IOException {
		JsonObject root = JsonParser.parseString(
			Files.readString(FOCUS_DATA_DIR.resolve(fileName), StandardCharsets.UTF_8)).getAsJsonObject();
		JsonArray modifiers = root.getAsJsonArray("modifiers");
		assertNotNull(modifiers, fileName + " should declare its sneaking-speed modifier");
		assumeTrue(modifiers.size() > 0,
			"Minecraft 1.19.4 maintenance builds remove minecraft:sneaking_speed because the attribute is unavailable.");

		JsonObject modifier = modifiers.asList().stream()
			.map(element -> element.getAsJsonObject())
			.filter(entry -> "minecraft:sneaking_speed".equals(entry.get("attribute").getAsString()))
			.findFirst()
			.orElseThrow(() -> new AssertionError(fileName + " should affect minecraft:sneaking_speed"));

		assertEquals(amount, modifier.get("amount").getAsDouble(), 0.0001,
			fileName + " should keep its advertised shift-speed magnitude");
		assertEquals("add_value", modifier.get("operation").getAsString(),
			"minecraft:sneaking_speed is itself the crouch-speed multiplier (vanilla base 0.3), "
				+ "so shift-speed Foci must add multiplier points instead of a small percent of 0.3.");
	}
}
