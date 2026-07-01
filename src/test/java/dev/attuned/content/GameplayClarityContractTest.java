package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Player-facing clarity contracts for the expanded 1.5.x gameplay loops. */
class GameplayClarityContractTest {
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path ABILITY_STATE = Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path PACT_TRIALS = Path.of("src/main/java/dev/attuned/pacts/PactTrials.java");
	private static final Path CIRCLE_CONTRIBUTIONS =
		Path.of("src/main/java/dev/attuned/party/CircleContributions.java");
	private static final Path READOUT = Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
	private static final Path REFERENCE = Path.of("docs/reference.md");
	private static final Path QA_CHECKLIST = Path.of("docs/platform/attuned-gameplay-polish-qa.md");
	private static final Path DOCS_PLATFORM = Path.of("docs/platform");
		private static final Path MOD_METADATA = Path.of(
		Files.exists(Path.of("src/main/resources/fabric.mod.json"))
			? "src/main/resources/fabric.mod.json"
			: "src/main/resources/quilt.mod.json");

	@Test
	void pactTrialDescriptionsMatchCurrentCodeGoals() throws IOException {
		String trials = read(PACT_TRIALS);
		String lang = read(LANG);

		Map<String, Integer> goals = pactGoals(trials);
		assertEquals(9, goals.size(), "Every Pact should have a trial goal.");

		assertTrial(lang, goals, "pyresworn", 40, "Ignite forty hostiles while Pyresworn is awake.");
		assertTrial(lang, goals, "stoneheart", 400, "Absorb four hundred foe damage while Stoneheart is awake.");
		assertTrial(lang, goals, "windrunner", 6400, "Sprint six thousand four hundred blocks while Windrunner is awake.");
		assertTrial(lang, goals, "radiant_covenant", 25, "Land twenty-five charged reveals while Radiant Covenant is awake.");
		assertTrial(lang, goals, "tidesworn", 40, "Slow forty foes while Tidesworn is awake.");
		assertTrial(lang, goals, "forgebound", 25, "Sear twenty-five foes while Forgebound is awake.");
		assertTrial(lang, goals, "wildroot", 36000, "Hold Wildroot awake for thirty minutes.");
		assertTrial(lang, goals, "nightsworn", 150, "Absorb one hundred fifty foe damage in the dark while Nightsworn is awake.");
		assertTrial(lang, goals, "untethered", 20, "Slay twenty affinity-bearing hostiles at Apex resonance while Untethered is awake.");
	}

	@Test
	void journalExplainsEightAffinityRealityAndCurrentGameplayLoops() throws IOException {
		String lang = read(LANG);
		String lower = lang.toLowerCase(java.util.Locale.ROOT);
		assertFalse(lower.contains("fourth affinity"), "Unseen copy must not describe an old four-affinity model.");
		assertFalse(lower.contains("one active focus from every affinity"),
			"Six slots and eight affinities make one-from-every-affinity wording impossible.");
		assertFalse(lower.contains("run every affinity at once"),
			"Six slots and eight affinities make every-affinity Maelstrom wording impossible.");
		assertFalse(lower.contains("full every-affinity"),
			"Wide Discord copy should not imply every affinity can be active at once.");

		String unseen = translationValue(lang, "journal.attuned.page9").replace("\\n", "\n");
		assertTrue(unseen.contains("not an affinity lane"), "Faction copy should distinguish Unseen from Affinity lanes.");

		String untethered = translationValue(lang, "journal.attuned.page18").replace("\\n", "\n");
		assertTrue(untethered.contains("Four or more different affinity lanes"));
		assertTrue(untethered.contains("no lane reaches three active Foci"));

		String maelstrom = translationValue(lang, "journal.attuned.page30").replace("\\n", "\n");
		assertTrue(maelstrom.contains("four or more different affinity lanes"));
		assertTrue(maelstrom.contains("near full capacity"));

		String hud = translationValue(lang, "journal.attuned.page28").replace("\\n", "\n");
		assertTrue(hud.contains("ability well"));
		assertTrue(hud.contains("resonance bar"));
		assertTrue(hud.contains("Confluence pips"));

		String pactTacticals = translationValue(lang, "journal.attuned.page42").replace("\\n", "\n");
		assertTrue(pactTacticals.contains("Focus Ability key"));
		assertTrue(pactTacticals.contains("overcharge"));
		assertTrue(pactTacticals.contains("resonance"));
		assertTrue(pactTacticals.contains("Priority: ability Focus, Pact tactical, Apex identity."),
			"The Pact Tacticals journal page should document the full ability-key fallback order.");

		String trials = translationValue(lang, "journal.attuned.page43").replace("\\n", "\n");
		assertTrue(trials.contains("Trial progress accrues only while that Pact is awake"));
		assertTrue(trials.contains("Tier 4"));

		String surges = translationValue(lang, "journal.attuned.page44").replace("\\n", "\n");
		assertTrue(surges.contains("thunderstorm"));
		assertTrue(surges.contains("shard fragments"));
		assertTrue(surges.contains("Resonance"));

		String updraft = translationValue(lang, "journal.attuned.page45").replace("\\n", "\n");
		assertTrue(updraft.contains("Updraft"));
		assertTrue(updraft.contains("hold jump"));
		assertTrue(updraft.contains("Hold sprint"));
		assertTrue(updraft.contains("PvP exhaustion"));
	}

	@Test
	void abilityKeyPriorityIsDocumentedAndMatchesServerOrder() throws IOException {
		String state = read(ABILITY_STATE);
		String lang = read(LANG);
		String trigger = methodBody(state, "public static void trigger(ServerPlayer player)");
		String journal = translationValue(lang, "journal.attuned.page42").replace("\\n", "\n");

		assertTrue(trigger.indexOf("AbilitySelection selection = firstActiveAbility(player)")
				< trigger.indexOf("PactTacticals.tryTrigger(player)")
				&& trigger.indexOf("PactTacticals.tryTrigger(player)")
				< trigger.indexOf("Apex.tryIdentityAbility(player)")
				&& trigger.indexOf("Apex.tryIdentityAbility(player)")
				< trigger.indexOf("item.attuned.focus_ability.none"),
			"The ability key should try an active ability Focus, then Pact tactical, then Apex identity, then no-ability feedback.");
		assertTrue(journal.contains("Priority: ability Focus, Pact tactical, Apex identity."),
			"The journal should explain the same ability-key priority that the server uses.");
	}

	@Test
	void factionSetBonusCopyRequiresThreeActiveFoci() throws IOException {
		String lang = read(LANG);
		String reference = read(REFERENCE);
		Pattern pattern = Pattern.compile(
			"\\\"faction\\.attuned\\.set_bonus\\.([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
		Matcher matcher = pattern.matcher(lang);
		int count = 0;
		while (matcher.find()) {
			count++;
			String id = matcher.group(1);
			String copy = matcher.group(2);
			assertTrue(copy.contains("3 active Foci"),
				"Faction set bonus copy should say it requires three active Foci: " + id);
			assertFalse(copy.contains("(3+)"),
				"Faction set bonus copy should not imply any owned/inventory Foci count is enough: " + id);
		}
		assertEquals(13, count, "All shipped faction set bonuses should be checked for active-Foci wording.");
		assertTrue(reference.contains("Set bonus (3 active Foci)"),
			"The public faction table should name active Foci in its set-bonus header.");
		assertFalse(reference.contains("Set bonus (3+)"),
			"The public faction table should not use shorthand that can be read as inventory ownership.");
	}

	@Test
	void referenceAndManualQaCoverGameplayFeelLoops() throws IOException {
		String reference = read(REFERENCE);
		assertTrue(reference.contains("## Pact Tacticals"), "Reference docs should explain pact actives.");
		assertTrue(reference.contains("## Pact Trials and Tier 4"), "Reference docs should explain long-term pact progression.");
		assertTrue(reference.contains("## Resonant Surges"), "Reference docs should explain storm combat events.");
		assertTrue(reference.contains("## Updraft Focus"), "Reference docs should explain the flight Focus.");

		String qa = read(QA_CHECKLIST);
		assertTrue(qa.contains("Moment-to-moment combat feel"));
		assertTrue(qa.contains("Pact loop"));
		assertTrue(qa.contains("Confluence and combo discovery"));
		assertTrue(qa.contains("Updraft flight feel"));
		assertTrue(qa.contains("Onboarding and journal clarity"));
		assertTrue(qa.contains("Circles chapter"));
		assertTrue(qa.contains("no item sharing"));
		assertTrue(qa.contains("Party-readiness smoke"));
		assertTrue(qa.contains("Create a Circle, invite a second player, accept the invite"));
		assertTrue(qa.contains("Disconnect one Circle member and confirm remaining members receive an updated party HUD"));
		assertTrue(qa.contains("same-dimension"));
		assertTrue(qa.contains("No AFK"));
		assertTrue(qa.contains("friendly-fire-off"));
	}

	@Test
	void referenceDescribesSharedCreditAsCurrentBehavior() throws IOException {
		String reference = read(REFERENCE);
		String contributions = read(CIRCLE_CONTRIBUTIONS);

		assertTrue(reference.contains("Circle shared trial credit."),
			"Reference docs should explain the shipped Circle trial-credit fan-out.");
		assertTrue(reference.contains("eligible Circle contributors can receive Pact Trial assist credit"),
			"The config table should describe the current Pact Trial assist behavior.");
		assertFalse(reference.contains("future Circle shared-credit"),
			"Reference docs should not describe shipped Circle shared-credit behavior as future work.");
		assertFalse(reference.contains("future shared credit"),
			"Reference docs should not leave old planned-feature language around shared credit.");
		assertFalse(contributions.contains("future Circle shared-credit hooks"),
			"Runtime Javadocs should describe Circle contribution tracking as a current subsystem.");
	}

	@Test
	void releaseFacingCopyDoesNotExposeInternalArtWorkflow() throws IOException {
		Map<String, String> forbidden = new LinkedHashMap<>();
		forbidden.put(".attuned-art-sources", "untracked asset workspace paths");
		forbidden.put("untracked art", "untracked asset workflow notes");
		forbidden.put("asset manifest", "untracked asset workflow notes");

		for (Path path : releaseFacingCopyPaths()) {
			String lower = read(path).toLowerCase(java.util.Locale.ROOT);
			for (Map.Entry<String, String> entry : forbidden.entrySet()) {
				assertFalse(lower.contains(entry.getKey()),
					path + " should not expose " + entry.getValue() + ".");
			}
		}
	}

	@Test
	void discordSurgeHalfRateIsExplainedInJournalReferenceAndReadout() throws IOException {
		String lang = read(LANG);
		String discord = translationValue(lang, "journal.attuned.page5").replace("\\n", "\n");
		String surges = translationValue(lang, "journal.attuned.page44").replace("\\n", "\n");
		String reference = read(REFERENCE);
		String readout = read(READOUT);

		assertTrue(discord.contains("half Resonance from Resonant Surges"),
			"The Discord journal page should call out the exact surge-resonance penalty.");
		assertTrue(surges.contains("Discord builds earn half Resonance"),
			"The Resonant Surges page should explain the Discord half-rate rule at the event surface.");
		assertTrue(reference.contains("Discord earns half Resonance from the surge field"),
			"The reference docs should name the exact Discord surge multiplier, not just say reduced.");
		assertTrue(readout.contains("Discord: half Resonance from surge fields."),
			"The Focus-panel hover readout should expose the same half-rate rule in-game.");
	}

	private static void assertTrial(String lang, Map<String, Integer> goals, String id, int expectedGoal,
			String expectedDescription) {
		assertEquals(expectedGoal, goals.get(id), "Code goal changed for " + id + "; update wording intentionally.");
		assertEquals(expectedDescription,
			translationValue(lang, "pact.attuned." + id + "_trial.description"));
	}

	private static Map<String, Integer> pactGoals(String source) {
		Pattern pattern = Pattern.compile("GOALS\\.put\\(Pact\\.([A-Z_]+), ([0-9_]+)\\)");
		Matcher matcher = pattern.matcher(source);
		Map<String, Integer> goals = new LinkedHashMap<>();
		while (matcher.find()) {
			goals.put(matcher.group(1).toLowerCase(java.util.Locale.ROOT),
				Integer.parseInt(matcher.group(2).replace("_", "")));
		}
		return goals;
	}

	private static String translationValue(String lang, String key) {
		Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
		Matcher matcher = pattern.matcher(lang);
		assertTrue(matcher.find(), "Missing lang key: " + key);
		return matcher.group(1);
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static List<Path> releaseFacingCopyPaths() throws IOException {
		List<Path> paths = new ArrayList<>();
		paths.add(Path.of("docs/README.md"));
		paths.add(Path.of("docs/adding-a-focus.md"));
		paths.add(REFERENCE);
		paths.add(MOD_METADATA);
		try (Stream<Path> stream = Files.walk(DOCS_PLATFORM)) {
			stream.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".md"))
				.sorted(Comparator.comparing(Path::toString))
				.forEach(paths::add);
		}
		return paths;
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
