package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelNetworkingContractTest {
	private static final Path PAYLOAD = Path.of("src/main/java/dev/attuned/menu/MoveFocusPayload.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/SatchelNetworking.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void moveFocusPayloadCarriesSanitizedSlots() throws IOException {
		String payload = read(PAYLOAD);
		assertTrue(payload.contains("record MoveFocusPayload(int direction, int satchelSlot, int equippedSlot)"),
			"Payload should carry direction + both slot indices.");
		assertTrue(payload.contains("StreamCodec.composite"), "Payload should use a composite codec.");
		assertTrue(payload.contains("ByteBufCodecs.VAR_INT"), "Payload ints should serialize as VAR_INT.");
		assertTrue(payload.contains(".cast()"),
			"A composite over ByteBufCodecs.* is ByteBuf-typed; cast() to the RegistryFriendlyByteBuf field type.");
		assertTrue(payload.contains("public MoveFocusPayload {"),
			"Payload should validate/clamp in its canonical constructor.");
	}

	@Test
	void moveFocusPayloadRejectsMalformedDirections() throws IOException {
		assertEquals(0, new MoveFocusPayload(0, 0, 0).direction(),
			"Direction 0 is the legitimate satchel-to-equipped move.");
		assertEquals(1, new MoveFocusPayload(1, 0, 0).direction(),
			"Direction 1 is the legitimate equipped-to-satchel move.");
		assertEquals(-1, new MoveFocusPayload(-2, 0, 0).direction(),
			"Negative malformed directions should become inert.");
		assertEquals(-1, new MoveFocusPayload(2, 0, 0).direction(),
			"Unexpected positive directions should become inert.");

		String payload = read(PAYLOAD);
		String net = read(NET);
		assertTrue(payload.contains("direction != 0 && direction != 1"),
			"Payload direction sanitizer should reject anything outside the two legal move directions.");
		assertTrue(payload.contains("direction = -1"),
			"Invalid directions should use the same negative sentinel style as invalid slots.");
		assertTrue(net.contains("payload.direction() < 0 || payload.satchelSlot() < 0 || payload.equippedSlot() < 0"),
			"The receiver should reject invalid direction sentinels before resolving a move.");
		assertTrue(net.contains("payload.direction() > 1"),
			"The server receiver should defensively reject unexpected positive directions too.");
	}

	@Test
	void receiverIsIdempotentServerAuthoritativeRateLimitedAndUsesTheResolver() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean initialized;"), "Networking should be idempotent.");
		assertBefore(net, "initialized = true;", "PayloadTypeRegistry.serverboundPlay().register");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(MoveFocusPayload.TYPE"),
			"Networking should register the move receiver.");
		assertTrue(net.contains("player.level().getServer().execute("), "Handlers must hop to the server thread.");
		assertTrue(net.contains("player.containerMenu instanceof SatchelMenu"),
			"Handler must validate the satchel menu is open.");
		assertTrue(net.contains("player.getItemInHand(menu.hand())"),
			"Handler must re-read the held satchel live via the menu's hand (same instance as the container).");
		assertTrue(net.contains("payload.satchelSlot() < 0 || payload.equippedSlot() < 0"),
			"Handler must reject negative sentinel slots before any container access.");
		assertTrue(net.contains("SatchelMoveResolver."),
			"Handler must delegate the swap/overflow decision to the pure resolver.");
		assertTrue(net.contains("AttunedAttachments.setSlot(player"),
			"Equip side must write through the validated setSlot boundary.");
		assertTrue(net.contains("AttunedComponents.SATCHEL_CONTENTS"),
			"Satchel side must write through the contents component.");
		assertTrue(net.contains("player.level().getGameTime()"),
			"Rate limit must use server-level game time.");
		assertTrue(net.contains("AttunedPlayerCleanup.onForget"), "Rate-limit map cleaned on disconnect.");
		assertTrue(net.contains("AttunedServerCleanup.onStop"), "Rate-limit map cleared on server stop.");
		assertTrue(net.contains("Attunement.definitionFor(player"),
			"Handler must re-validate the moved stack is a real Focus.");
		assertTrue(net.contains("menu.broadcastChanges()"), "Handler must broadcast after a successful move.");
		assertTrue(read(BOOTSTRAP).contains("SatchelNetworking.init()"), "Bootstrap wiring.");
	}

	@Test
	void receiverPreservesMovedItemStacksInsteadOfRebuildingBareRegistryItems() throws IOException {
		String net = read(NET);
		assertTrue(!net.contains("new ItemStack(item)"),
			"Satchel moves must preserve existing ItemStack components such as custom names, not rebuild bare items.");
		assertTrue(net.contains("holder.get(payload.satchelSlot()).copy()"),
			"Moving from the satchel should carry the actual stored stack into the equipped slot.");
		assertTrue(net.contains("AttunedAttachments.getInventory(player).get(payload.equippedSlot()).copy()"),
			"Swapping with an equipped Focus should carry the actual equipped stack back into the satchel.");
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
