package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PresetNetworkingContractTest {
	private static final Path SAVE = Path.of("src/main/java/dev/attuned/menu/SavePresetPayload.java");
	private static final Path APPLY = Path.of("src/main/java/dev/attuned/menu/ApplyPresetPayload.java");
	private static final Path DELETE = Path.of("src/main/java/dev/attuned/menu/DeletePresetPayload.java");
	private static final Path IMPORT = Path.of("src/main/java/dev/attuned/menu/ImportPresetPayload.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/PresetNetworking.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path SATCHEL_SCREEN = Path.of("src/client/java/dev/attuned/client/screen/SatchelScreen.java");

	@Test
	void payloadsCarryMinimalSanitizedData() throws IOException {
		assertTrue(read(SAVE).contains("record SavePresetPayload(String name)"), "Save carries only a name.");
		assertTrue(read(SAVE).contains("ByteBufCodecs.STRING_UTF8"), "Save name serializes as UTF-8.");
		assertTrue(read(SAVE).contains(".cast()"), "STRING_UTF8 composite must cast to the RegistryFriendlyByteBuf field type.");
		assertTrue(read(APPLY).contains("record ApplyPresetPayload(int index)"), "Apply carries only an index.");
		assertTrue(read(DELETE).contains("record DeletePresetPayload(int index, String name)"),
			"Delete carries the selected name as an idempotency guard for duplicate clicks.");
		assertTrue(read(DELETE).contains("ByteBufCodecs.STRING_UTF8"),
			"Delete name serializes as UTF-8.");
		assertTrue(read(IMPORT).contains("record ImportPresetPayload(String name, List<String> slots)"),
			"Import carries the decoded preset name and slot ids.");
		assertTrue(read(IMPORT).contains("ByteBufCodecs.STRING_UTF8"),
			"Import serializes strings as UTF-8.");
	}

	@Test
	void presetNetworkingIsIdempotentServerAuthoritativeAndUsesTheResolver() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean initialized;"), "Idempotent init.");
		assertBefore(net, "initialized = true;", "PayloadTypeRegistry.serverboundPlay().register");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(SavePresetPayload.TYPE"), "Save receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(ApplyPresetPayload.TYPE"), "Apply receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(DeletePresetPayload.TYPE"), "Delete receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(ImportPresetPayload.TYPE"), "Import receiver.");
		assertTrue(net.contains("player.level().getServer().execute("), "Server-thread hop.");
		assertTrue(net.contains("PresetApplicationResolver."), "Apply must delegate to the pure resolver.");
		assertTrue(read(BOOTSTRAP).contains("PresetNetworking.init()"), "Bootstrap wiring.");
	}

	@Test
	void presetMutationsRequireTheOpenLiveSatchelMenu() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean hasOpenLiveSatchel(ServerPlayer player)"),
			"Preset mutation packets should share one open-menu/live-held-satchel guard.");
		assertTrue(net.contains("player.containerMenu instanceof SatchelMenu menu"),
			"Preset mutation packets should only apply while the satchel menu is open.");
		// The open-menu guard re-reads the held reliquary from the menu hand; the per-tier
		// item check (small satchel or Grand Reliquary) is centralized in isReliquary().
		assertTrue(net.contains("isReliquary(player.getItemInHand(menu.hand()))"),
			"Preset mutation packets should re-read the held reliquary from the menu hand before mutating.");
		assertTrue(net.contains("stack.getItem() == AttunedContent.SATCHEL_OF_FOCI"),
			"isReliquary should still recognize the small Focus Reliquary item.");
		assertEquals(4, countOccurrences(net, "if (!hasOpenLiveSatchel(player))"),
			"Save, apply, delete, and import should all reject spoofed/out-of-menu preset packets.");
	}

	@Test
	void applyAndSaveResolveAgainstTheRegistryAndPreserveAvailableStacks() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)"),
			"Apply must resolve focus ids against the world registry server-side.");
		assertTrue(net.contains("SatchelState satchel = satchelState(player);"),
			"Apply should preserve stored satchel stacks even when a Focus definition failed to load.");
		assertTrue(net.contains("private static SatchelState satchelState(ServerPlayer player)"),
			"Satchel component reads should not need registry context that would erase definitionless Foci.");
		assertTrue(net.contains("ids.add(id);") && net.contains("stacks.add(stack);"),
			"Definitionless satchel stacks should survive preset apply writeback.");
		assertTrue(!net.contains("ids.add(registeredFocusIds.contains(id) ? id : \"\");"),
			"Preset apply must not erase unregistered satchel ids before the resolver can preserve them.");
		assertTrue(net.contains("AttunedAttachments.setSlot(player"),
			"Apply must re-equip through the validated setSlot boundary.");
		assertTrue(!net.contains("new ItemStack(item)"),
			"Preset apply must preserve existing ItemStack components instead of rebuilding bare items.");
		assertTrue(net.contains("availableSatchelStacks(satchel, registeredFocusIds)"),
			"Preset apply should source actual stacks from the satchel without merging equipped stacks first.");
		assertTrue(net.contains("availableDisplacedEquippedStacks(currentEquippedStacks, result.equips(), registeredFocusIds)"),
			"Preset apply should source displaced equipped stacks only after preserving same-slot matches.");
		assertTrue(net.contains("availableInventoryStacks(player, registeredFocusIds)"),
			"Preset apply should source actual inventory stacks only after satchel/equipped stacks.");
		assertTrue(net.contains("removeConsumedInventory(player, consumedInventory)"),
			"Inventory sources should remove the exact consumed stacks after materializing the preset.");
		assertTrue(net.contains("returnOverflowToInventory(player, satchelStacks)"),
			"Displaced Foci that do not fit back into the satchel should return to inventory with components intact.");
		assertTrue(net.contains("BuiltInRegistries.ITEM.getKey"),
			"Save must capture equipped foci by their registry id.");
		assertTrue(net.contains("AttunedComponents.SATCHEL_CONTENTS"),
			"Apply must write the consumed satchel pool back to the component.");
		assertTrue(net.contains("menu.broadcastChanges()"),
			"Apply must broadcast so the open satchel grid reflects consumed foci.");
	}

	@Test
	void deleteRequiresTheSelectedPresetNameSoDuplicateClicksCannotDeleteTheShiftedNextPreset() throws IOException {
		String net = read(NET);
		String screen = read(SATCHEL_SCREEN);

		assertTrue(screen.contains("new DeletePresetPayload(selectedIndex, presets.get(selectedIndex).name())"),
			"Client delete should send the name currently shown at the selected index.");
		assertTrue(screen.contains("if (selectedIndex >= 0 && selectedIndex < presets.size())"),
			"Client delete should re-validate the selection against the live synced list before sending.");
		assertTrue(net.contains("List<FocusPreset> presets = AttunedAttachments.getPresets(player);"),
			"Server delete should re-read the authoritative preset list.");
		assertTrue(net.contains("if (!presets.get(payload.index()).name().equals(payload.name()))"),
			"A stale duplicate delete packet should no-op once another preset shifts into that index.");
		assertTrue(net.contains("AttunedAttachments.deletePreset(player, payload.index());"),
			"Deletion should still route through the attachment helper after validation.");
	}

	@Test
	void applyPreservesSameSlotEquippedStacksBeforeConsumingDuplicateSatchelCopies() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("List<ItemStack> currentEquippedStacks = equippedStacks(player);"),
			"Apply should snapshot current equipped stacks separately so same-slot matches can keep their components.");
		assertTrue(net.contains("availableSatchelStacks(satchel, registeredFocusIds)"),
			"Satchel stacks should stay in their own source pool instead of being merged with equipped stacks first.");
		assertTrue(net.contains("availableDisplacedEquippedStacks(currentEquippedStacks, result.equips(), registeredFocusIds)"),
			"Only non-preserved equipped stacks should become displaced sources for other target slots.");
		assertTrue(net.contains("matchingEquippedStack(currentEquippedStacks, slot, id)"),
			"Materialization should try the same equipped slot before consuming a duplicate satchel copy.");
		assertBefore(net, "matchingEquippedStack(currentEquippedStacks, slot, id)", "takeStack(satchelStacks, id)");
		assertTrue(!net.contains("availableSatchelAndEquippedStacks"),
			"Preset apply should not merge satchel and equipped stacks before same-slot preservation.");
	}

	@Test
	void applyPreservesDefinitionlessSatchelAndEquippedStacksAsStorageOnlyItems() throws IOException {
		String net = read(NET);

		assertTrue(net.contains("List<ItemStack> currentEquippedStacks = equippedStacks(player);"),
			"Current equipped stacks must be snapshotted without registry filtering so definitionless Foci can be displaced.");
		assertTrue(net.contains("private static List<ItemStack> equippedStacks(ServerPlayer player)"),
			"Equipped stack snapshots should not need the definition registry.");
		assertTrue(net.contains("stacks.add(stack.copy());"),
			"Definitionless equipped stacks should remain available for satchel/inventory return.");
		assertTrue(net.contains("addStack(stacks, stack);"),
			"Satchel and displaced equipped source pools should keep any stored stack by id.");
		assertTrue(!net.contains("private static void addStack(Map<String, Deque<ItemStack>> stacks, ItemStack stack,\n"
				+ "\t\t\tSet<String> registeredFocusIds)"),
			"Storage-only source pools must not filter through registered Focus definitions.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static int countOccurrences(String source, String needle) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
