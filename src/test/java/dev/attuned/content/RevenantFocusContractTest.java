package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Contract coverage for The Revenant faction launch. */
class RevenantFocusContractTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path BEHAVIOR_REGISTRATION_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path REVENANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RevenantFocusBehaviors.java");
	private static final Path REVENANT_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");
	private static final Path DAMAGE_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityHurtMixin.java");
	private static final Path COMMON_INIT =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path SOURCE_ART =
		Path.of("docs/superpowers/assets/revenant-foci/revenant-foci-concept-source.png");
	private static final Path REPORT =
		Path.of("docs/superpowers/assets/revenant-foci/revenant-foci-report.json");
	private static final Path TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/item");

	private static final Set<String> NEW_REVENANT_FOCI = Set.of(
		"epitaph_focus",
		"ashen_debt_focus",
		"hollowstep_focus",
		"last_rites_focus",
		"bonechill_focus");

	@Test
	void revenantFociAreRegisteredListedAndBehaviorBacked() throws IOException {
		String content = read(CONTENT_SOURCE);
		String registrations = read(BEHAVIOR_REGISTRATION_SOURCE);

		for (String name : NEW_REVENANT_FOCI) {
			String field = name.substring(0, name.length() - "_focus".length()).toUpperCase() + "_FOCUS";
			assertTrue(content.contains("public static final Item " + field + " = registerFocus(\"" + name + "\")"),
				"AttunedContent should register " + name);
		}
		assertTrue(content.contains("public static final List<Item> FOCI = List.copyOf(REGISTERED_FOCI);"),
			"AttunedContent.FOCI should follow Focus registration order");
		assertTrue(registrations.contains("register(\"epitaph\", new RevenantFocusBehaviors.Epitaph())"),
			"Epitaph behavior should be registered");
		assertTrue(registrations.contains("register(\"hollowstep\", new RevenantFocusBehaviors.Hollowstep())"),
			"Hollowstep behavior should be registered");
	}

	@Test
	void revenantCombatHooksAreServerAuthoritativeAndPvpAware() throws IOException {
		String combat = read(REVENANT_COMBAT);
		String mixin = read(DAMAGE_MIXIN);
		String init = read(COMMON_INIT);

		assertTrue(init.contains("RevenantCombat.init()"),
			"Common init should register Revenant combat hooks");
		assertTrue(mixin.contains("RevenantCombat.adjustDamage"),
			"Ashen Debt should adjust damage before armor through the common hurt mixin");
		assertTrue(combat.contains("DEBT_WINDOW_TICKS = 160"),
			"Ashen Debt should keep its revenge window short");
		assertTrue(combat.contains("DEBT_MULTIPLIER = 1.25F"),
			"Ashen Debt should keep the revenge hit controlled");
		assertTrue(combat.contains("MobEffects.SLOWNESS") || combat.contains("MobEffects.MOVEMENT_SLOWDOWN"),
			"Bonechill should apply a real vanilla slow effect");
		assertTrue(combat.contains("CombatTargets.isHostileOrPvpOpponent(attacker, player)"),
			"Bonechill should respect PvP and hostile target rules");
		assertTrue(combat.contains("CombatTargets.isHostileOrPvpOpponent(entity, player)"),
			"Last Rites should respect PvP and hostile target rules");
		assertTrue(combat.contains("removeEffect(MobEffects.POISON)")
				&& combat.contains("removeEffect(MobEffects.WITHER)")
				&& (combat.contains("removeEffect(MobEffects.SLOWNESS)")
					|| combat.contains("removeEffect(MobEffects.MOVEMENT_SLOWDOWN)"))
				&& combat.contains("player.clearFire()"),
			"Last Rites should cleanse the intended harmful states");
	}

	@Test
	void hollowstepAndEpitaphUseTheSingleAbilityAndUtilityPaths() throws IOException {
		String behaviors = read(REVENANT_BEHAVIORS);

		assertTrue(behaviors.contains("class Epitaph implements FocusBehavior"),
			"Epitaph should be a normal server-side Focus behavior");
		assertTrue(behaviors.contains("ExperienceOrb"),
			"Epitaph should pull nearby experience orbs as death-memory utility");
		assertTrue(behaviors.contains("class Hollowstep implements FocusBehavior"),
			"Hollowstep should be a normal server-side Focus behavior");
		assertTrue(behaviors.contains("hasActiveAbility()")
				&& behaviors.contains("return true;")
				&& behaviors.contains("COOLDOWN_TICKS = 180"),
			"Hollowstep should own the single Focus Ability key with a cooldown");
		assertTrue(behaviors.contains("MAX_DISTANCE = 5"),
			"Hollowstep should stay shorter than Voidstep");
		assertTrue(behaviors.contains("level.noCollision(player, box)"),
			"Hollowstep should ensure the destination space fits the player");
		assertTrue(behaviors.contains("ClipContext.Block.COLLIDER")
				&& behaviors.contains("HitResult.Type.BLOCK"),
			"Hollowstep should not phase through walls before reaching that destination");
	}

	@Test
	void revenantArtKeepsSourceAndShipsAnimatedSheets() throws IOException {
		assertTrue(Files.isRegularFile(SOURCE_ART),
			"Revenant Foci should keep their concept source");
		JsonObject report = JsonParser.parseString(read(REPORT)).getAsJsonObject();
		assertEquals("concept_source_chroma_key_crops_with_pulsed_revenant_glow",
			report.get("strategy").getAsString(),
			"Revenant texture report should document the source-art pipeline");

		for (String name : NEW_REVENANT_FOCI) {
			Path texture = TEXTURE_DIR.resolve(name + ".png");
			Path metadata = TEXTURE_DIR.resolve(name + ".png.mcmeta");
			assertTrue(Files.isRegularFile(texture), "Missing Revenant texture: " + texture);
			assertTrue(Files.isRegularFile(metadata), "Missing Revenant animation metadata: " + metadata);
			BufferedImage image = ImageIO.read(texture.toFile());
			assertEquals(64, image.getWidth(), "Revenant Focus texture width should match shipped Foci");
			assertEquals(512, image.getHeight(), "Revenant Focus texture should have eight frames");
		}
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
