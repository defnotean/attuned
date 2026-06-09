package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusSlotContractTest {
	private static final Path FOCUS_SLOT =
		Path.of("src/main/java/dev/attuned/menu/FocusSlot.java");
	private static final Path FOCUS_CONTAINER =
		Path.of("src/main/java/dev/attuned/menu/FocusContainer.java");
	private static final Path CREATIVE_SLOT_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/ServerGamePacketListenerImplMixin.java");

	@Test
	void creativeFocusSlotWritesRespectTheOneItemSlotCap() throws IOException {
		String slot = read(FOCUS_SLOT);
		String mixin = read(CREATIVE_SLOT_MIXIN);

		assertTrue(slot.contains("public int getMaxStackSize()"),
			"Focus slots should expose a one-item slot cap.");
		assertTrue(slot.contains("return 1;"),
			"Focus slots should stay capped at one accessory.");
		assertTrue(mixin.contains("stack.getCount() <= slot.getMaxStackSize()"),
			"Creative focus-slot packets should validate against the Focus slot cap, not the item stack cap.");
		assertTrue(!mixin.contains("stack.getCount() <= stack.getMaxStackSize()"),
			"Creative focus-slot packets must not allow full item stacks into one Focus slot.");
	}

	@Test
	void focusContainerWritesCannotBypassSlotFilterOrStackCap() throws IOException {
		String container = read(FOCUS_CONTAINER);

		assertTrue(container.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"Direct FocusContainer writes should reject non-Focus stacks before storing them.");
		assertTrue(container.contains("AttunedAttachments.setSlot(player, slot, cappedStack(stack));"),
			"Direct FocusContainer writes should pass through a one-item cap helper.");
		assertTrue(container.contains("private ItemStack cappedStack(ItemStack stack)"),
			"The Focus slot stack cap should be centralized in a named helper.");
		assertTrue(container.contains("copy.setCount(Math.min(copy.getCount(), getMaxStackSize()))"),
			"FocusContainer should clamp oversized Focus stacks to the same one-item cap as FocusSlot.");
	}

	@Test
	void focusContainerOnlyStaysValidForItsOwningPlayer() throws IOException {
		String container = read(FOCUS_CONTAINER);

		assertTrue(container.contains("return who == player;"),
			"FocusContainer should not allow another player/menu context to interact with this player's attachment.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
