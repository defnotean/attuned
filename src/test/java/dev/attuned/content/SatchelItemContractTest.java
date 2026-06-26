package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelItemContractTest {
	private static final Path ITEM = Path.of("src/main/java/dev/attuned/content/SatchelItem.java");
	private static final Path CONTENT = Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path TABS = Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path RECIPE = Path.of("src/main/resources/data/attuned/recipe/satchel_of_foci.json");

	@Test
	void satchelItemAttachesEmptyContentsAndStacksToOne() throws IOException {
		String item = read(ITEM);
		assertTrue(item.contains("class SatchelItem extends Item"),
			"SatchelItem should be a custom Item subclass.");
		assertTrue(item.contains("public SatchelItem(Item.Properties properties)") || item.contains("public SatchelItem(Properties properties)"),
			"SatchelItem must take a single Item.Properties arg to satisfy the register helper's Function<Properties,Item>.");
		assertTrue(item.contains("properties.stacksTo(1)"),
			"A bag holding a per-stack component must not stack.");
		// The small-satchel public constructor delegates to the parameterized constructor with
		// the SATCHEL_CONTENTS component and its empty default, so both tiers share one class.
		assertTrue(item.contains("AttunedComponents.SATCHEL_CONTENTS")
				&& item.contains("AttunedComponents.emptyContents()"),
			"The small satchel should attach the empty SATCHEL_CONTENTS component by default.");
		assertTrue(item.contains(".component(contentsType, emptyContents)"),
			"The satchel should attach an empty contents component by default, inside its constructor.");
		assertTrue(item.contains("public InteractionResult use(Level level, Player player, InteractionHand hand)")
				|| item.contains("public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)"),
			"The satchel should open its screen on use.");
	}

	@Test
	void satchelIsRegisteredAsAStandardPublicItemInTheUtilityTab() throws IOException {
		String content = read(CONTENT);
		assertTrue(content.contains("public static final DeferredItem<Item> SATCHEL_OF_FOCI = register(\"satchel_of_foci\", SatchelItem::new);"),
			"Satchel should be a public field using the plain register helper, never registerFocus.");
		String tabs = read(TABS);
		int journal = tabs.indexOf("output.accept(AttunedContent.ATTUNEMENT_JOURNAL.get())");
		int satchel = tabs.indexOf("output.accept(AttunedContent.SATCHEL_OF_FOCI.get())");
		assertTrue(journal >= 0 && satchel >= 0 && Math.abs(journal - satchel) < 400,
			"Satchel should appear in the utility tab's includeCoreItems block, near the journal.");
	}

	@Test
	void satchelHasLangAndRecipe() throws IOException {
		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("item.attuned.satchel_of_foci"), "Satchel item needs a display name.");
		assertTrue(lang.has("container.attuned.satchel"), "Satchel menu needs a window title key.");
		JsonObject recipe = JsonParser.parseString(read(RECIPE)).getAsJsonObject();
		assertTrue(recipe.getAsJsonObject("result").get("id").getAsString().equals("attuned:satchel_of_foci"),
			"Recipe should produce the satchel item.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
