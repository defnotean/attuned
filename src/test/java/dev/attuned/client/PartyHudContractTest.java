package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for the public Circle party HUD. */
class PartyHudContractTest {
	private static final Path PARTY_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/PartyHud.java");
	private static final Path CLIENT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path CONFIG =
		Path.of("src/client/java/dev/attuned/client/AttunedClientConfig.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void partyHudIsOptionalRegisteredAndTopLeftWithBossBarClearanceByDefault() throws IOException {
		String hud = read(PARTY_HUD);
		String client = read(CLIENT);
		String config = read(CONFIG);

		assertTrue(client.contains("PartyHud.init()"),
			"The client initializer should register the party HUD.");
		assertTrue(config.contains("boolean showPartyHud"),
			"The party HUD should have an independent visibility toggle.");
		assertTrue(config.contains("HudLayout partyHudLayout"),
			"The party HUD should have an independent configurable layout.");
		assertTrue(config.contains("PARTY_BOSS_BAR_CLEARANCE_Y"),
			"The party HUD default should reserve vertical room for vanilla boss bars instead of hugging y=0.");
		assertTrue(hud.contains("HudRenderCallback.EVENT.register(PartyHud::renderLayer)"),
			"The party HUD should register with the 1.21.1 HUD callback.");
		assertTrue(hud.contains("AttunedClientConfig.get().partyHudLayout()"),
			"The party HUD should read its own configured layout.");
		assertTrue(hud.contains("Bounds bounds = bounds(graphics.guiWidth(), graphics.guiHeight(), height, layout)"),
			"The party HUD renderer should use its measured bounds helper.");
		assertTrue(hud.contains("HudAnchor.x(screenW, PARTY_HUD_W, layout)")
				&& hud.contains("HudAnchor.y(screenH, height, layout)"),
			"The party HUD bounds helper should resolve its position through the shared anchor helper.");
		assertTrue(hud.contains("graphics.pose().pushPose()")
				&& hud.contains("graphics.pose().scale(bounds.scale(), bounds.scale(), 1.0F)"),
			"The party HUD should support non-default scale.");
		assertTrue(hud.contains("graphics.pose().popPose()"),
			"The party HUD should pop its scale transform.");
		assertTrue(hud.contains("Math.round(guiWidth / scale)")
				&& hud.contains("Math.round(guiHeight / scale)"),
			"Scaled party HUD drawing should position in scaled coordinate space.");
		assertTrue(hud.contains("PARTY_HUD_W = 124"),
			"The party HUD should have stable compact dimensions.");
		assertTrue(hud.contains("MAX_ROWS = 4"),
			"The party HUD should respect the default Circle member cap.");
	}

	@Test
	void partyHudConsumesOnlyPublicCircleSnapshotRows() throws IOException {
		String hud = read(PARTY_HUD);

		assertTrue(hud.contains("CircleClientState.members()"),
			"The party HUD should render the client mirror of the public server snapshot.");
		assertTrue(hud.contains("CircleClientState.name()"),
			"The party HUD should show the public Circle name.");
		assertTrue(hud.contains("member.leader()"),
			"The party HUD should distinguish the leader from regular members.");
		assertTrue(hud.contains("member.stance()"),
			"The party HUD should show each member's public stance summary.");
		assertTrue(hud.contains("member.role()"),
			"The party HUD should show each member's coarse inferred role label when the server provides one.");
		assertTrue(hud.contains("member.activeCount()") && hud.contains("member.dormantCount()"),
			"The party HUD should show active/dormant counts without listing exact Foci.");
		assertTrue(hud.contains("party.attuned.member.summary"),
			"The party HUD member summary should be translatable.");
		assertTrue(hud.contains("party.attuned.member.summary.role"),
			"The party HUD should use a distinct translation when a member has a public role label.");
		assertTrue(hud.contains("member.role().isBlank()"),
			"The party HUD should fall back cleanly for builds that do not infer a role yet.");
		assertTrue(hud.contains("graphics.drawString"),
			"The party HUD should draw readable member names.");
		assertTrue(hud.contains("trimName("),
			"Long party/member names should be trimmed to fit the compact panel.");
		for (String forbidden : new String[] {"AttunedAttachments", "Attunement.", "Focus", "cooldown", "inventory"}) {
			assertTrue(!hud.contains(forbidden),
				"The party HUD must not read private attunement/inventory/cooldown state: " + forbidden);
		}
	}

	@Test
	void partyHudUsesShapeCodedLeaderAndMemberMarkers() throws IOException {
		String hud = read(PARTY_HUD);
		String leader = methodBody(hud,
			"private static void drawLeaderMark(GuiGraphics graphics, int x, int y)");
		String member = methodBody(hud,
			"private static void drawMemberMark(GuiGraphics graphics, int x, int y)");

		assertTrue(hud.contains("if (member.leader())")
				&& hud.contains("drawLeaderMark(graphics")
				&& hud.contains("drawMemberMark(graphics"),
			"Leader/member status should branch to shape-coded marker helpers, not only swap colours.");
		assertTrue(!hud.contains("int mark = member.leader() ? LEADER_MARK : MEMBER_MARK"),
			"Party row status markers must not collapse to a color-only square.");
		assertTrue(leader.contains("LEADER_MARK"), "Leader marker should still use the leader colour.");
		assertTrue(member.contains("MEMBER_MARK"), "Member marker should still use the member colour.");
		assertTrue(countOccurrences(leader, "graphics.fill(") >= 3,
			"Leader marker should use a multi-rect shape readable without colour.");
		assertTrue(countOccurrences(member, "graphics.fill(") >= 2,
			"Member marker should use a distinct multi-rect shape readable without colour.");
		assertTrue(!leader.equals(member),
			"Leader and member markers should have different silhouettes, not only different colours.");
	}

	@Test
	void partyHudRendersReadableInvitePromptWithoutUsingAdvancementToasts() throws IOException {
		String hud = read(PARTY_HUD);
		String lang = read(LANG);

		assertTrue(hud.contains("CircleClientState.invitePrompt()"),
			"The party HUD should consume the cached server-owned invite prompt.");
		assertTrue(hud.contains("if (members.isEmpty() && invitePrompt.isEmpty())"),
			"An invite prompt should render even before the recipient belongs to a Circle.");
		assertTrue(hud.contains("drawInvitePrompt("),
			"Invite prompt drawing should live in a named helper separate from roster rows.");
		assertTrue(hud.contains("prompt.senderName()"),
			"Invite prompts should show who invited the player.");
		assertTrue(hud.contains("prompt.circleName()"),
			"Invite prompts should show the Circle name.");
		assertTrue(hud.contains("prompt.partySize() + \"/\" + prompt.maxMembers()"),
			"Invite prompts should show party size/capacity.");
		assertTrue(hud.contains("remainingInviteSeconds("),
			"Invite prompts should show a remaining expiration time.");
		assertTrue(!hud.contains("Toast"),
			"Circle invite prompts should not use vanilla toast styling that can be confused with advancements.");
		assertTrue(!hud.contains("Advancement"),
			"Circle invite prompts should not use advancement toast surfaces.");
		for (String key : new String[] {
				"party.attuned.invite.title",
				"party.attuned.invite.sender",
				"party.attuned.invite.size",
				"party.attuned.invite.expires",
				"party.attuned.invite.accept" }) {
			assertTrue(lang.contains("\"" + key + "\""),
				"Party invite HUD prompt should have translatable copy key " + key + ".");
		}
	}

	@Test
	void partyHudRendersLocationOnlyPingMarker() throws IOException {
		String hud = read(PARTY_HUD);
		String lang = read(LANG);

		assertTrue(hud.contains("CircleClientState.ping()"),
			"The party HUD should consume the cached server-owned Circle ping.");
		assertTrue(hud.contains("drawPing("),
			"Circle ping drawing should live in a named helper.");
		assertTrue(hud.contains("ping.sourceName()"),
			"Circle pings should show the member who placed the marker.");
		assertTrue(hud.contains("ping.x()") && hud.contains("ping.y()") && hud.contains("ping.z()"),
			"Circle pings should render a location marker, not an entity target.");
		assertTrue(hud.contains("remainingPingSeconds("),
			"Circle pings should expire from the HUD.");
		assertTrue(!hud.contains("targetEntityId"),
			"Circle ping HUD must not render entity-tracking state.");
		for (String key : new String[] {
				"party.attuned.ping.title",
				"party.attuned.ping.location",
				"party.attuned.ping.expires" }) {
			assertTrue(lang.contains("\"" + key + "\""),
				"Party ping HUD marker should have translatable copy key " + key + ".");
		}
	}

	@Test
	void partyHudHasTranslatableRoleSummaryCopy() throws IOException {
		String lang = read(LANG);

		assertTrue(lang.contains("\"party.attuned.member.summary.role\""),
			"Party HUD role summaries should have dedicated translatable copy.");
	}

	@Test
	void partyHudTruncatesNamesWithFontWidthBudgets() throws IOException {
		String hud = read(PARTY_HUD);
		String trimName = methodBody(hud, "private static String trimName(Font font, String raw, int maxWidth)");

		assertTrue(hud.contains("HEADER_NAME_MAX_W = PARTY_HUD_W - TEXT_PAD"),
			"Party name text should have an explicit header width budget inside the compact panel.");
		assertTrue(hud.contains("MEMBER_NAME_MAX_W = PARTY_HUD_W - TEXT_PAD - 6"),
			"Member name text should reserve room for the leader/member mark.");
		assertTrue(hud.contains("trimName(font, circleName, HEADER_NAME_MAX_W)"),
			"The header draw should use the named party-name width budget.");
		assertTrue(hud.contains("trimName(font, member.name(), MEMBER_NAME_MAX_W)"),
			"Member rows should use the named member-name width budget.");
		assertTrue(trimName.contains("font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ELLIPSIS)))"),
			"Trimming should use Minecraft's font-width helper instead of char-by-char chopping.");
		assertTrue(!trimName.contains("while (!trimmed.isEmpty()"),
			"Party HUD trimming should avoid hand-rolled loops that can still mis-handle variable-width text.");
	}

	@Test
	void partyHudTruncatesTranslatedInviteAndPingLinesAfterComposition() throws IOException {
		String hud = read(PARTY_HUD);
		String invite = methodBody(hud,
			"private static void drawInvitePrompt(GuiGraphics graphics, Font font,");
		String ping = methodBody(hud,
			"private static void drawPing(GuiGraphics graphics, Font font,");
		String drawTrimmedText = methodBody(hud,
			"private static void drawTrimmedText(GuiGraphics graphics, Font font,");
		String trimText = methodBody(hud, "private static String trimText(Font font, Component text, int maxWidth)");

		assertTrue(hud.contains("INVITE_SIZE_MAX_W") && hud.contains("INVITE_EXPIRES_MAX_W"),
			"Invite prompt side-by-side lines need their own width budgets inside the compact panel.");
		assertTrue(invite.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.invite.title\")"),
			"Invite title should go through final-line trimming.");
		assertTrue(invite.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.invite.sender\", sender, circle)"),
			"The composed invite sender/circle sentence should be trimmed after translation args are inserted.");
		assertTrue(invite.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.invite.size\", size)"),
			"Invite size text should stay inside its half-row budget.");
		assertTrue(invite.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.invite.expires\", seconds)"),
			"Invite expiry text should stay inside its half-row budget.");
		assertTrue(invite.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.invite.accept\", accept)"),
			"Invite accept command should be trimmed after final copy composition.");
		assertTrue(ping.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.ping.title\", sender)"),
			"Ping title should trim the composed sender phrase.");
		assertTrue(ping.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.ping.location\", location)"),
			"Ping location should trim the composed coordinate phrase.");
		assertTrue(ping.contains("drawTrimmedText(graphics, font, Component.translatable(\"party.attuned.ping.expires\", seconds)"),
			"Ping expiry should trim final copy as rendered.");
		assertTrue(drawTrimmedText.contains("graphics.drawString(font, trimText(font, text, maxWidth)"),
			"Translated HUD copy should be clipped by one helper before drawing.");
		assertTrue(trimText.contains("text == null ? \"\" : text.getString()"),
			"Final-line trimming should operate on the rendered text copy, not only pre-trimmed arguments.");
		assertTrue(trimText.contains("font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ELLIPSIS)))"),
			"Translated line trimming should use Minecraft's font-width helper.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
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

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
