package dev.attuned.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedCommandsContractTest {
	private static final Path COMMANDS =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");

	@Test
	void commandTreeDoesNotShipGuiPreviewDebugCommands() throws IOException {
		String source = read(COMMANDS);

		assertTrue(!source.contains("Commands.literal(\"gui\")"),
			"GUI preview/debug commands should not ship; use tools/render_gui_previews.py and the asset customizer.");
		assertTrue(!source.contains("openAltarGuiPreview"),
			"Attunement Altar GUI previews should stay in the out-of-game tooling.");
		assertTrue(!source.contains("openReweavingGuiPreview"),
			"Reweaving GUI previews should stay in the out-of-game tooling.");
		assertTrue(!source.contains("previewAltarInput"),
			"Preview-only inventory setup should not live in the runtime command class.");
		assertTrue(!source.contains("previewReweavingContainer"),
			"Preview-only inventory setup should not live in the runtime command class.");
	}

	@Test
	void circleLifecycleCommandsWrapTheServerOwnedManager() throws IOException {
		String source = read(COMMANDS);

		assertTrue(source.contains("import com.mojang.brigadier.arguments.StringArgumentType;"),
			"Circle creation should accept a sanitized user-facing name.");
		assertTrue(source.contains("import dev.attuned.party.CircleContributions;"),
			"Circle lifecycle commands should clear stale contribution windows after membership changes.");
		assertTrue(source.contains("import net.minecraft.commands.arguments.EntityArgument;"),
			"Circle invite/accept/kick should resolve online players on the server.");
		assertTrue(source.contains("Commands.literal(\"circle\")"),
			"The /attuned command tree should expose a Circle lifecycle group.");
		for (String literal : new String[] {"create", "invite", "accept", "leave", "disband", "kick"}) {
			assertTrue(source.contains("Commands.literal(\"" + literal + "\")"),
				"Circle commands should expose /attuned circle " + literal + ".");
		}
		assertTrue(source.contains("CircleRuntime.manager().create(player.getUUID(), StringArgumentType.getString(ctx, \"name\"), player.getLevel().getGameTime())"),
			"Create should use the sender as leader, server-owned manager, and server time for create cooldowns.");
		assertTrue(source.contains("CircleRuntime.manager().invite(player.getUUID(), target.getUUID(), player.getLevel().getGameTime())"),
			"Invite should use the sender as actor and an online server-resolved target.");
		assertTrue(source.contains("CircleRuntime.manager().accept(player.getUUID(), inviter.getUUID(), player.getLevel().getGameTime())"),
			"Accept should use the sender as accepting target and a server-resolved inviter.");
		assertTrue(source.contains("CircleRuntime.manager().leave(player.getUUID(), player.getLevel().getGameTime())"),
			"Leave should only affect the sender and use server time for disband cooldowns.");
		assertTrue(source.contains("CircleRuntime.manager().disband(player.getUUID(), player.getLevel().getGameTime())"),
			"Disband should use the sender as actor and server time for create cooldowns.");
		assertTrue(source.contains("onlineCircleMembers(player, previousCircle)"),
			"Disband should capture old Circle members before mutation so every former member gets a clear snapshot.");
		assertTrue(source.contains("CircleRuntime.manager().kick(player.getUUID(), target.getUUID())"),
			"Kick should use the sender as actor and let CirclePolicy enforce leadership.");
		assertTrue(source.contains("sendCircleResult(source, result.status())"),
			"Circle commands should report the policy status instead of failing silently.");
		assertTrue(source.contains("sendCircleInviteNotice(target, player, result.circle().orElseThrow())"),
			"Invite commands should notify the target player with sender, Circle, size, and expiry.");
		assertTrue(source.contains("NetworkPackets.send(target, new CircleInvitePromptPayload("),
			"Invite commands should send the same bounded client prompt as packet invites.");
		assertTrue(source.contains("circle.invites().get(target.getUUID()).expiresAtTick()"),
			"Invite command prompts should carry the server-owned invite expiration tick.");
		assertTrue(source.contains("CircleRuntime.maxMembers()"),
			"Invite commands should include the Circle cap in the invite notice.");
		assertTrue(source.contains("CircleRuntime.inviteTtlSeconds()"),
			"Invite commands should include the invite expiration window.");
	}

	@Test
	void membershipChangingCircleCommandsClearContributionWindows() throws IOException {
		String source = read(COMMANDS);

		assertTrue(countOccurrences(source, "CircleContributions.forgetIfMembershipChanged(result, player);") >= 3,
			"Create, accept, and leave commands should clear the actor's stale contribution window after success.");
		assertTrue(source.contains("CircleContributions.forgetIfMembershipChanged(result, affected);"),
			"Disband command should clear stale contribution windows for every former online member.");
		assertTrue(source.contains("CircleContributions.forgetIfMembershipChanged(result, target);"),
			"Kick command should clear stale contribution windows for the kicked target after success.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
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
}
