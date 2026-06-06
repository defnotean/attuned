package dev.attuned.client.hud;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.client.AttunedClientConfig;
import dev.attuned.client.FocusAbilityClientState;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.menu.FocusLayout;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Compact gameplay HUD for the player's equipped Foci and active ability state. */
public final class FociHud {
	private FociHud() {}

	private static final Identifier FRAME_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/foci_hud.png");

	static final int HUD_W = 64;
	static final int HUD_H = 96;
	private static final int ABILITY_WELL_X = 5;
	private static final int ABILITY_WELL_Y = 5;
	private static final int ABILITY_WELL_SIZE = 22;
	private static final int FOCUS_GRID_X = 5;
	private static final int FOCUS_GRID_Y = 37;
	private static final int FOCUS_GRID_COLUMNS = 2;
	private static final int FOCUS_GRID_ROWS = 3;
	private static final int FOCUS_GRID_GAP_X = 6;
	private static final int FOCUS_GRID_GAP_Y = 1;
	private static final int APEX_GEM_X = 39;
	private static final int APEX_GEM_Y = 6;
	private static final int APEX_GEM_SIZE = 14;
	private static final int APEX_BAR_X = 5;
	private static final int APEX_BAR_Y = 29;
	private static final int APEX_BAR_W = 54;
	private static final int APEX_BAR_H = 6;
	private static final int SCREEN_MARGIN = 4;
	private static final int ACTIVE_GLOW_ALPHA = 0xA0;
	private static final int DORMANT_DIM = 0x78000000;
	private static final int COOLDOWN_SHADE = 0x9C0A0812;
	private static final int COOLDOWN_RING = 0xDCCB93FF;
	private static final int BAR_TRACK = 0xB0111118;
	private static final int BAR_EMPTY_FILL = 0x663A2E64;
	private static final int APEX_MARK = 0xD8F4D06A;
	private static final int FRAME_GLOW = 0xB46D4FD8;
	private static final int FRAME_EDGE = 0xD49FC8FF;
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "foci_hud");
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id, FociHud::renderLayer);
	}

	public static boolean isVisible(Player player) {
		return AttunedClientConfig.get().showFociHud();
	}

	private static void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || !isVisible(player)) {
			return;
		}
		draw(graphics, player);
	}

	private static void draw(GuiGraphicsExtractor graphics, Player player) {
		int x = primarySidecarX(graphics.guiWidth(), HUD_W);
		int y = primarySidecarY(graphics.guiWidth(), graphics.guiHeight(), HUD_W);

		graphics.blit(RenderPipelines.GUI_TEXTURED, FRAME_TEXTURE, x, y,
			0.0F, 0.0F, HUD_W, HUD_H, HUD_W, HUD_H);
		drawFrameGlow(graphics, x, y);

		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<Integer> activeSlots = Attunement.activeSlots(player);
		Map<Integer, BudgetResolver.DormantReason> dormantReasons = Attunement.dormantReasons(player);

		drawAbilityWell(graphics, player, inv, x + ABILITY_WELL_X, y + ABILITY_WELL_Y);
		drawApexBar(graphics, player, x + APEX_GEM_X, y + APEX_GEM_Y, x + APEX_BAR_X, y + APEX_BAR_Y);
		drawFocusGrid(graphics, player, inv, activeSlots, dormantReasons, x + FOCUS_GRID_X, y + FOCUS_GRID_Y);
	}

	static int primarySidecarX(int screenW, int hudWidth) {
		return Math.max(SCREEN_MARGIN, screenW - hudWidth - SCREEN_MARGIN);
	}

	private static int primarySidecarY(int screenW, int screenH, int hudWidth) {
		return Math.max(SCREEN_MARGIN, screenH - HUD_H - SCREEN_MARGIN);
	}

	private static void drawFrameGlow(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 4, y, x + HUD_W - 4, y + 1, FRAME_EDGE);
		graphics.fill(x + 4, y + HUD_H - 1, x + HUD_W - 4, y + HUD_H, FRAME_EDGE);
		graphics.fill(x, y + 4, x + 1, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + HUD_W - 1, y + 4, x + HUD_W, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + 4, y + 34, x + HUD_W - 4, y + 35, FRAME_GLOW);
	}

	private static void drawAbilityWell(GuiGraphicsExtractor graphics, Player player, AttunedInv inv, int x, int y) {
		int slot = selectedAbilitySlot(player, inv);
		if (slot >= 0) {
			ItemStack stack = inv.get(slot);
			graphics.item(stack, x + (ABILITY_WELL_SIZE - 16) / 2, y + (ABILITY_WELL_SIZE - 16) / 2);
		}
		int remaining = FocusAbilityClientState.remainingTicks();
		int total = FocusAbilityClientState.totalTicks();
		if (remaining > 0 && total > 0) {
			drawCooldownRing(graphics, x, y, ABILITY_WELL_SIZE, remainingTicks(), total);
		}
	}

	private static int remainingTicks() {
		return FocusAbilityClientState.remainingTicks();
	}

	private static int selectedAbilitySlot(Player player, AttunedInv inv) {
		int syncedSlot = FocusAbilityClientState.slot();
		if (syncedSlot >= 0 && syncedSlot < AttunedInv.SIZE && !inv.get(syncedSlot).isEmpty()) {
			return syncedSlot;
		}
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inv.get(slot);
			FocusBehavior behavior = Attunement.definitionFor(player, stack)
				.flatMap(FocusDefinition::behavior)
				.map(AttunedRegistries::getBehavior)
				.orElse(null);
			if (behavior != null && behavior.hasActiveAbility()) {
				return slot;
			}
		}
		return -1;
	}

	private static void drawFocusGrid(GuiGraphicsExtractor graphics, Player player, AttunedInv inv,
			List<Integer> activeSlots, Map<Integer, BudgetResolver.DormantReason> dormantReasons, int x, int y) {
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			int column = slot % FOCUS_GRID_COLUMNS;
			int row = slot / FOCUS_GRID_COLUMNS;
			if (row >= FOCUS_GRID_ROWS) {
				continue;
			}
			int sx = x + column * (FocusLayout.SLOT + FOCUS_GRID_GAP_X);
			int sy = y + row * (FocusLayout.SLOT + FOCUS_GRID_GAP_Y);
			ItemStack stack = inv.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (activeSlots.contains(slot)) {
				drawActiveGlow(graphics, sx, sy, focusColor(player, stack));
			} else if (dormantReasons.containsKey(slot)) {
				drawDormantOverlay(graphics, sx, sy);
			}
			graphics.item(stack, sx + FocusLayout.SLOT_INSET, sy + FocusLayout.SLOT_INSET);
		}
	}

	private static int focusColor(Player player, ItemStack stack) {
		return Attunement.definitionFor(player, stack)
			.flatMap(FocusDefinition::affinity)
			.map(Affinity::argb)
			.orElse(AffinityColors.NEUTRAL_ARGB);
	}

	private static void drawActiveGlow(GuiGraphicsExtractor graphics, int x, int y, int color) {
		int argb = (ACTIVE_GLOW_ALPHA << 24) | (color & 0x00FFFFFF);
		int x1 = x + FocusLayout.SLOT;
		int y1 = y + FocusLayout.SLOT;
		graphics.fill(x, y, x1, y + 1, argb);
		graphics.fill(x, y1 - 1, x1, y1, argb);
		graphics.fill(x, y + 1, x + 1, y1 - 1, argb);
		graphics.fill(x1 - 1, y + 1, x1, y1 - 1, argb);
	}

	private static void drawDormantOverlay(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 1, y + 1, x + FocusLayout.SLOT - 1, y + FocusLayout.SLOT - 1, DORMANT_DIM);
	}

	private static void drawApexBar(GuiGraphicsExtractor graphics, Player player, int gemX, int gemY, int barX, int barY) {
		Optional<Apex.Capstone> capstone = Apex.capstoneOf(player);
		Optional<Affinity> committed = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		boolean atApex = capstone.isPresent() && Resonance.atApex(player);
		CombatHud.drawPlayerGem(graphics, gemX, gemY, APEX_GEM_SIZE,
			committed.orElse(null), discord, capstone.orElse(null), atApex);

		graphics.fill(barX - 1, barY - 1, barX + APEX_BAR_W + 1, barY + APEX_BAR_H + 1, BAR_EMPTY_FILL);
		graphics.fill(barX, barY, barX + APEX_BAR_W, barY + APEX_BAR_H, BAR_TRACK);
		graphics.fill(barX + 1, barY + 1, barX + APEX_BAR_W - 1, barY + APEX_BAR_H - 1, BAR_EMPTY_FILL);
		float resonance = Math.max(0.0F, Math.min(1.0F, Resonance.get(player)));
		int fill = Math.round(APEX_BAR_W * resonance);
		if (fill > 0) {
			int color = capstone.map(Apex.Capstone::argb)
				.orElse(committed.map(Affinity::argb).orElse(AffinityColors.NEUTRAL_ARGB));
			graphics.fill(barX, barY, barX + fill, barY + APEX_BAR_H, 0xD0000000 | (color & 0x00FFFFFF));
		}
		int thresholdX = barX + Math.round(APEX_BAR_W * Resonance.APEX_THRESHOLD);
		graphics.fill(thresholdX, barY - 1, thresholdX + 1, barY + APEX_BAR_H + 1, APEX_MARK);
	}

	private static void drawCooldownRing(GuiGraphicsExtractor graphics, int x, int y, int size,
			int remainingTicks, int totalTicks) {
		graphics.fill(x + 3, y + 3, x + size - 3, y + size - 3, COOLDOWN_SHADE);
		float progress = Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) totalTicks));
		int perimeter = (size - 1) * 4;
		int lit = Math.max(1, Math.round(perimeter * progress));
		drawCooldownEdge(graphics, x, y, size, lit);
	}

	private static void drawCooldownEdge(GuiGraphicsExtractor graphics, int x, int y, int size, int lit) {
		int edge = size - 1;
		int top = Math.min(lit, edge);
		if (top > 0) {
			graphics.fill(x, y, x + top, y + 2, COOLDOWN_RING);
		}
		int remaining = lit - edge;
		if (remaining > 0) {
			int right = Math.min(remaining, edge);
			graphics.fill(x + size - 2, y, x + size, y + right, COOLDOWN_RING);
			remaining -= edge;
		}
		if (remaining > 0) {
			int bottom = Math.min(remaining, edge);
			graphics.fill(x + size - bottom, y + size - 2, x + size, y + size, COOLDOWN_RING);
			remaining -= edge;
		}
		if (remaining > 0) {
			int left = Math.min(remaining, edge);
			graphics.fill(x, y + size - left, x + 2, y + size, COOLDOWN_RING);
		}
	}
}
