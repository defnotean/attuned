package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RecipeDataContractTest {
	private static final Path RECIPE_DIR = Path.of("src/main/resources/data/attuned/recipe");

	@Test
	void minecraft1211RecipesUseObjectIngredients() throws IOException {
		Set<String> shorthandIngredients = new TreeSet<>();
		try (Stream<Path> paths = Files.list(RECIPE_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				if (root.has("key")) {
					for (var entry : root.getAsJsonObject("key").entrySet()) {
						if (entry.getValue().isJsonPrimitive()) {
							shorthandIngredients.add(file + " key " + entry.getKey());
						}
					}
				}
				if (root.has("ingredients")) {
					int index = 0;
					for (JsonElement ingredient : root.getAsJsonArray("ingredients")) {
						if (ingredient.isJsonPrimitive()) {
							shorthandIngredients.add(file + " ingredients[" + index + "]");
						}
						index++;
					}
				}
			}
		}

		assertEquals(Set.of(), shorthandIngredients,
			"Minecraft 1.21.1 rejects recipe ingredient string shorthand; use {\"item\": \"...\"} objects");
	}

	@Test
	void recipeResultsUseModernIdField() throws IOException {
		try (Stream<Path> paths = Files.list(RECIPE_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject result = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject()
					.getAsJsonObject("result");
				assertTrue(result.has("id"), "Minecraft 1.21.1 recipes should use result.id: " + file);
				assertTrue(!result.has("item"), "Minecraft 1.21.1 recipes should not use legacy result.item: " + file);
			}
		}
	}
}
