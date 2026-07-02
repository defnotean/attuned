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
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void payloadsCarryMinimalSanitizedData() throws IOException {
		assertTrue(read(SAVE).contains("record SavePresetPayload(String name)"), "Save carries only a name.");
		assertTrue(read(SAVE).contains("ByteBufCodecs.STRING_UTF8")
				|| read(SAVE).contains("readUtf(32)"), "Save name serializes as UTF-8.");
		assertTrue(read(SAVE).contains(".cast()")
				|| read(SAVE).contains("writeUtf(name)"), "STRING_UTF8 or FriendlyByteBuf serialization must match the branch packet API.");
		assertTrue(read(APPLY).contains("record ApplyPresetPayload(int index)"), "Apply carries only an index.");
		assertTrue(read(DELETE).contains("record DeletePresetPayload(int index, String name)"),
			"Delete carries the selected name as an idempotency guard for duplicate clicks.");
		assertTrue(read(DELETE).contains("ByteBufCodecs.STRING_UTF8")
				|| read(DELETE).contains("readUtf(32)"),
			"Delete name serializes as UTF-8.");
		assertTrue(read(IMPORT).contains("record ImportPresetPayload(FocusPreset preset)"),
			"Import carries the decoded preset so optional setup metadata survives server validation.");
		assertTrue(read(IMPORT).contains("FocusPreset.STREAM_CODEC")
				|| read(IMPORT).contains("FocusPreset.read(buf)") && read(IMPORT).contains("preset.write(buf)"),
			"Import should reuse normalized FocusPreset serialization for slots and setup metadata.");
	}

	@Test
	void presetNetworkingIsIdempotentServerAuthoritativeAndUsesTheResolver() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean initialized;"), "Idempotent init.");
		assertBefore(net, "initialized = true;", serverboundRegistrationNeedle(net));
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(SavePresetPayload.TYPE"), "Save receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(ApplyPresetPayload.TYPE"), "Apply receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(DeletePresetPayload.TYPE"), "Delete receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(ImportPresetPayload.TYPE"), "Import receiver.");
		assertTrue(net.contains("player.getLevel().getServer().execute(")
				|| net.contains("player.getLevel().getServer().execute("), "Server-thread hop.");
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
		assertTrue(net.contains("lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)")
				|| net.contains("registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)"),
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
		assertTrue(net.contains("hasOverflow(satchelStacks) || hasOverflow(displacedEquippedStacks)"),
			"Planner/materializer mismatches should fail before item mutation instead of pushing overflow into player inventory.");
		assertTrue(net.contains("BuiltInRegistries.ITEM.getKey"),
			"Save must capture equipped foci by their registry id.");
		assertTrue(net.contains("AttunedComponents.SATCHEL_CONTENTS")
				|| net.contains("AttunedComponents.setContents(satchelStack"),
			"Apply must write the consumed satchel pool back to the component.");
		assertTrue(net.contains("menu.broadcastChanges()"),
			"Apply must broadcast so the open satchel grid reflects consumed foci.");
	}

	@Test
	void applyFailsClosedBeforeMaterializingStacksWhenAnyPresetTargetIsMissing() throws IOException {
		String net = read(NET);

		assertTrue(net.contains("PresetApplicationResolver.applyAllOrNothing("),
			"Preset apply should use the fail-closed resolver so partial build application cannot move items.");
		assertTrue(net.contains("if (!result.missing().isEmpty())"),
			"Missing or invalid target Foci should be handled before any item-stack mutation.");
		assertBefore(net, "if (!result.missing().isEmpty())", "List<ItemStack> currentEquippedStacks = equippedStacks(player);");
		assertBefore(net, "if (!result.missing().isEmpty())", "AttunedAttachments.setSlot(player");
		assertBefore(net, "screen.attuned.preset.missing", "screen.attuned.preset.applied");
	}

	@Test
	void applyCooldownFeedbackUsesTheSharedActionBarGate() throws IOException {
		String net = read(NET);
		String lang = read(LANG);
		String applyCore = methodBody(net,
			"private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel)");

		assertTrue(net.contains("import dev.attuned.network.ActionBarMessages;"),
			"Apply cooldown feedback should use the shared action-bar priority gate.");
		assertTrue(net.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS"),
			"Apply cooldown feedback should be restrained progress text, not a warning that buries ability feedback.");
		assertTrue(net.contains("\"screen.attuned.preset.cooldown\""),
			"The shared apply cooldown path should use a translatable action-bar message.");
		assertBefore(applyCore, "ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS",
			"Registry<FocusDefinition> registry");
		assertTrue(lang.contains("\"screen.attuned.preset.cooldown\""),
			"The apply cooldown action-bar message should have English copy.");
	}

	@Test
	void presetSuccessFeedbackUsesTheSharedActionBarGate() throws IOException {
		String net = read(NET);
		String importPreset = methodBody(net, "private static void importPreset(ServerPlayer player, ImportPresetPayload payload)");
		String applyCore = methodBody(net,
			"private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel)");

		assertTrue(importPreset.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,\n"
				+ "\t\t\tComponent.translatable(\"screen.attuned.preset.imported\", preset.name()))"),
			"Successful import feedback should be progress-priority server-confirmed action-bar text.");
		assertTrue(applyCore.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,\n"
				+ "\t\t\tComponent.translatable(\"screen.attuned.preset.applied\", presets.get(index).name()))"),
			"Successful apply feedback should be progress-priority server-confirmed action-bar text.");
	}

	@Test
	void saveCapturesInferredSetupMetadataServerSide() throws IOException {
		String net = read(NET);
		String savePreset = methodBody(net, "private static void savePreset(ServerPlayer player, SavePresetPayload payload)");

		assertTrue(net.contains("import dev.attuned.AttunedConfig;"),
			"Preset save/import should read the server-owned suggestion toggle.");
		assertTrue(net.contains("import dev.attuned.menu.PartySetupSuggestions.MemberRole;"),
			"Preset save should use the pure party setup suggestion role rows.");
		assertTrue(net.contains("import dev.attuned.party.CircleRuntime;"),
			"Preset save should read current Circle membership from the server-owned runtime.");
		assertTrue(net.contains("import dev.attuned.menu.PresetMetadataResolver.ResolvedFocus;"),
			"Preset save should use the shared setup metadata resolver facts.");
		assertTrue(savePreset.contains("List<ResolvedFocus> facts"),
			"Preset save should collect active/dormant facts while it walks equipped slots.");
		assertTrue(savePreset.contains("BudgetResolver.Resolution resolution = Attunement.resolution(player);"),
			"Preset save should use the same active/dormant budget resolution that gameplay uses.");
		assertTrue(savePreset.contains("PresetMetadataResolver.infer(facts,\n"
				+ "\t\t\tAttunedConfig.get().partySetupSuggestionsEnabled())"),
			"Freshly saved builds should capture inferred setup metadata while honoring the suggestion toggle.");
		assertTrue(savePreset.contains("metadata = withPartyWarnings(metadata, partySetupWarnings(player, metadata.role()))"),
			"Freshly saved builds should append Circle-aware role/loadout suggestions after the role is inferred.");
		assertTrue(savePreset.contains("new FocusPreset(payload.name(), ids, metadata)"),
			"Server save should persist the inferred metadata with the saved slots.");
		assertTrue(savePreset.contains("hasActiveAbility(player, definition.get(), stack)"),
			"Save should ask a helper whether each active Focus has a real ability.");
		assertTrue(net.contains("AttunedRegistries.getBehavior(behaviorId, player.getLevel().registryAccess())")
				|| net.contains("AttunedRegistries.getBehavior(behaviorId, player.registryAccess())"),
			"Ability warnings should check the resolved server behavior, not just behavior-id presence.");
		assertTrue(net.contains("behavior != null && behavior.hasActiveAbility()"),
			"Only active Focus Ability behaviors should satisfy the no-active-ability setup warning.");
	}

	@Test
	void saveCapturesCircleAwareSetupWarningsWithoutClientAuthority() throws IOException {
		String net = read(NET);
		String partyWarnings = methodBody(net,
			"private static List<String> partySetupWarnings(ServerPlayer player, String candidateRole)");

		assertTrue(partyWarnings.contains("!AttunedConfig.get().partySetupSuggestionsEnabled()"),
			"Circle setup suggestions should honor the server-owned warning toggle.");
		assertTrue(partyWarnings.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"Circle setup suggestions should be based on server-owned Circle membership.");
		assertTrue(partyWarnings.contains("server.getPlayerList().getPlayer(member)"),
			"Circle setup suggestions should only inspect online server players.");
		assertTrue(partyWarnings.contains("new MemberRole(member,"),
			"Circle setup suggestions should pass bounded public role rows to the pure resolver.");
		assertTrue(partyWarnings.contains("member.equals(player.getUUID()) ? candidateRole : Attunement.publicRole(memberPlayer)"),
			"Previewed setup role should replace the saving player's current role before party-fit checks.");
		assertTrue(partyWarnings.contains("PartySetupSuggestions.warnings(player.getUUID(), candidateRole, roles)"),
			"The server wrapper should delegate party-fit policy to the pure suggestion resolver.");
		for (String forbidden : new String[] {"getInventory(", "definitionFor(", "FocusDefinition", "cooldown"}) {
			assertTrue(!partyWarnings.contains(forbidden),
				"Circle setup warnings should not inspect exact Foci or cooldown state directly: " + forbidden);
		}
	}

	@Test
	void importHonorsTheSetupSuggestionToggleWithoutDroppingOtherMetadata() throws IOException {
		String net = read(NET);
		String importPreset = methodBody(net, "private static void importPreset(ServerPlayer player, ImportPresetPayload payload)");

		assertTrue(importPreset.contains("FocusPreset preset = PresetMetadataResolver.withWarningsEnabled(payload.preset(),\n"
				+ "\t\t\tAttunedConfig.get().partySetupSuggestionsEnabled())"),
			"Imported build metadata should preserve non-warning fields while the server can disable advisory warnings.");
		assertTrue(importPreset.contains("AttunedAttachments.savePreset(player, preset)"),
			"Import should save the warning-filtered preset, not the raw client payload.");
		assertTrue(importPreset.contains("Component.translatable(\"screen.attuned.preset.imported\", preset.name())"),
			"Import feedback should describe the exact preset that was accepted.");
	}

	@Test
	void applyIncludesStorageOnlyEquippedStacksInTheResolverPlan() throws IOException {
		String net = read(NET);
		String equippedIds = methodBody(net, "private static List<String> equippedIds(ServerPlayer player)");

		assertTrue(equippedIds.contains("ids.add(keyFor(stack));"),
			"Equipped storage-only stacks must be visible to the pure resolver so they can be moved into the satchel or fail closed.");
		assertTrue(!equippedIds.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"Definitionless equipped stacks should not be hidden until the post-plan materialization phase.");
	}

	@Test
	void applyFailsClosedBeforeMutationWhenMaterializedStacksOverflowThePlan() throws IOException {
		String net = read(NET);

		assertTrue(net.contains("hasOverflow(satchelStacks) || hasOverflow(displacedEquippedStacks)"),
			"Apply should detect any planner/materializer mismatch before mutating slots or reliquary contents.");
		assertBefore(net, "hasOverflow(satchelStacks) || hasOverflow(displacedEquippedStacks)", "AttunedAttachments.setSlot(player");
		assertTrue(!net.contains("placeItemBackInInventory("),
			"Preset apply should not rely on player inventory as a post-mutation overflow sink.");
	}

	@Test
	void importValidatesDecodedFocusIdsServerSideBeforeSaving() throws IOException {
		String net = read(NET);
		String screen = read(SATCHEL_SCREEN);
		String importPreset = methodBody(net, "private static void importPreset(ServerPlayer player, ImportPresetPayload payload)");

		assertTrue(importPreset.contains("PresetImportValidator.validate(payload.preset(), registeredFocusIds(registry))"),
			"Imported build codes, including requires metadata, must be checked against the server's Focus registry before they become saved presets.");
		assertTrue(importPreset.contains("\"screen.attuned.preset.import_missing\""),
			"Unknown imported Foci should produce a readable missing-content warning.");
		assertBefore(importPreset, "PresetImportValidator.validate", "AttunedAttachments.savePreset(player");
		assertBefore(importPreset, "if (!validation.missing().isEmpty())", "AttunedAttachments.savePreset(player");
		assertTrue(importPreset.contains("AttunedAttachments.savePreset(player, preset)"),
			"The server should save the normalized decoded preset after applying the setup-warning toggle.");
		assertTrue(importPreset.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS"),
			"Successful import feedback should come from the server after validation and save.");
		assertTrue(screen.contains("ClientPlayNetworking.send(new ImportPresetPayload(preset))"),
			"The client should send the decoded FocusPreset so setup metadata is not discarded before server validation.");
		assertTrue(!screen.contains("showPresetToast(minecraft, Component.translatable(\"screen.attuned.preset.imported\""),
			"The client should not show an optimistic imported toast before the server validates missing content.");
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
	void applyAndSaveUseComponentSensitivePresetSlotKeysForTemperedFoci() throws IOException {
		String net = read(NET);
		String screen = read(SATCHEL_SCREEN);

		assertTrue(net.contains("FocusPreset.slotKey(idFor(stack), AttunedComponents.isTempered(stack))")
				|| net.contains("FocusPreset.slotKey(idFor(stack), stack.has(AttunedComponents.TEMPERED))"),
			"Saving a preset should remember whether the equipped Focus stack was Tempered.");
		assertTrue(net.contains("FocusPreset.slotId("),
			"Apply preflight should validate component-sensitive slot keys through their base Focus id.");
		assertTrue(net.contains("keyFor(stack)"),
			"Apply materialization should source stacks by exact preset key, not by bare item id.");
		assertTrue(net.contains("FocusPreset.slotKey(idFor(stack), AttunedComponents.isTempered(stack))")
				|| net.contains("FocusPreset.slotKey(idFor(stack), stack.has(AttunedComponents.TEMPERED))"),
			"Every real stack pool should distinguish tempered from untempered copies.");
		assertTrue(screen.contains("FocusPreset.slotId(id)"),
			"The client preview should strip the preset key qualifier before resolving an item icon.");
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

	@Test
	void menuApplyAndQuickApplyShareOneServerSideAtomicApplyGate() throws IOException {
		String net = read(NET);
		String menuApply = methodBody(net, "private static void applyPreset(ServerPlayer player, ApplyPresetPayload payload)");
		String quickApply = methodBody(net,
			"private static void quickApplyPreset(ServerPlayer player, QuickApplyPresetPayload payload)");
		String applyCore = methodBody(net,
			"private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel)");

		assertTrue(net.contains("private static final int APPLY_COOLDOWN_TICKS = 5"),
			"Menu and hotkey apply should share one short server-side same-tick/race dampener.");
		assertTrue(menuApply.contains("applyPresetCore(player, payload.index(), satchel)"),
			"Open-menu apply should delegate to the shared atomic apply core.");
		assertTrue(quickApply.contains("applyPresetCore(player, payload.index(), inventorySatchelState(player))"),
			"Hotkey apply should delegate to the same atomic apply core, with only the satchel lookup differing.");
		assertBefore(applyCore, "Long last = LAST_APPLY_TICK.get(id)", "Registry<FocusDefinition> registry");
		assertBefore(applyCore, "Long last = LAST_APPLY_TICK.get(id)",
			"PresetApplicationResolver.applyAllOrNothing(");
		assertBefore(applyCore, "removeConsumedInventory(player, consumedInventory)", "LAST_APPLY_TICK.put(id, now)");
		assertBefore(applyCore, "LAST_APPLY_TICK.put(id, now)", "Component.translatable(\"screen.attuned.preset.applied\"");
		assertTrue(applyCore.contains("LAST_APPLY_TICK.put(id, now)"),
			"The cooldown stamp should be written by the same core after a successful materialized mutation.");
	}

	@Test
	void applyWarnsWhenBuildIsOverCapacityOrLacksActiveAbility() throws IOException {
		String net = read(NET);
		String lang = read(LANG);
		String applyCore = methodBody(net,
			"private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel)");
		String warn = methodBody(net, "private static void warnApplyResolutionIssues(ServerPlayer player)");

		assertTrue(applyCore.contains("warnApplyResolutionIssues(player)"),
			"Successful apply should check the post-apply budget resolution.");
		assertBefore(applyCore, "Component.translatable(\"screen.attuned.preset.applied\"",
			"warnApplyResolutionIssues(player)");
		assertTrue(warn.contains("Attunement.resolution(player)"),
			"Apply warnings should use the same budget resolution as gameplay.");
		assertTrue(warn.contains("PresetMetadataResolver.infer(facts).warnings()"),
			"Apply warnings should reuse the shared setup metadata warning policy.");
		assertTrue(warn.contains("ActionBarMessages.Priority.WARNING"),
			"Over-capacity and missing-ability warnings should use the warning priority gate.");
		assertTrue(warn.contains("\"screen.attuned.preset.apply_over_capacity\""),
			"Over-capacity apply feedback should be translatable.");
		assertTrue(warn.contains("\"screen.attuned.preset.apply_no_active_ability\""),
			"Missing active-ability apply feedback should be translatable.");
		assertTrue(lang.contains("\"screen.attuned.preset.apply_over_capacity\""),
			"Over-capacity apply warning should have English copy.");
		assertTrue(lang.contains("\"screen.attuned.preset.apply_no_active_ability\""),
			"Missing active-ability apply warning should have English copy.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String serverboundRegistrationNeedle(String source) {
		return source.contains("ServerPlayNetworking.registerGlobalReceiver")
			? "ServerPlayNetworking.registerGlobalReceiver"
			: source.contains("PayloadTypeRegistry.serverboundPlay().register")
			? "PayloadTypeRegistry.serverboundPlay().register"
			: source.contains("PayloadTypeRegistry.playC2S().register")
			? "PayloadTypeRegistry.playC2S().register"
			: "PayloadTypeRegistry.playC2S().register";
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

	private static String methodBody(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, "Expected method: " + signature);
		int brace = source.indexOf('{', start);
		assertTrue(brace >= 0, "Expected method body: " + signature);
		int depth = 0;
		for (int i = brace; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(brace, i + 1);
				}
			}
		}
		throw new AssertionError("Unclosed method body: " + signature);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
