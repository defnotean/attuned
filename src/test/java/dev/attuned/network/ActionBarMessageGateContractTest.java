package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for prioritized action-bar messages. */
class ActionBarMessageGateContractTest {
	private static final Path ACTION_BAR =
		Path.of("src/main/java/dev/attuned/network/ActionBarMessages.java");
	private static final Path FOCUS_ABILITY =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path PACT_TACTICALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTacticals.java");
	private static final Path SURGES =
		Path.of("src/main/java/dev/attuned/combat/ResonantSurges.java");
	private static final Path COMBAT_FEEDBACK =
		Path.of("src/main/java/dev/attuned/combat/CombatFeedback.java");
	private static final Path APEX =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path UPDRAFT =
		Path.of("src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java");
	private static final Path HARPOON =
		Path.of("src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java");
	private static final Path UNSEEN =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path SYNERGIES =
		Path.of("src/main/java/dev/attuned/synergy/Synergies.java");
	private static final Path PACT_TRIALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTrials.java");
	private static final Path PRESET_NETWORKING =
		Path.of("src/main/java/dev/attuned/menu/PresetNetworking.java");
	private static final Path ONBOARDING =
		Path.of("src/main/java/dev/attuned/onboarding/Onboarding.java");
	private static final Path ATTUNED_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");

	@Test
	void actionBarGateDefinesPrioritiesAndCleansPerPlayerState() throws IOException {
		String source = read(ACTION_BAR);

		assertTrue(source.contains("enum Priority"),
			"Action-bar gating should use named priority tiers.");
		assertTrue(source.contains("AMBIENT") && source.contains("PROGRESS")
				&& source.contains("ABILITY") && source.contains("WARNING")
				&& source.contains("CRITICAL"),
			"Priority tiers should cover noisy ambience, progress, abilities, warnings, and critical combat.");
		assertTrue(source.contains("AttunedPlayerCleanup.onForget(LAST_SENT::remove)"),
			"The message gate should clean per-player state on disconnect.");
		assertTrue(source.contains("AttunedServerCleanup.onStop(LAST_SENT::clear)"),
			"The message gate should clean all state on server stop.");
		assertTrue(source.contains("shouldDisplay("),
			"The stateful sender should delegate the ordering policy to a pure helper.");
	}

	@Test
	void noisyOverlayPathsUseThePriorityGate() throws IOException {
		String focusAbility = read(FOCUS_ABILITY);
		String pactTacticals = read(PACT_TACTICALS);
		String surges = read(SURGES);
		String combatFeedback = read(COMBAT_FEEDBACK);
		String apex = read(APEX);
		String updraft = read(UPDRAFT);
		String harpoon = read(HARPOON);
		String unseen = read(UNSEEN);
		String synergies = read(SYNERGIES);
		String pactTrials = read(PACT_TRIALS);
		String presetNetworking = read(PRESET_NETWORKING);
		String onboarding = read(ONBOARDING);
		String attunedNetworking = read(ATTUNED_NETWORKING);

		assertTrue(focusAbility.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING"),
			"Ability cooldown/none feedback should be warning-priority so it can beat routine progress text.");
		assertTrue(pactTacticals.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY"),
			"Pact tactical success/overcharge messages should use ability-priority action-bar feedback.");
		assertTrue(surges.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT"),
			"Surge ambience/reward action-bar text should not overwrite higher-priority combat messages.");
		assertTrue(combatFeedback.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS"),
			"Resonance streak/progress text should route through the shared priority gate.");
		assertTrue(apex.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING"),
			"Apex identity cooldown feedback should use the shared gate so routine text cannot bury it.");
		assertTrue(apex.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY"),
			"Apex identity success feedback should use ability-priority action-bar feedback.");
		assertTrue(containsTranslatable(apex, "ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,\n"
				+ "\t\t\tComponent.translatable(\"apex.attuned.rearmed\""),
			"Apex rearmed feedback should use ability-priority action-bar gating.");
		assertTrue(containsTranslatable(apex, "ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,\n"
				+ "\t\t\tComponent.translatable(\"apex.attuned.dormant\"))"),
			"Apex dormant feedback should use warning-priority action-bar gating.");
		assertTrue(updraft.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING"),
			"Updraft PvP exhaustion feedback should use warning-priority action-bar gating.");
		assertTrue(containsTranslatable(harpoon, "ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,\n"
				+ "\t\t\t\tComponent.translatable(\"item.attuned.harpoon_focus.active\"))"),
			"Harpoon already-active feedback should use warning-priority action-bar gating.");
		assertTrue(containsTranslatable(harpoon, "ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,\n"
				+ "\t\t\t\tComponent.translatable(\"item.attuned.harpoon_focus.no_space\"))"),
			"Harpoon no-space feedback should use warning-priority action-bar gating.");
		assertTrue(containsTranslatable(unseen, "ActionBarMessages.send(attacker, ActionBarMessages.Priority.ABILITY,\n"
				+ "\t\t\tComponent.translatable(\"combo.attuned.softstep_needle\"))"),
			"Softstep + Needle combo confirmation should use ability-priority action-bar gating.");
		assertTrue(synergies.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS, discovery)"),
			"Confluence discovery fanfare should use progress-priority action-bar gating.");
		assertTrue(pactTrials.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,"),
			"Pact trial milestone feedback should use progress-priority action-bar gating.");
		assertTrue(containsTranslatable(presetNetworking, "ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,\n"
				+ "\t\t\tComponent.translatable(\"screen.attuned.preset.imported\", preset.name()))"),
			"Preset import success feedback should use progress-priority action-bar gating.");
		assertTrue(containsTranslatable(presetNetworking, "ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,\n"
				+ "\t\t\tComponent.translatable(\"screen.attuned.preset.applied\", presets.get(index).name()))"),
			"Preset apply success feedback should use progress-priority action-bar gating.");
		assertTrue(onboarding.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT, preview)"),
			"One-away onboarding preview hints should use ambient-priority action-bar gating.");
		assertTrue(attunedNetworking.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,\n"
				+ "\t\t\tinspectLine(target))"),
			"Inspect replies should use the shared priority gate so routine inspect text cannot bury warnings.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static boolean containsTranslatable(String source, String modernNeedle) {
		return source.contains(modernNeedle)
			|| source.contains(modernNeedle.replace("Component.translatable",
				"new net.minecraft.network.chat.TranslatableComponent"));
	}
}
