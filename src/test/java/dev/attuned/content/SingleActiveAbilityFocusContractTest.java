package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.api.focus.FocusBehavior;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract coverage for the one-active-ability-Focus rule. */
class SingleActiveAbilityFocusContractTest {
	private static final Path NETWORKING_SOURCE =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path ABILITY_STATE_SOURCE =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path KEYBINDS_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedKeybinds.java");
	private static final Path BEHAVIOR_DIR =
		Path.of("src/main/java/dev/attuned/content/behavior");

	@Test
	void focusBehaviorsOptIntoTheFocusAbilityKey() throws ReflectiveOperationException {
		Method method = FocusBehavior.class.getMethod("hasActiveAbility");

		assertEquals(boolean.class, method.getReturnType());
		assertFalse((boolean) method.invoke(new FocusBehavior() {}),
			"Passive or tick-only Focus behaviors should not respond to the ability key by default.");
	}

	@Test
	void everyBehaviorThatOverridesOnAbilityDeclaresItselfAnAbilityFocus() throws IOException {
		List<String> missingOptIn;
		try (Stream<Path> files = Files.list(BEHAVIOR_DIR)) {
			missingOptIn = files
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.filter(path -> {
					String source = readUnchecked(path);
					return source.contains("public boolean onAbility(")
						&& !source.contains("public boolean hasActiveAbility()");
				})
				.map(path -> path.getFileName().toString())
				.sorted()
				.toList();
		}

		assertEquals(List.of(), missingOptIn,
			"Any behavior with a keybind ability must explicitly opt into the single active ability slot.");
	}

	@Test
	void abilityPacketTriggersOnlyTheFirstActiveAbilityFocus() throws IOException {
		String networking = Files.readString(NETWORKING_SOURCE, StandardCharsets.UTF_8);
		String state = Files.readString(ABILITY_STATE_SOURCE, StandardCharsets.UTF_8);
		String keybinds = Files.readString(KEYBINDS_SOURCE, StandardCharsets.UTF_8);

		assertTrue(networking.contains("FocusAbilityState.trigger(player)"),
			"The server receiver should delegate to the singular active ability state path.");
		assertTrue(state.contains("hasActiveAbility(behavior, player, stack)"),
			"The server should ignore passive/tick-only behaviors when choosing the ability Focus.");
		assertTrue(state.contains("boolean fired = runAbility(selection.behavior(), player, selection.stack())")
				&& state.contains("return new AbilitySelection"),
			"The server should stop after firing the first active ability Focus.");
		assertTrue(state.contains("PactTacticals.tryTrigger(player)"),
			"When no ability Focus is equipped, pact tacticals should run before Apex.");
		assertTrue(!networking.contains("triggers FocusBehavior#onAbility")
				&& !networking.contains("every one of that player's active Foci"),
			"Networking comments should not promise that every active Focus fires.");
		assertTrue(keybinds.contains("one active ability Focus"),
			"Client-side keybind docs should match the one-active-ability server rule.");
	}

	private static String readUnchecked(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read " + path, e);
		}
	}
}
