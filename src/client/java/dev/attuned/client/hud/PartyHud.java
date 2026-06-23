package dev.attuned.client.hud;

import dev.attuned.Attuned;
import dev.attuned.client.AttunedClientConfig;
import dev.attuned.client.CircleClientState;
import dev.attuned.network.CircleInvitePromptPayload;
import dev.attuned.network.CirclePingClientPayload;
import dev.attuned.network.CircleSnapshotPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Compact public Circle roster HUD. */
public final class PartyHud {
	static final int PARTY_HUD_W = 124;
	private static final int HEADER_H = 12;
	private static final int ROW_H = 20;
	private static final int ROW_GAP = 1;
	private static final int MAX_ROWS = 4;
	private static final int INVITE_H = 44;
	private static final int PING_H = 34;
	private static final int TEXT_X = 6;
	private static final int TEXT_PAD = 12;
	private static final int HEADER_NAME_MAX_W = PARTY_HUD_W - TEXT_PAD;
	private static final int MEMBER_NAME_MAX_W = PARTY_HUD_W - TEXT_PAD - 6;
	private static final int MEMBER_SUMMARY_MAX_W = PARTY_HUD_W - TEXT_PAD - 6;
	private static final int INVITE_TEXT_MAX_W = PARTY_HUD_W - TEXT_PAD;
	private static final int INVITE_SIZE_MAX_W = 48;
	private static final int INVITE_EXPIRES_MAX_W = PARTY_HUD_W - TEXT_X - 54 - 2;
	private static final int PING_TEXT_MAX_W = PARTY_HUD_W - TEXT_PAD;
	private static final String ELLIPSIS = "...";
	private static final int PANEL_BG = 0x8C080812;
	private static final int HEADER_BG = 0xA0181530;
	private static final int ROW_BG = 0x70121120;
	private static final int INVITE_BG = 0xA01A1830;
	private static final int PING_BG = 0xA0101F2B;
	private static final int INVITE_ACCENT = 0xFFAEEAFF;
	private static final int PING_ACCENT = 0xFF8CFFD4;
	private static final int LEADER_MARK = 0xFFE8C85A;
	private static final int MEMBER_MARK = 0xFF78B8FF;
	private static final int HEADER_TEXT = 0xFFEADFFF;
	private static final int MEMBER_TEXT = 0xFFE8EEF8;
	private static boolean initialized;

	private PartyHud() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "party_hud");
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id, PartyHud::renderLayer);
	}

	private static void renderLayer(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !AttunedClientConfig.get().showPartyHud()) {
			return;
		}
		List<CircleSnapshotPayload.Member> members = CircleClientState.members();
		long nowTick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		Optional<CircleInvitePromptPayload> invitePrompt = CircleClientState.invitePrompt()
			.filter(prompt -> remainingInviteSeconds(prompt, nowTick) > 0L);
		Optional<CirclePingClientPayload> ping = CircleClientState.ping()
			.filter(marker -> remainingPingSeconds(marker, nowTick) > 0L);
		if (members.isEmpty() && invitePrompt.isEmpty()) {
			if (ping.isEmpty()) {
				return;
			}
		}
		draw(graphics, minecraft, CircleClientState.name(), members, invitePrompt, ping, nowTick);
	}

	private static void draw(GuiGraphics graphics, Minecraft minecraft,
			String circleName, List<CircleSnapshotPayload.Member> members,
			Optional<CircleInvitePromptPayload> invitePrompt,
			Optional<CirclePingClientPayload> ping, long nowTick) {
		Font font = minecraft.font;
		int rows = Math.min(MAX_ROWS, members.size());
		int rosterHeight = rows == 0 ? 0 : HEADER_H + rows * ROW_H + Math.max(0, rows - 1) * ROW_GAP;
		int inviteHeight = invitePrompt.isPresent() ? INVITE_H : 0;
		int pingHeight = ping.isPresent() ? PING_H : 0;
		int promptGap = invitePrompt.isPresent() && (ping.isPresent() || rows > 0) ? ROW_GAP : 0;
		int pingGap = ping.isPresent() && rows > 0 ? ROW_GAP : 0;
		int height = inviteHeight + promptGap + pingHeight + pingGap + rosterHeight;
		AttunedClientConfig.HudLayout layout = AttunedClientConfig.get().partyHudLayout();
		Bounds bounds = bounds(graphics.guiWidth(), graphics.guiHeight(), height, layout);
		int x = bounds.x();
		int y = bounds.y();
		if (bounds.scaled()) {
			graphics.pose().pushMatrix();
			graphics.pose().scale(bounds.scale(), bounds.scale());
		}

		graphics.fill(x, y, x + PARTY_HUD_W, y + height, PANEL_BG);
		int cursorY = y;
		if (invitePrompt.isPresent()) {
			drawInvitePrompt(graphics, font, invitePrompt.orElseThrow(), x, cursorY, nowTick);
			cursorY += INVITE_H + promptGap;
		}

		if (ping.isPresent()) {
			drawPing(graphics, font, ping.orElseThrow(), x, cursorY, nowTick);
			cursorY += PING_H + pingGap;
		}

		if (rows > 0) {
			graphics.fill(x, cursorY, x + PARTY_HUD_W, cursorY + HEADER_H, HEADER_BG);
			graphics.drawString(font, trimName(font, circleName, HEADER_NAME_MAX_W),
				x + TEXT_X, cursorY + 2, HEADER_TEXT, false);
		}

		int rowY = cursorY + HEADER_H;
		for (int index = 0; index < rows; index++) {
			CircleSnapshotPayload.Member member = members.get(index);
			graphics.fill(x, rowY, x + PARTY_HUD_W, rowY + ROW_H, ROW_BG);
			if (member.leader()) {
				drawLeaderMark(graphics, x + 3, rowY + 3);
			} else {
				drawMemberMark(graphics, x + 3, rowY + 3);
			}
			graphics.drawString(font, trimName(font, member.name(), MEMBER_NAME_MAX_W),
				x + TEXT_X + 6, rowY + 1, MEMBER_TEXT, false);
			String summary = trimName(font, memberSummary(member).getString(), MEMBER_SUMMARY_MAX_W);
			graphics.drawString(font, summary, x + TEXT_X + 6, rowY + 10, 0xFFB7C6D8, false);
			rowY += ROW_H + ROW_GAP;
		}

		if (bounds.scaled()) {
			graphics.pose().popMatrix();
		}
	}

	private static Component memberSummary(CircleSnapshotPayload.Member member) {
		if (member.role().isBlank()) {
			return Component.translatable("party.attuned.member.summary",
				member.stance(), member.activeCount(), member.dormantCount());
		}
		return Component.translatable("party.attuned.member.summary.role",
			member.stance(), member.role(), member.activeCount(), member.dormantCount());
	}

	static Bounds bounds(int guiWidth, int guiHeight, int height,
			AttunedClientConfig.HudLayout layout) {
		float scale = layout.scale();
		boolean scaled = scale != 1.0F;
		int screenW = scaled ? Math.round(guiWidth / scale) : guiWidth;
		int screenH = scaled ? Math.round(guiHeight / scale) : guiHeight;
		return new Bounds(HudAnchor.x(screenW, PARTY_HUD_W, layout),
			HudAnchor.y(screenH, height, layout), PARTY_HUD_W, height, scale, screenW, screenH);
	}

	record Bounds(int x, int y, int width, int height, float scale,
			int scaledScreenW, int scaledScreenH) {
		boolean scaled() {
			return scale != 1.0F;
		}
	}

	private static void drawLeaderMark(GuiGraphics graphics, int x, int y) {
		graphics.fill(x, y + 3, x + 7, y + 5, LEADER_MARK);
		graphics.fill(x + 1, y + 1, x + 3, y + 3, LEADER_MARK);
		graphics.fill(x + 4, y, x + 6, y + 3, LEADER_MARK);
	}

	private static void drawMemberMark(GuiGraphics graphics, int x, int y) {
		graphics.fill(x + 2, y, x + 5, y + 1, MEMBER_MARK);
		graphics.fill(x + 1, y + 1, x + 6, y + 3, MEMBER_MARK);
		graphics.fill(x + 2, y + 3, x + 5, y + 4, MEMBER_MARK);
	}

	private static void drawInvitePrompt(GuiGraphics graphics, Font font,
			CircleInvitePromptPayload prompt, int x, int y, long nowTick) {
		String sender = trimName(font, prompt.senderName(), INVITE_TEXT_MAX_W);
		String circle = trimName(font, prompt.circleName(), INVITE_TEXT_MAX_W);
		String size = prompt.partySize() + "/" + prompt.maxMembers();
		String accept = trimName(font, "/attuned circle accept " + sender, INVITE_TEXT_MAX_W);
		long seconds = remainingInviteSeconds(prompt, nowTick);

		graphics.fill(x, y, x + PARTY_HUD_W, y + INVITE_H, INVITE_BG);
		graphics.fill(x, y, x + 2, y + INVITE_H, INVITE_ACCENT);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.invite.title"),
			x + TEXT_X, y + 3, HEADER_TEXT, INVITE_TEXT_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.invite.sender", sender, circle),
			x + TEXT_X, y + 13, MEMBER_TEXT, INVITE_TEXT_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.invite.size", size),
			x + TEXT_X, y + 23, MEMBER_TEXT, INVITE_SIZE_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.invite.expires", seconds),
			x + TEXT_X + 54, y + 23, MEMBER_TEXT, INVITE_EXPIRES_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.invite.accept", accept),
			x + TEXT_X, y + 33, MEMBER_TEXT, INVITE_TEXT_MAX_W);
	}

	private static void drawPing(GuiGraphics graphics, Font font,
			CirclePingClientPayload ping, int x, int y, long nowTick) {
		String sender = trimName(font, ping.sourceName(), PING_TEXT_MAX_W);
		String location = Math.round(ping.x()) + ", " + Math.round(ping.y()) + ", "
			+ Math.round(ping.z());
		long seconds = remainingPingSeconds(ping, nowTick);

		graphics.fill(x, y, x + PARTY_HUD_W, y + PING_H, PING_BG);
		graphics.fill(x, y, x + 2, y + PING_H, PING_ACCENT);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.ping.title", sender),
			x + TEXT_X, y + 3, HEADER_TEXT, PING_TEXT_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.ping.location", location),
			x + TEXT_X, y + 14, MEMBER_TEXT, PING_TEXT_MAX_W);
		drawTrimmedText(graphics, font, Component.translatable("party.attuned.ping.expires", seconds),
			x + TEXT_X, y + 24, MEMBER_TEXT, PING_TEXT_MAX_W);
	}

	private static long remainingInviteSeconds(CircleInvitePromptPayload prompt, long nowTick) {
		long remainingTicks = Math.max(0L, prompt.expiresAtTick() - nowTick);
		return (remainingTicks + 19L) / 20L;
	}

	private static long remainingPingSeconds(CirclePingClientPayload ping, long nowTick) {
		long remainingTicks = Math.max(0L, ping.expiresAtTick() - nowTick);
		return (remainingTicks + 19L) / 20L;
	}

	private static String trimName(Font font, String raw, int maxWidth) {
		String value = raw == null || raw.isBlank() ? "Circle" : raw;
		if (font.width(value) <= maxWidth) {
			return value;
		}
		return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
	}

	private static void drawTrimmedText(GuiGraphics graphics, Font font,
			Component text, int x, int y, int color, int maxWidth) {
		graphics.drawString(font, trimText(font, text, maxWidth), x, y, color, false);
	}

	private static String trimText(Font font, Component text, int maxWidth) {
		String value = text == null ? "" : text.getString();
		if (font.width(value) <= maxWidth) {
			return value;
		}
		return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
	}
}
