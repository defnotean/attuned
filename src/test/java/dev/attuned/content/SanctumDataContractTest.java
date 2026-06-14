package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * File-level checks for the Attunement Sanctum datapack and generated template.
 *
 * <p>These run on the unit classpath (no Bootstrap), so they validate JSON shape
 * and the raw bytes of the generated {@code .nbt} rather than instantiating any
 * game objects.
 */
class SanctumDataContractTest {
	private static final Path DATA = Path.of("src/main/resources/data/attuned");
	private static final Path STRUCTURE = DATA.resolve("worldgen/structure/attunement_sanctum.json");
	private static final Path STRUCTURE_SET = DATA.resolve("worldgen/structure_set/attunement_sanctum.json");
	private static final Path TEMPLATE_POOL = DATA.resolve("worldgen/template_pool/sanctum/main.json");
	private static final Path BIOME_TAG = DATA.resolve("tags/worldgen/biome/has_sanctum.json");
	private static final Path LOOT_TABLE = DATA.resolve("loot_table/chests/sanctum.json");
	private static final Path TEMPLATE = DATA.resolve("structure/sanctum.nbt");

	private static final List<String> SANCTUM_BIOMES = List.of(
		"minecraft:lush_caves",
		"minecraft:forest",
		"minecraft:dark_forest");
	private static final String LOOT_TABLE_ID = "attuned:chests/sanctum";

	@Test
	void structureJsonIsJigsawKeyedToTheBiomeTag() throws IOException {
		JsonObject root = json(STRUCTURE);
		assertEquals("minecraft:jigsaw", root.get("type").getAsString(),
			"The sanctum must be a jigsaw structure");
		assertEquals("#attuned:has_sanctum", root.get("biomes").getAsString(),
			"The sanctum must reference its own biome tag");
		assertEquals("attuned:sanctum/main", root.get("start_pool").getAsString(),
			"The sanctum start pool must point at the single-piece pool");
		assertEquals(1, root.get("size").getAsInt(), "The sanctum is a single piece");
		assertEquals("surface_structures", root.get("step").getAsString());
		assertEquals("beard_thin", root.get("terrain_adaptation").getAsString());
	}

	@Test
	void structureSetSpreadsTheSanctum() throws IOException {
		JsonObject placement = json(STRUCTURE_SET).getAsJsonObject("placement");
		assertEquals("minecraft:random_spread", placement.get("type").getAsString());
		assertEquals(48, placement.get("spacing").getAsInt());
		assertEquals(24, placement.get("separation").getAsInt());
		JsonArray structures = json(STRUCTURE_SET).getAsJsonArray("structures");
		assertEquals(1, structures.size(), "Only the sanctum belongs to its set");
		assertEquals("attuned:attunement_sanctum",
			structures.get(0).getAsJsonObject().get("structure").getAsString());
	}

	@Test
	void templatePoolResolvesTheSanctumTemplate() throws IOException {
		JsonArray elements = json(TEMPLATE_POOL).getAsJsonArray("elements");
		assertEquals(1, elements.size(), "The pool has exactly one element");
		JsonObject element = elements.get(0).getAsJsonObject().getAsJsonObject("element");
		assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString());
		assertEquals("attuned:sanctum", element.get("location").getAsString());
		assertEquals("rigid", element.get("projection").getAsString());
	}

	@Test
	void biomeTagListsOnlyThePinnedBiomes() throws IOException {
		JsonObject root = json(BIOME_TAG);
		assertTrue(!root.get("replace").getAsBoolean(),
			"The sanctum biome tag should append, not replace");
		List<String> values = new ArrayList<>();
		for (var value : root.getAsJsonArray("values")) {
			values.add(value.getAsString());
		}
		assertEquals(SANCTUM_BIOMES, values,
			"The sanctum biome tag must list exactly the three pinned biomes");
	}

	@Test
	void lootTableParsesAndIsAChestTable() throws IOException {
		JsonObject root = json(LOOT_TABLE);
		assertEquals("minecraft:chest", root.get("type").getAsString());
		JsonArray pools = root.getAsJsonArray("pools");
		assertTrue(pools.size() >= 1, "The sanctum loot table must have at least one pool");
	}

	@Test
	void templateEmbedsTheSanctumLootTableId() throws IOException {
		byte[] decoded = gunzip(Files.readAllBytes(TEMPLATE));
		String text = new String(decoded, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains(LOOT_TABLE_ID),
			"The generated template must wire its chest to " + LOOT_TABLE_ID);
	}

	private static JsonObject json(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static byte[] gunzip(byte[] raw) throws IOException {
		try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}
}
