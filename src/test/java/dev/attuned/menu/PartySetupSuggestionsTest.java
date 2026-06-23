package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.menu.PartySetupSuggestions.MemberRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartySetupSuggestionsTest {
	private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000901");
	private static final UUID ALLY = UUID.fromString("00000000-0000-0000-0000-000000000902");
	private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000903");

	@Test
	void scoutHeavyPartySuggestsRevealAndFrontLineCoverage() {
		assertEquals(List.of(
			"No one is carrying reveal.",
			"Party has no Anchor/Vanguard front line."),
			PartySetupSuggestions.warnings(SELF, "Current", List.of(
				new MemberRole(SELF, "Current"),
				new MemberRole(ALLY, "Warden"))));
	}

	@Test
	void repeatedShadeBuildsSuggestWitnessSupport() {
		assertEquals(List.of(
			"No one is carrying reveal.",
			"Party has no Anchor/Vanguard front line.",
			"Two members are both low-light Shade builds; bright caves may be safer with Witness support."),
			PartySetupSuggestions.warnings(SELF, "Shade", List.of(
				new MemberRole(SELF, "Shade"),
				new MemberRole(ALLY, "Shade"),
				new MemberRole(THIRD, "Current"))));
	}

	@Test
	void candidateRoleReplacesCurrentSelfRoleWhenPreviewingPartyFit() {
		assertEquals(List.of("Party has no Anchor/Vanguard front line."),
			PartySetupSuggestions.warnings(SELF, "Witness", List.of(
				new MemberRole(SELF, "Shade"),
				new MemberRole(ALLY, "Shade"))),
			"Previewing a Witness setup should cover reveal and avoid the duplicate-Shade warning.");
	}

	@Test
	void soloOrWellCoveredPartiesStayQuiet() {
		assertEquals(List.of(), PartySetupSuggestions.warnings(SELF, "Current", List.of(
			new MemberRole(SELF, "Current"))));
		assertEquals(List.of(), PartySetupSuggestions.warnings(SELF, "Witness", List.of(
			new MemberRole(SELF, "Current"),
			new MemberRole(ALLY, "Anchor"),
			new MemberRole(THIRD, "Witness"))));
	}
}
