package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelMenuContractTest {
	private static final Path CONTAINER = Path.of("src/main/java/dev/attuned/menu/SatchelContainer.java");
	private static final Path MENU = Path.of("src/main/java/dev/attuned/menu/SatchelMenu.java");
	private static final Path TYPE = Path.of("src/main/java/dev/attuned/menu/SatchelMenuType.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void satchelContainerReadsLiveHeldStackAndIsFociOnly() throws IOException {
		String container = read(CONTAINER);
		assertTrue(container.contains("implements Container"),
			"SatchelContainer should adapt the held stack to a Container.");
		assertTrue(container.contains("player.getItemInHand(hand)"),
			"SatchelContainer must resolve the held satchel live via the hand, never a cached ItemStack.");
		assertTrue(container.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"SatchelContainer.setItem must reject non-Focus stacks like FocusContainer.");
		assertTrue(container.contains("private ItemStack focusStackAt(int slot)"),
			"SatchelContainer reads should centralize Focus validation for stored component stacks.");
		assertTrue(container.contains("return focusStackAt(slot);"),
			"getItem should hide malformed non-Focus component contents from the menu.");
		assertTrue(container.contains("if (!focusStackAt(i).isEmpty())"),
			"isEmpty should ignore malformed non-Focus component contents.");
		// The container is now parameterized by the contents component type so the same
		// class backs both the small satchel and the Grand Focus Reliquary tiers.
		assertTrue(container.contains("satchel().set(contentsType"),
			"Writes must persist the holder back into the live held stack's component.");
		assertTrue(container.contains("satchel().get(contentsType"),
			"Reads must pull the holder from the live held stack's component.");
		assertTrue(container.contains("DataComponentType<FocusHolder> contentsType"),
			"The component type must be a per-instance field so each tier reads its own holder.");
		assertTrue(container.contains("private boolean hasLiveSatchel()"),
			"Container reads/writes should refuse to touch a hand stack after the satchel is swapped out.");
		assertTrue(container.contains("return satchel().getItem() == reliquaryItem;"),
			"The live-hand guard should validate the held item (either reliquary tier) before component access.");
		assertTrue(container.contains("return who == player && hasLiveSatchel();"),
			"Container validity should match the same live-hand satchel guard as the menu.");
		assertTrue(container.contains("public int getMaxStackSize()"),
			"Satchel slots hold single-count Foci.");
		assertTrue(container.contains("public void setChanged()"),
			"setChanged must be declared (a documented no-op; write-through happens in the mutators).");
	}

	@Test
	void satchelMenuValidatesHeldStackRefusesNestingAndRoutesFociOnly() throws IOException {
		String menu = read(MENU);
		assertTrue(menu.contains("extends AbstractContainerMenu"),
			"SatchelMenu should be a real container menu.");
		assertTrue(menu.contains("public SatchelMenu(int containerId, Inventory inventory)"),
			"A client (int, Inventory) constructor is required by the MenuType factory.");
		assertTrue(menu.contains("addStandardInventorySlots") || menu.contains("addPlayerInventorySlots"),
			"SatchelMenu should expose the player inventory for transfers.");
		assertTrue(menu.contains("public boolean stillValid(Player player)"),
			"SatchelMenu must validate the satchel is still in hand.");
		assertTrue(menu.contains("private final Player owner;"),
			"SatchelMenu should remember the inventory owner it was opened for.");
		assertTrue(menu.contains("private final boolean handUnknown;"),
			"The client-side menu constructor should record that it does not know the opening hand.");
		// The client (int, Inventory) constructor still uses a hand-unknown fallback; it now
		// also threads the registered menu type so the small/grand tiers stay distinct.
		assertTrue(menu.contains("new SimpleContainer(AttunedComponents.SATCHEL_SIZE)")
				&& menu.contains("InteractionHand.MAIN_HAND, true, SatchelMenuType.TYPE"),
			"The client constructor should use a hand-unknown fallback instead of treating MAIN_HAND as authoritative.");
		assertTrue(menu.contains("this(containerId, inventory, satchel, hand, false, type);"),
			"The server/provider constructor should keep validating the known interaction hand.");
		assertTrue(menu.contains("return player == owner && hasLiveSatchel(player);"),
			"SatchelMenu validity should reject other player contexts as well as a missing held satchel.");
		assertTrue(menu.contains("private boolean hasLiveSatchel(Player player)"),
			"Validity should centralize known-hand and unknown-hand checks.");
		assertTrue(menu.contains("if (handUnknown)"),
			"The client fallback should explicitly branch when the opening hand is not known.");
		// Unknown-hand validity accepts a reliquary (either tier) in either hand; the
		// per-tier item check is centralized in isReliquary().
		assertTrue(menu.contains("isReliquary(player.getMainHandItem())")
				&& menu.contains("isReliquary(player.getOffhandItem())"),
			"Unknown-hand validity should accept a reliquary in either hand for offhand opens.");
		assertTrue(menu.contains("stack.getItem() == AttunedContent.SATCHEL_OF_FOCI.get()"),
			"isReliquary should still recognize the small Focus Reliquary item.");
		assertTrue(menu.contains("getItemInHand"),
			"Validity should check the held satchel stack rather than a block position.");
		assertTrue(menu.contains("stack.getItem() != AttunedContent.SATCHEL_OF_FOCI.get()"),
			"Satchel slots must refuse the satchel item itself (no nested-bag duplication).");
	}

	@Test
	void satchelMenuTypeRegistersInsideGuardAndProvidesFromHand() throws IOException {
		String type = read(TYPE);
		assertTrue(type.contains("private static boolean initialized;"),
			"MenuType registration should be idempotent.");
		assertTrue(type.contains("initialized = true;"),
			"MenuType registration should set its guard before registering.");
		assertBefore(type, "initialized = true;", "NeoForgeDeferredRegistries.menu");
		assertTrue(type.contains("new MenuType<>(SatchelMenu::new, FeatureFlags.VANILLA_SET)"),
			"SatchelMenuType should use the plain MenuType ctor like AltarMenuType.");
		assertTrue(type.contains("public static MenuProvider provider(Player player, InteractionHand hand)"),
			"Provider should close over the player's hand, not a BlockPos.");
		assertTrue(read(BOOTSTRAP).contains("SatchelMenuType.init()"),
			"SatchelMenuType must be initialized at bootstrap.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
