package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** File-level checks for data-driven vanilla trade integration. */
class AttunedTradeDataTest {
	private static final Path TRADE_DIR =
		Path.of("src/main/resources/data/attuned/villager_trade/wandering_trader");
	private static final Path WANDERING_TRADER_UNCOMMON_TAG =
		Path.of("src/main/resources/data/minecraft/tags/villager_trade/wandering_trader/uncommon.json");
	private static final Set<String> ALLOWED_GIVES = Set.of(
		"attuned:attunement_journal",
		"attuned:attunement_shard_fragment");

	@Test
	void wanderingTraderOffersOnlyJournalAndShardFragments() throws IOException {
		assertTrue(Files.isDirectory(TRADE_DIR), "Missing Attuned wandering trader trade directory");
		Set<String> tradeIds = new TreeSet<>();
		try (var files = Files.list(TRADE_DIR)) {
			for (Path trade : files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = json(trade);
				String item = root.getAsJsonObject("gives").get("id").getAsString();
				assertTrue(ALLOWED_GIVES.contains(item),
					"Wandering traders should not sell full Foci or full shards: " + trade);
				assertEquals("minecraft:emerald", root.getAsJsonObject("wants").get("id").getAsString(),
					"Wandering trader Attuned offers should remain emerald-priced");
				assertTrue(root.get("max_uses").getAsInt() <= 2,
					"Wandering trader Attuned offers should stay low-use");
				tradeIds.add("attuned:wandering_trader/"
					+ trade.getFileName().toString().replaceFirst("\\.json$", ""));
			}
		}
		assertEquals(taggedTradeIds(), tradeIds,
			"The uncommon wandering trader tag should include exactly the Attuned trade files");
	}

	private static Set<String> taggedTradeIds() throws IOException {
		JsonObject root = json(WANDERING_TRADER_UNCOMMON_TAG);
		assertTrue(!root.get("replace").getAsBoolean(), "Attuned should append to vanilla uncommon trades");
		JsonArray values = root.getAsJsonArray("values");
		Set<String> ids = new TreeSet<>();
		for (var value : values) {
			ids.add(value.getAsString());
		}
		return ids;
	}

	private static JsonObject json(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
