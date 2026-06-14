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
import dev.attuned.client.AttunementReadout;
import dev.attuned.client.FocusAbilityClientState;
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
	// Confluence count chip: small pips in the top gap between the ability well and the Apex gem.
	private static final int CONFLUENCE_CHIP_X = 28;
	private static final int CONFLUENCE_CHIP_Y = 5;
	private static final int CONFLUENCE_PIP = 2;
	private static final int CONFLUENCE_PIP_STEP = 3;
	private static final int CONFLUENCE_CHIP_COLUMNS = 2;
	private static final int CONFLUENCE_MAX_PIPS = 6;
	private static final int CONFLUENCE_PIP_COLOR = 0xE0B9E8FF;
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
		AttunedClientConfig.HudLayout layout = HudAnchor.layout();
		float scale = layout.scale();
		boolean scaled = scale != 1.0F;
		// Position in scaled coordinate space so the anchored corner stays put.
		int screenW = scaled ? Math.round(graphics.guiWidth() / scale) : graphics.guiWidth();
		int screenH = scaled ? Math.round(graphics.guiHeight() / scale) : graphics.guiHeight();
		int x = primarySidecarX(screenW, HUD_W);
		int y = primarySidecarY(screenW, screenH, HUD_W);
		if (scaled) {
			graphics.pose().pushMatrix();
			graphics.pose().scale(scale, scale);
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, FRAME_TEXTURE, x, y,
			0.0F, 0.0F, HUD_W, HUD_H, HUD_W, HUD_H);
		drawFrameGlow(graphics, x, y);

		AttunedInv inv = AttunedAttachments.getInventory(player);
		AttunementReadout.Snapshot readout = AttunementReadout.cached(player);
		List<Integer> activeSlots = readout.activeSlots();
		Map<Integer, BudgetResolver.DormantReason> dormantReasons = readout.dormantReasons();
		FocusDefinition[] slotDefinitions = activeSlotDefinitions(player, inv, activeSlots);

		drawAbilityWell(graphics, player, inv, activeSlots, slotDefinitions,
			x + ABILITY_WELL_X, y + ABILITY_WELL_Y);
		drawApexBar(graphics, readout,
			x + APEX_GEM_X, y + APEX_GEM_Y, x + APEX_BAR_X, y + APEX_BAR_Y);
		drawFocusGrid(graphics, inv, activeSlots, dormantReasons, slotDefinitions,
			x + FOCUS_GRID_X, y + FOCUS_GRID_Y);
		drawConfluenceChip(graphics, readout.activeConfluences().size(),
			x + CONFLUENCE_CHIP_X, y + CONFLUENCE_CHIP_Y);

		if (scaled) {
			graphics.pose().popMatrix();
		}
	}

	// Defaults (bottom_right anchor, zero offset) resolve to the historical
	// layout: screenW - hudWidth - SCREEN_MARGIN, bottom-aligned.
	static int primarySidecarX(int screenW, int hudWidth) {
		return HudAnchor.x(screenW, hudWidth, HudAnchor.layout());
	}

	private static int primarySidecarY(int screenW, int screenH, int hudWidth) {
		return HudAnchor.y(screenH, HUD_H, HudAnchor.layout());
	}

	private static FocusDefinition[] activeSlotDefinitions(Player player, AttunedInv inv, List<Integer> activeSlots) {
		FocusDefinition[] definitions = new FocusDefinition[AttunedInv.SIZE];
		for (int slot : activeSlots) {
			definitions[slot] = Attunement.definitionFor(player, inv.get(slot)).orElse(null);
		}
		return definitions;
	}

	private static Optional<Affinity> affinityOf(FocusDefinition definition) {
		return definition == null ? Optional.empty() : definition.affinity();
	}

	private static void drawFrameGlow(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 4, y, x + HUD_W - 4, y + 1, FRAME_EDGE);
		graphics.fill(x + 4, y + HUD_H - 1, x + HUD_W - 4, y + HUD_H, FRAME_EDGE);
		graphics.fill(x, y + 4, x + 1, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + HUD_W - 1, y + 4, x + HUD_W, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + 4, y + 34, x + HUD_W - 4, y + 35, FRAME_GLOW);
	}

	private static void drawAbilityWell(GuiGraphicsExtractor graphics, Player player, AttunedInv inv,
			List<Integer> activeSlots, FocusDefinition[] slotDefinitions, int x, int y) {
		int slot = selectedAbilitySlot(player, inv, activeSlots, slotDefinitions);
		if (slot >= 0) {
			ItemStack stack = inv.get(slot);
			graphics.item(stack, x + (ABILITY_WELL_SIZE - 16) / 2, y + (ABILITY_WELL_SIZE - 16) / 2);
		}
		int remaining = FocusAbilityClientState.remainingTicks();
		int total = FocusAbilityClientState.totalTicks();
		if (remaining > 0 && total > 0) {
			drawCooldownRing(graphics, x, y, ABILITY_WELL_SIZE, remaining, total);
		}
	}

	private static int selectedAbilitySlot(Player player, AttunedInv inv, List<Integer> activeSlots,
			FocusDefinition[] slotDefinitions) {
		int syncedSlot = FocusAbilityClientState.slot();
		if (syncedSlot >= 0 && syncedSlot < AttunedInv.SIZE && !inv.get(syncedSlot).isEmpty()) {
			return syncedSlot;
		}
		for (int slot : activeSlots) {
			ItemStack stack = inv.get(slot);
			FocusDefinition definition = slotDefinitions[slot];
			FocusBehavior behavior = definition == null
				? null
				: definition.behavior().map(AttunedRegistries::getBehavior).orElse(null);
			if (behavior != null && hasActiveAbility(behavior, player, stack)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean hasActiveAbility(FocusBehavior behavior, Player player, ItemStack stack) {
		try {
			return behavior.hasActiveAbility();
		} catch (RuntimeException e) {
			Attuned.LOGGER.warn("Attuned Focus ability HUD availability failed for {} using {} ({})",
				player.getUUID(), stack.getItem(), behavior.getClass().getName(), e);
			return false;
		}
	}

	private static void drawFocusGrid(GuiGraphicsExtractor graphics, AttunedInv inv,
			List<Integer> activeSlots, Map<Integer, BudgetResolver.DormantReason> dormantReasons,
			FocusDefinition[] slotDefinitions, int x, int y) {
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
				drawActiveGlow(graphics, sx, sy, focusColor(slotDefinitions[slot]));
			} else if (dormantReasons.containsKey(slot)) {
				drawDormantOverlay(graphics, sx, sy);
			}
			graphics.item(stack, sx + FocusLayout.SLOT_INSET, sy + FocusLayout.SLOT_INSET);
		}
	}

	private static int focusColor(FocusDefinition definition) {
		return affinityOf(definition)
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

	/**
	 * Paints one pip per active Confluence (up to a small cap) in the HUD's top gap.
	 * Fill-only to match the HUD idiom (no font batch); draws nothing when none are active.
	 */
	private static void drawConfluenceChip(GuiGraphicsExtractor graphics, int count, int x, int y) {
		if (count <= 0) {
			return;
		}
		int pips = Math.min(count, CONFLUENCE_MAX_PIPS);
		for (int i = 0; i < pips; i++) {
			int px = x + (i % CONFLUENCE_CHIP_COLUMNS) * CONFLUENCE_PIP_STEP;
			int py = y + (i / CONFLUENCE_CHIP_COLUMNS) * CONFLUENCE_PIP_STEP;
			graphics.fill(px, py, px + CONFLUENCE_PIP, py + CONFLUENCE_PIP, CONFLUENCE_PIP_COLOR);
		}
	}

	private static void drawApexBar(GuiGraphicsExtractor graphics, AttunementReadout.Snapshot readout,
			int gemX, int gemY, int barX, int barY) {
		CombatHud.drawPlayerGem(graphics, gemX, gemY, APEX_GEM_SIZE,
			readout.committed().orElse(null), readout.discord(), readout.capstone().orElse(null), readout.atApex());

		graphics.fill(barX - 1, barY - 1, barX + APEX_BAR_W + 1, barY + APEX_BAR_H + 1, BAR_EMPTY_FILL);
		graphics.fill(barX, barY, barX + APEX_BAR_W, barY + APEX_BAR_H, BAR_TRACK);
		graphics.fill(barX + 1, barY + 1, barX + APEX_BAR_W - 1, barY + APEX_BAR_H - 1, BAR_EMPTY_FILL);
		float resonance = Math.max(0.0F, Math.min(1.0F, readout.resonance()));
		int fill = Math.round(APEX_BAR_W * resonance);
		if (fill > 0) {
			int color = readout.stanceArgb();
			graphics.fill(barX, barY, barX + fill, barY + APEX_BAR_H, 0xD0000000 | (color & 0x00FFFFFF));
		}
		// Clamp so a threshold at/near 1.0 stays on the bar track instead of
		// painting one pixel past it into the border.
		int thresholdX = barX + Math.min(APEX_BAR_W - 1, Math.round(APEX_BAR_W * Resonance.APEX_THRESHOLD));
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
