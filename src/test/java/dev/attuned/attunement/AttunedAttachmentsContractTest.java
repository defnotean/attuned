package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedAttachmentsContractTest {
	private static final Path ATTACHMENTS =
		Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");
	private static final Path COMMANDS =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");

	@Test
	void capacityReadsAndWritesRespectConfiguredCap() throws IOException {
		String attachments = read(ATTACHMENTS);

		assertTrue(attachments.contains("return clampCapacity(player.getAttachedOrElse(CAPACITY, 0));"),
			"Capacity reads should clamp old persisted values to the configured cap.");
		assertTrue(attachments.contains("player.setAttached(CAPACITY, clampCapacity(value));"),
			"Capacity writes should not persist values above the configured cap.");
		assertTrue(attachments.contains("private static int clampCapacity(int value)"),
			"Capacity clamping should be centralized in a named helper.");
		assertTrue(attachments.contains("Math.min(AttunedConfig.get().capacityCap(), Math.max(0, value))"),
			"Capacity clamping should preserve the zero floor and configured cap ceiling.");
	}

	@Test
	void capacityCommandReportsTheClampedValue() throws IOException {
		String commands = read(COMMANDS);

		assertTrue(commands.contains("AttunedAttachments.setCapacity(player, amount);"),
			"The capacity command should still write through the attachment boundary.");
		assertTrue(commands.contains("int capacity = AttunedAttachments.getCapacity(player);"),
			"The capacity command should read back the actual clamped value.");
		assertTrue(commands.contains("\"Attunement capacity set to \" + capacity"),
			"The capacity command should report the value that was actually stored.");
		assertTrue(commands.contains("return capacity;"),
			"The capacity command should return the clamped value to command automation.");
	}

	@Test
	void resonanceReadsAndWritesClampToGaugeBounds() throws IOException {
		String attachments = read(ATTACHMENTS);

		assertTrue(attachments.contains("return clampResonance(player.getAttachedOrElse(RESONANCE, 0.0F));"),
			"Resonance reads should sanitize old persisted values before gameplay logic sees them.");
		assertTrue(attachments.contains("player.setAttached(RESONANCE, clampResonance(value));"),
			"Resonance writes should not persist values outside the gauge bounds.");
		assertTrue(attachments.contains("private static float clampResonance(float value)"),
			"Resonance clamping should be centralized in a named helper.");
		assertTrue(attachments.contains("if (!Float.isFinite(value))"),
			"Resonance clamping should reject NaN and infinity before numeric bounds checks.");
		assertTrue(attachments.contains("return Math.min(1.0F, Math.max(0.0F, value));"),
			"Resonance clamping should preserve the zero floor and one ceiling.");
	}

	@Test
	void focusSlotWritesIgnoreInvalidSlotsAndNormalizeStacks() throws IOException {
		String attachments = read(ATTACHMENTS);

		assertTrue(attachments.contains("if (slot < 0 || slot >= AttunedInv.SIZE)"),
			"Attachment slot writes should ignore out-of-range slot indices instead of throwing.");
		assertTrue(attachments.contains("if (stack == null || stack.isEmpty())"),
			"Attachment slot writes should treat null or empty stacks as an explicit clear.");
		assertTrue(attachments.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"Attachment slot writes should reject non-Focus stacks at the public boundary.");
		assertTrue(attachments.contains("getInventory(player).with(slot, cappedSlotStack(stack))"),
			"Attachment slot writes should normalize valid Focus stacks before persisting them.");
		assertTrue(attachments.contains("private static ItemStack cappedSlotStack(ItemStack stack)"),
			"Focus slot stack normalization should be centralized in a named helper.");
		assertTrue(attachments.contains("copy.setCount(Math.min(copy.getCount(), 1))"),
			"Attachment slot writes should preserve the one-Focus-per-slot invariant.");
	}

	@Test
	void listAttachmentIdsAreNormalizedBeforeReadsAndWrites() throws IOException {
		String attachments = read(ATTACHMENTS);

		assertTrue(attachments.contains("import java.util.Optional;"),
			"Milestone and onboarding ids should use an explicit absent-id type.");
		assertTrue(attachments.contains("private static Optional<String> normalizedAttachmentId(String id)"),
			"Milestone and onboarding id normalization should be centralized in a named helper.");
		assertTrue(attachments.contains("String normalized = id.trim();"),
			"List attachment ids should trim accidental whitespace before persisting or checking them.");
		assertTrue(attachments.contains("return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);"),
			"Blank list attachment ids should be rejected instead of persisted.");
		assertTrue(attachments.contains("normalizedAttachmentId(id)"),
			"Milestone and onboarding APIs should normalize ids before touching persistent attachments.");
		assertTrue(attachments.contains(".orElse(false)"),
			"Milestone and onboarding reads should report invalid ids as unseen instead of throwing.");
		assertTrue(attachments.contains("player.setAttached(MILESTONES, List.copyOf(updated));"),
			"Milestone writes should persist an immutable defensive snapshot.");
		assertTrue(attachments.contains("player.setAttached(ONBOARDING, List.copyOf(updated));"),
			"Onboarding writes should persist an immutable defensive snapshot.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
