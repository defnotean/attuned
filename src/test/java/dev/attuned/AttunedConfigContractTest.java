package dev.attuned;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AttunedConfigContractTest {
	private static final Path CONFIG_SOURCE = Path.of("src/main/java/dev/attuned/AttunedConfig.java");
	private static final Path REFERENCE_DOC = Path.of("docs/reference.md");

	@Test
	void startingCapacityIsCappedAtCapacityCap() {
		AttunedConfig direct = config(30, 12);
		assertEquals(12, direct.startingCapacity(),
			"Direct config construction should preserve the capacity cap invariant.");

		JsonObject json = new JsonObject();
		json.addProperty("starting_capacity", 30);
		json.addProperty("capacity_cap", 12);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(12, parsed.capacityCap());
		assertEquals(12, parsed.startingCapacity(),
			"Parsed config should not let players start above the configured cap.");
	}

	@Test
	void directConfigRejectsNonPositiveCapacityCap() {
		assertThrows(IllegalArgumentException.class, () -> config(0, 0),
			"Direct config construction should reject a capacity cap that would let capacity become negative.");
	}

	@Test
	void directConfigRejectsCodecOutOfRangeNumbers() {
		assertInvalid(() -> fullConfig(-1, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(257, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 257, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 0, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 65, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, -0.01F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 1.01F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, -0.01F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, Float.NaN, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 129.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1728001, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, -1));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1728001));
	}

	@Test
	void combatDefaultsMatchTheDocumentedSchema() {
		assertEquals(1.33F, AttunedConfig.DEFAULT.advantageMultiplier());
		assertEquals(0.75F, AttunedConfig.DEFAULT.disadvantageMultiplier());
		assertEquals(1.20F, AttunedConfig.DEFAULT.discordDamageMultiplier());
		assertEquals(0.012F, AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage());
		assertEquals(0.10F, AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss());
		assertEquals(0.30F, AttunedConfig.DEFAULT.resonanceKillEmpoweredGain());
		assertEquals(0.00025F, AttunedConfig.DEFAULT.resonanceDecayPerTick());
	}

	@Test
	void combatKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("advantage_multiplier", 1.5F);
		json.addProperty("disadvantage_multiplier", 0.6F);
		json.addProperty("discord_damage_multiplier", 1.25F);
		json.addProperty("resonance_hit_empowered_gain_per_damage", 0.02F);
		json.addProperty("resonance_hit_neutralized_loss", 0.15F);
		json.addProperty("resonance_kill_empowered_gain", 0.4F);
		json.addProperty("resonance_decay_per_tick", 0.0005F);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1.5F, parsed.advantageMultiplier());
		assertEquals(0.6F, parsed.disadvantageMultiplier());
		assertEquals(1.25F, parsed.discordDamageMultiplier());
		assertEquals(0.02F, parsed.resonanceHitEmpoweredGainPerDamage());
		assertEquals(0.15F, parsed.resonanceHitNeutralizedLoss());
		assertEquals(0.4F, parsed.resonanceKillEmpoweredGain());
		assertEquals(0.0005F, parsed.resonanceDecayPerTick());
	}

	@Test
	void resonantSurgeDefaultsMatchTheDocumentedSchema() {
		assertEquals(12000, AttunedConfig.DEFAULT.surgeIntervalTicks(),
			"Surge interval defaults to 10 minutes (12000 ticks).");
		assertEquals(1200, AttunedConfig.DEFAULT.surgeDurationTicks(),
			"Surge duration defaults to 60 seconds (1200 ticks).");
		assertEquals(16, AttunedConfig.DEFAULT.surgeRadius(),
			"Surge radius defaults to 16 blocks.");
	}

	@Test
	void resonantSurgeKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("surge_interval_ticks", 4000);
		json.addProperty("surge_duration_ticks", 800);
		json.addProperty("surge_radius", 24);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(4000, parsed.surgeIntervalTicks());
		assertEquals(800, parsed.surgeDurationTicks());
		assertEquals(24, parsed.surgeRadius());
	}

	@Test
	void directConfigRejectsOutOfRangeSurgeNumbers() {
		assertInvalid(() -> surgeConfig(199, 1200, 16));
		assertInvalid(() -> surgeConfig(1728001, 1200, 16));
		assertInvalid(() -> surgeConfig(12000, 199, 16));
		assertInvalid(() -> surgeConfig(12000, 72001, 16));
		assertInvalid(() -> surgeConfig(12000, 1200, 3));
		assertInvalid(() -> surgeConfig(12000, 1200, 65));
	}

	private static AttunedConfig surgeConfig(int surgeIntervalTicks, int surgeDurationTicks, int surgeRadius) {
		return new AttunedConfig(
			AttunedConfig.DEFAULT.startingCapacity(),
			AttunedConfig.DEFAULT.capacityCap(),
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			surgeIntervalTicks,
			surgeDurationTicks,
			surgeRadius,
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			AttunedConfig.DEFAULT.affinityLoomMaxShardCost(),
			AttunedConfig.DEFAULT.partyMaxMembers(),
			AttunedConfig.DEFAULT.partySharedCreditRadius(),
			AttunedConfig.DEFAULT.partySharedCreditWindowTicks(),
			AttunedConfig.DEFAULT.partyInviteTtlTicks(),
			AttunedConfig.DEFAULT.partyInviteCooldownTicks(),
			AttunedConfig.DEFAULT.partyCreateCooldownTicks(),
			AttunedConfig.DEFAULT.partyCrossDimensionCreditEnabled(),
			AttunedConfig.DEFAULT.partyHudEnabled(),
			AttunedConfig.DEFAULT.partySharedCreditEnabled(),
			AttunedConfig.DEFAULT.partyTrialAssistsEnabled(),
			AttunedConfig.DEFAULT.partyEffectsEnabled(),
			AttunedConfig.DEFAULT.partyConfluenceHintsEnabled(),
			AttunedConfig.DEFAULT.partySetupSuggestionsEnabled());
	}

	@Test
	void affinityLoomDefaultsMatchTheDocumentedSchema() {
		assertEquals(1, AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			"First Affinity Loom reroll defaults to one Attunement Shard.");
		assertEquals(3, AttunedConfig.DEFAULT.affinityLoomMaxShardCost(),
			"Affinity Loom shard cost defaults to a cap of three.");
	}

	@Test
	void affinityLoomKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("affinity_loom_base_shard_cost", 2);
		json.addProperty("affinity_loom_max_shard_cost", 5);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, parsed.affinityLoomBaseShardCost());
		assertEquals(5, parsed.affinityLoomMaxShardCost());
	}

	@Test
	void directConfigRejectsMaxAffinityLoomCostBelowBase() {
		assertInvalid(() -> loomConfig(3, 2));
	}

	@Test
	void partyDefaultsMatchTheDocumentedSchema() {
		assertEquals(4, AttunedConfig.DEFAULT.partyMaxMembers(),
			"Circle cap should default to the dossier's four-player party foundation.");
		assertEquals(48.0F, AttunedConfig.DEFAULT.partySharedCreditRadius(), 0.0001F,
			"Shared-credit radius should default to the dossier's 48-block party foundation.");
		assertEquals(200, AttunedConfig.DEFAULT.partySharedCreditWindowTicks(),
			"Shared-credit contribution windows should default to ten seconds.");
		assertEquals(1200, AttunedConfig.DEFAULT.partyInviteTtlTicks(),
			"Circle invites should default to a 60-second expiration.");
		assertEquals(200, AttunedConfig.DEFAULT.partyInviteCooldownTicks(),
			"Circle invite spam protection should default to ten seconds.");
		assertEquals(600, AttunedConfig.DEFAULT.partyCreateCooldownTicks(),
			"Circle disband/recreate protection should default to thirty seconds.");
		assertTrue(!AttunedConfig.DEFAULT.partyCrossDimensionCreditEnabled(),
			"Cross-dimension shared credit should be disabled by default.");
		assertTrue(AttunedConfig.DEFAULT.partyHudEnabled(),
			"Server-owned Circle HUD snapshots should be enabled by default.");
		assertTrue(AttunedConfig.DEFAULT.partySharedCreditEnabled(),
			"Shared-credit systems should be enabled by default but separately configurable.");
		assertTrue(AttunedConfig.DEFAULT.partyTrialAssistsEnabled(),
			"Pact Trial assists should default on but stay separately configurable.");
		assertTrue(AttunedConfig.DEFAULT.partyEffectsEnabled(),
			"Party effects should be enabled by default but separately configurable.");
		assertTrue(AttunedConfig.DEFAULT.partyConfluenceHintsEnabled(),
			"Confluence discovery hints should default on but stay separately configurable.");
		assertTrue(AttunedConfig.DEFAULT.partySetupSuggestionsEnabled(),
			"Party/setup suggestions should default on so existing saved-build guidance remains visible.");
	}

	@Test
	void partyKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("party_max_members", 8);
		json.addProperty("party_shared_credit_radius", 64.0F);
		json.addProperty("party_shared_credit_window_ticks", 400);
		json.addProperty("party_invite_ttl_ticks", 2400);
		json.addProperty("party_invite_cooldown_ticks", 300);
		json.addProperty("party_create_cooldown_ticks", 900);
		json.addProperty("party_cross_dimension_credit_enabled", true);
		json.addProperty("party_hud_enabled", false);
		json.addProperty("party_shared_credit_enabled", false);
		json.addProperty("party_trial_assists_enabled", false);
		json.addProperty("party_effects_enabled", false);
		json.addProperty("party_confluence_hints_enabled", false);
		json.addProperty("party_setup_suggestions_enabled", false);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(8, parsed.partyMaxMembers());
		assertEquals(64.0F, parsed.partySharedCreditRadius(), 0.0001F);
		assertEquals(400, parsed.partySharedCreditWindowTicks());
		assertEquals(2400, parsed.partyInviteTtlTicks());
		assertEquals(300, parsed.partyInviteCooldownTicks());
		assertEquals(900, parsed.partyCreateCooldownTicks());
		assertTrue(parsed.partyCrossDimensionCreditEnabled());
		assertEquals(false, parsed.partyHudEnabled());
		assertEquals(false, parsed.partySharedCreditEnabled());
		assertEquals(false, parsed.partyTrialAssistsEnabled());
		assertEquals(false, parsed.partyEffectsEnabled());
		assertEquals(false, parsed.partyConfluenceHintsEnabled());
		assertEquals(false, parsed.partySetupSuggestionsEnabled());
	}

	@Test
	void partyMaxMembersRejectsValuesOutsideTheDossierRange() {
		JsonObject tooSmall = new JsonObject();
		tooSmall.addProperty("party_max_members", 0);
		JsonObject tooLarge = new JsonObject();
		tooLarge.addProperty("party_max_members", 9);

		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, tooSmall).result().isEmpty(),
			"Circle caps below one should be rejected rather than silently repaired.");
		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, tooLarge).result().isEmpty(),
			"Circle caps above eight should be rejected so the party foundation stays small.");
	}

	@Test
	void partySharedCreditTuningRejectsMalformedValues() {
		JsonObject zeroRadius = new JsonObject();
		zeroRadius.addProperty("party_shared_credit_radius", 0.0F);
		JsonObject hugeRadius = new JsonObject();
		hugeRadius.addProperty("party_shared_credit_radius", 129.0F);
		JsonObject zeroWindow = new JsonObject();
		zeroWindow.addProperty("party_shared_credit_window_ticks", 0);
		JsonObject hugeWindow = new JsonObject();
		hugeWindow.addProperty("party_shared_credit_window_ticks", 72001);

		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, zeroRadius).result().isEmpty(),
			"Shared-credit radius should stay positive.");
		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, hugeRadius).result().isEmpty(),
			"Shared-credit radius should stay bounded for nearby party play.");
		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, zeroWindow).result().isEmpty(),
			"Contribution windows should require at least one tick.");
		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, hugeWindow).result().isEmpty(),
			"Contribution windows should stay below one in-game hour.");
	}

	@Test
	void partyCooldownTuningRejectsMalformedValues() {
		for (String key : new String[] {
				"party_invite_ttl_ticks",
				"party_invite_cooldown_ticks",
				"party_create_cooldown_ticks"}) {
			JsonObject zero = new JsonObject();
			zero.addProperty(key, 0);
			JsonObject huge = new JsonObject();
			huge.addProperty(key, 72001);

			assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, zero).result().isEmpty(),
				key + " should be at least one tick.");
			assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, huge).result().isEmpty(),
				key + " should stay below one in-game hour.");
		}
	}

	@Test
	void referenceDocsDocumentPartyServerToggles() throws IOException {
		String reference = Files.readString(REFERENCE_DOC, StandardCharsets.UTF_8);
		assertTrue(reference.contains("| `party_max_members` | 4 |"),
			"Reference config table should document the server-side Circle member cap.");
		assertTrue(reference.contains("| `party_shared_credit_radius` | 48.0 |"),
			"Reference config table should document the shared-credit radius.");
		assertTrue(reference.contains("| `party_shared_credit_window_ticks` | 200 |"),
			"Reference config table should document the shared-credit contribution window.");
		assertTrue(reference.contains("| `party_invite_ttl_ticks` | 1200 |"),
			"Reference config table should document Circle invite expiry.");
		assertTrue(reference.contains("| `party_invite_cooldown_ticks` | 200 |"),
			"Reference config table should document Circle invite spam throttling.");
		assertTrue(reference.contains("| `party_create_cooldown_ticks` | 600 |"),
			"Reference config table should document Circle disband/recreate throttling.");
		assertTrue(reference.contains("| `party_cross_dimension_credit_enabled` | false |"),
			"Reference config table should document the cross-dimension shared-credit opt-in.");
		assertTrue(reference.contains("| `party_hud_enabled` | true |"),
			"Reference config table should document the server-side Circle HUD toggle.");
		assertTrue(reference.contains("| `party_shared_credit_enabled` | true |"),
			"Reference config table should document the shared-credit toggle separately from HUD.");
		assertTrue(reference.contains("| `party_trial_assists_enabled` | true |"),
			"Reference config table should document Pact Trial assists separately from shared-credit windows.");
		assertTrue(reference.contains("| `party_effects_enabled` | true |"),
			"Reference config table should document party-effect toggles separately from HUD.");
		assertTrue(reference.contains("| `party_confluence_hints_enabled` | true |"),
			"Reference config table should document Confluence discovery hints separately from combat shared credit.");
		assertTrue(reference.contains("| `party_setup_suggestions_enabled` | true |"),
			"Reference config table should document the optional setup/party suggestion layer.");
	}

	@Test
	void partyConfigIncludesSeparateConfluenceHintToggle() throws IOException {
		String source = Files.readString(CONFIG_SOURCE, StandardCharsets.UTF_8);
		String reference = Files.readString(REFERENCE_DOC, StandardCharsets.UTF_8);

		assertTrue(source.contains("boolean partyConfluenceHintsEnabled"),
			"Confluence discovery hints should have their own party toggle instead of riding combat shared credit.");
		assertTrue(source.contains("\"party_confluence_hints_enabled\""),
			"The server config codec should persist the Confluence discovery hint toggle.");
		assertTrue(source.contains("json.addProperty(\"party_confluence_hints_enabled\""),
			"The rewritten config file should include the Confluence discovery hint toggle.");
		assertTrue(reference.contains("| `party_confluence_hints_enabled` | true |"),
			"Reference docs should document the Confluence discovery hint toggle.");
	}

	@Test
	void partyConfigIncludesSeparateTrialAssistToggle() throws IOException {
		String source = Files.readString(CONFIG_SOURCE, StandardCharsets.UTF_8);
		String reference = Files.readString(REFERENCE_DOC, StandardCharsets.UTF_8);

		assertTrue(source.contains("boolean partyTrialAssistsEnabled"),
			"Pact Trial assists should have their own party toggle instead of riding all shared credit.");
		assertTrue(source.contains("\"party_trial_assists_enabled\""),
			"The server config codec should persist the Pact Trial assist toggle.");
		assertTrue(source.contains("json.addProperty(\"party_trial_assists_enabled\""),
			"The rewritten config file should include the Pact Trial assist toggle.");
		assertTrue(reference.contains("| `party_trial_assists_enabled` | true |"),
			"Reference docs should document the Pact Trial assist toggle.");
	}

	private static AttunedConfig loomConfig(int affinityLoomBaseShardCost, int affinityLoomMaxShardCost) {
		return new AttunedConfig(
			AttunedConfig.DEFAULT.startingCapacity(),
			AttunedConfig.DEFAULT.capacityCap(),
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			AttunedConfig.DEFAULT.surgeIntervalTicks(),
			AttunedConfig.DEFAULT.surgeDurationTicks(),
			AttunedConfig.DEFAULT.surgeRadius(),
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			affinityLoomBaseShardCost,
			affinityLoomMaxShardCost,
			AttunedConfig.DEFAULT.partyMaxMembers(),
			AttunedConfig.DEFAULT.partySharedCreditRadius(),
			AttunedConfig.DEFAULT.partySharedCreditWindowTicks(),
			AttunedConfig.DEFAULT.partyInviteTtlTicks(),
			AttunedConfig.DEFAULT.partyInviteCooldownTicks(),
			AttunedConfig.DEFAULT.partyCreateCooldownTicks(),
			AttunedConfig.DEFAULT.partyCrossDimensionCreditEnabled(),
			AttunedConfig.DEFAULT.partyHudEnabled(),
			AttunedConfig.DEFAULT.partySharedCreditEnabled(),
			AttunedConfig.DEFAULT.partyTrialAssistsEnabled(),
			AttunedConfig.DEFAULT.partyEffectsEnabled(),
			AttunedConfig.DEFAULT.partyConfluenceHintsEnabled(),
			AttunedConfig.DEFAULT.partySetupSuggestionsEnabled());
	}

	private static AttunedConfig config(int startingCapacity, int capacityCap) {
		return fullConfig(
			startingCapacity,
			capacityCap,
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks());
	}

	private static AttunedConfig fullConfig(
			int startingCapacity,
			int capacityCap,
			int capacityPerShard,
			float focusLootChance,
			float lowLootMultiplier,
			float commonLootMultiplier,
			float richLootMultiplier,
			float treasureLootMultiplier,
			float shardFragmentLootMultiplier,
			int voidstepCooldownTicks,
			int gravebindCooldownTicks) {
		return new AttunedConfig(
			startingCapacity,
			capacityCap,
			capacityPerShard,
			focusLootChance,
			lowLootMultiplier,
			commonLootMultiplier,
			richLootMultiplier,
			treasureLootMultiplier,
			shardFragmentLootMultiplier,
			voidstepCooldownTicks,
			gravebindCooldownTicks,
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			AttunedConfig.DEFAULT.surgeIntervalTicks(),
			AttunedConfig.DEFAULT.surgeDurationTicks(),
			AttunedConfig.DEFAULT.surgeRadius(),
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			AttunedConfig.DEFAULT.affinityLoomMaxShardCost(),
			AttunedConfig.DEFAULT.partyMaxMembers(),
			AttunedConfig.DEFAULT.partySharedCreditRadius(),
			AttunedConfig.DEFAULT.partySharedCreditWindowTicks(),
			AttunedConfig.DEFAULT.partyInviteTtlTicks(),
			AttunedConfig.DEFAULT.partyInviteCooldownTicks(),
			AttunedConfig.DEFAULT.partyCreateCooldownTicks(),
			AttunedConfig.DEFAULT.partyCrossDimensionCreditEnabled(),
			AttunedConfig.DEFAULT.partyHudEnabled(),
			AttunedConfig.DEFAULT.partySharedCreditEnabled(),
			AttunedConfig.DEFAULT.partyTrialAssistsEnabled(),
			AttunedConfig.DEFAULT.partyEffectsEnabled(),
			AttunedConfig.DEFAULT.partyConfluenceHintsEnabled(),
			AttunedConfig.DEFAULT.partySetupSuggestionsEnabled());
	}

	private static void assertInvalid(Executable constructor) {
		assertThrows(IllegalArgumentException.class, constructor,
			"Direct config construction should reject numbers outside the codec contract.");
	}
}
