package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guardrails for making combat-facing Foci participate in PvP. */
class FocusPvpAvailabilityContractTest {
	private static final Path COMBAT_TARGETS_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/CombatTargets.java");
	private static final Path ATTUNED_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path APEX_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path PACTS_SOURCE =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path STORMCALL_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/StormcallBehavior.java");
	private static final Path LANTERN_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/LanternBehavior.java");
	private static final Path RADIANT_BEHAVIORS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");
	private static final Path REVENANT_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");
	private static final Path DATA_BEHAVIORS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");
	private static final Path UPDRAFT_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void pvpTargetHelperRespectsVanillaPlayerCombatRules() throws IOException {
		String source = readSource(COMBAT_TARGETS_SOURCE);

		assertTrue(source.contains("GameRules.PVP") || source.contains("isPvpAllowed()"),
			"PvP Focus effects should respect the server PvP switch.");
		assertTrue(source.contains("attacker.canHarmPlayer(target)"),
			"PvP Focus effects should respect vanilla team/friendly-fire checks.");
		assertTrue(source.contains("target != attacker"),
			"PvP Focus effects should never target the player wearing the Focus.");
		assertTrue(source.contains("!target.isSpectator()"),
			"PvP Focus effects should ignore spectators.");
	}

	@Test
	void pvpTargetHelperRejectsCircleMembersBeforeHostileOnlyProcs() throws IOException {
		String source = readSource(COMBAT_TARGETS_SOURCE);

		assertTrue(source.contains("import dev.attuned.party.CircleRuntime;"),
			"Shared PvP target rules should know about server-owned Circle membership.");
		assertTrue(source.contains("&& !sameCircle(attacker, target)"),
			"Circle members should not be valid PvP opponents for hostile-only Attuned procs.");
		assertTrue(source.contains("private static boolean sameCircle(Player attacker, Player target)"),
			"The Circle exclusion should live in one shared helper.");
		assertTrue(source.contains("CircleRuntime.manager().circleOf(attacker.getUUID())"),
			"Circle exclusion should derive membership from the server-owned Circle manager.");
		assertTrue(source.contains("circle.members().contains(target.getUUID())"),
			"Circle exclusion should reject any target already in the attacker's Circle.");
		assertTrue(source.indexOf("&& !sameCircle(attacker, target)")
				< source.indexOf("&& attacker.canHarmPlayer(target)"),
			"Attuned Circle membership should narrow vanilla PvP before Focus procs can affect a player.");
	}

	@Test
	void partyAssistsInheritSustainedPvpExhaustionPressure() throws IOException {
		String partyAssist = methodBody(readSource(DATA_BEHAVIORS_SOURCE),
			"private boolean matchesTarget(ServerPlayer player, ServerPlayer target)");
		String updraft = readSource(UPDRAFT_BEHAVIOR_SOURCE);

		assertTrue(updraft.contains("static boolean isPvpAssistDampened(ServerPlayer player)"),
			"Updraft's sustained PvP pressure state should be exposed for party-assist dampening.");
		assertTrue(updraft.contains("return isPvpExhausted(player);"),
			"Party-assist dampening should reuse the same five-second PvP exhaustion window as flight controls.");
		assertTrue(partyAssist.contains("UpdraftBehavior.isPvpAssistDampened(player) || UpdraftBehavior.isPvpAssistDampened(target)"),
			"Rescue, mobility, and shield party assists should not bypass sustained PvP exhaustion.");
		assertBefore(partyAssist,
			"CombatTargets.canAffectPlayer(player, target) || CombatTargets.canAffectPlayer(target, player)",
			"UpdraftBehavior.isPvpAssistDampened(player) || UpdraftBehavior.isPvpAssistDampened(target)");
		assertBefore(partyAssist,
			"UpdraftBehavior.isPvpAssistDampened(player) || UpdraftBehavior.isPvpAssistDampened(target)",
			"return switch (def.targetFilter())");
	}

	@Test
	void activeCombatFociCanAffectPlayerOpponents() throws IOException {
		String attunedCombat = readSource(ATTUNED_COMBAT_SOURCE);
		String stormcall = readSource(STORMCALL_SOURCE);
		String lantern = readSource(LANTERN_SOURCE);
		String radiant = readSource(RADIANT_BEHAVIORS_SOURCE);
		String revenant = readSource(REVENANT_COMBAT_SOURCE);

		assertTrue(attunedCombat.contains("affinityOf(defender).filter(affinity -> affinity == Affinity.FURY)"),
			"Sunlance should read Fury-attuned players as valid Fury-aligned foes.");
		assertTrue(stormcall.contains("List<LivingEntity> targets")
				&& stormcall.contains("CombatTargets.isHostileOrPvpOpponent(target, player)"),
			"Stormcall should be able to choose hostile mobs or valid PvP opponents.");
		assertTrue(lantern.contains("CombatTargets.isHostileOrPvpOpponent(target, player)"),
			"Lantern should reveal visible PvP opponents as threats.");
		assertTrue(radiant.contains("CombatTargets.isHostileOrPvpOpponent(target, player)"),
			"Bellwether should reveal visible PvP opponents as threats.");
		assertTrue(revenant.contains("CombatTargets.isHostileOrPvpOpponent(attacker, player)"),
			"Bonechill should chill valid PvP opponents that strike the wearer.");
		assertTrue(revenant.contains("CombatTargets.isHostileOrPvpOpponent(entity, player)"),
			"Last Rites should allow valid PvP opponent kills to cleanse the wearer.");
	}

	@Test
	void revealFociMarkCircleContributionOnlyAfterFindingVisibleThreats() throws IOException {
		String lantern = readSource(LANTERN_SOURCE);
		String radiant = readSource(RADIANT_BEHAVIORS_SOURCE);
		String lanternTick = methodBody(lantern,
			"public void onTick(ServerPlayer player, ItemStack focus)");
		String bellwetherReveal = methodBody(radiant,
			"private void revealVisibleThreats(ServerPlayer player, Cooldown cooldown)");
		String witnessAbility = methodBody(radiant,
			"public boolean onAbility(ServerPlayer player, ItemStack focus)");

		assertTrue(lantern.contains("import dev.attuned.party.CircleContributions;"),
			"Lantern reveals should feed the server-owned Circle contribution runtime.");
		assertTrue(radiant.contains("import dev.attuned.party.CircleContributions;"),
			"Radiant reveal Foci should feed the server-owned Circle contribution runtime.");

		assertRevealContributionUsesFilteredTargets(lanternTick, "Lantern");
		assertRevealContributionUsesFilteredTargets(bellwetherReveal, "Bellwether");
		assertRevealContributionUsesFilteredTargets(witnessAbility, "Oathguard witness");
	}

	@Test
	void ashenDebtOnlyRecordsAndSpendsAgainstFoes() throws IOException {
		String revenant = readSource(REVENANT_COMBAT_SOURCE);
		String adjustDamage = methodBody(revenant,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String afterDamage = methodBody(revenant,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(adjustDamage.contains("!CombatTargets.isHostileOrPvpOpponent(defender, attacker)"),
			"Ashen Debt should not spend a debt bonus against pets, passive mobs, spectators, or friendly-fire-blocked players.");
		assertTrue(afterDamage.contains("!CombatTargets.isHostileOrPvpOpponent(attacker, player)"),
			"Ashen Debt should only record debts when the wearer is hurt by a hostile mob or valid PvP opponent.");
	}

	@Test
	void pyreswornIgniteUsesVanillaPlayerTargetRules() throws IOException {
		String pacts = readSource(PACTS_SOURCE);
		String pyreswornIgnite = methodBody(pacts,
			"private static void pyreswornIgnite(Player attacker, LivingEntity defender, DamageSource source)");
		JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8))
			.getAsJsonObject();

		assertEquals("Half-charged or stronger melee hits set hostile foes alight.",
			lang.get("pact.attuned.pyresworn.description").getAsString());
		assertTrue(pyreswornIgnite.contains("defender instanceof Player targetPlayer"),
			"Pyresworn should name player targets before applying the shared PvP predicate.");
		assertTrue(pyreswornIgnite.contains("!CombatTargets.canAffectPlayer(attacker, targetPlayer)"),
			"Pyresworn should respect spectators, the PvP gamerule, and vanilla team/friendly-fire checks.");
		assertTrue(pyreswornIgnite.contains("!CombatTargets.isHostileOrPvpOpponent(defender, attacker)"),
			"Pyresworn should ignite only hostile mobs or valid PvP opponents, not passive animals.");
		assertTrue(!pyreswornIgnite.contains("GameRules.PVP"),
			"Pyresworn should not use a narrower raw PvP gamerule check for player targets.");
	}

	@Test
	void pactsAndApexUsePlayerAffinityTargetsForPvp() throws IOException {
		String pacts = readSource(PACTS_SOURCE);
		String apex = readSource(APEX_SOURCE);

		assertTrue(pacts.contains("CombatTargets.affinityOf(defender).isPresent()"),
			"Untethered should amplify against affinity-bearing players as well as mobs.");
		assertTrue(pacts.contains("CombatTargets.isHostileOrPvpOpponent(defender, attacker)"),
			"Radiant Covenant should reveal player opponents when PvP allows it.");
		assertTrue(apex.contains("CombatTargets.canAffectPlayer(attackerPlayer, targetPlayer)"),
			"Offensive Apex capstones should be guarded by the same PvP player-target rules.");
		assertTrue(apex.contains("context.hasAffinityPressure(defender)"),
			"Maelstrom should treat player attunement as affinity pressure.");
	}

	@Test
	void radiantCovenantChargedRevealRequiresLineOfSight() throws IOException {
		String pacts = readSource(PACTS_SOURCE);
		String reveal = methodBody(pacts,
			"private static void radiantCovenantReveal(Player attacker, LivingEntity defender, DamageSource source)");

		assertTrue(reveal.contains("attacker.hasLineOfSight(defender)"),
			"Radiant Covenant charged reveal should not apply Glowing to a defender the attacker cannot see.");
		assertTrue(reveal.indexOf("attacker.hasLineOfSight(defender)")
				< reveal.indexOf("defender.addEffect(new MobEffectInstance("),
			"Radiant Covenant should verify visibility before applying the reveal effect.");
	}

	@Test
	void playerFacingTextDescribesPvpCapableFociAsFoesOrThreats() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8))
			.getAsJsonObject();

		assertEquals("Ringing or standing within 8 blocks of a bell reveals visible threats for a short time.",
			lang.get("item.attuned.bellwether_focus.effect").getAsString());
		assertEquals("Fully charged melee hits deal 10% more damage to undead and Fury-aligned foes.",
			lang.get("item.attuned.sunlance_focus.effect").getAsString());
		assertEquals("After a foe hurts you, your next direct melee hit against them deals 25% more damage.",
			lang.get("item.attuned.ashen_debt_focus.effect").getAsString());
		assertEquals("Undead you strike, and hostile foes that strike you, are briefly slowed.",
			lang.get("item.attuned.bonechill_focus.effect").getAsString());
		assertEquals("Charged melee hits reveal visible threats; undead suffer a small extra wound.",
			lang.get("pact.attuned.radiant_covenant.description").getAsString());
	}

	private static String readSource(Path source) throws IOException {
		assertTrue(Files.isRegularFile(source), "Expected source file to exist: " + source);
		return Files.readString(source, StandardCharsets.UTF_8);
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

	private static void assertRevealContributionUsesFilteredTargets(String body, String label) {
		assertTrue(body.contains("List<LivingEntity> targets"),
			label + " should materialize the visible hostile/PvP target list before granting contribution credit.");
		assertTrue(body.contains("if (!targets.isEmpty())"),
			label + " should not open a contribution window for an empty reveal pulse.");
		assertTrue(body.contains("CircleContributions.recordContribution(player)"),
			label + " should open a Circle contribution window after revealing valid threats.");
		assertBefore(body, "CombatTargets.isHostileOrPvpOpponent(target, player)",
			"CircleContributions.recordContribution(player)");
		assertBefore(body, "!MaskBehavior.resistsReveal(target)",
			"CircleContributions.recordContribution(player)");
		assertBefore(body, "player.hasLineOfSight(target)",
			"CircleContributions.recordContribution(player)");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing expected source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing expected source fragment: " + later);
		assertTrue(earlierIndex < laterIndex,
			"Expected `" + earlier + "` to appear before `" + later + "`.");
	}
}
