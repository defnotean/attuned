package dev.attuned.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
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
import dev.attuned.content.AttunedComponents;
import dev.attuned.client.AttunedClientConfig;
import dev.attuned.client.AttunementReadout;
import dev.attuned.network.FocusAbilityStatusPayload;
import dev.attuned.client.FocusAbilityClientState;
import dev.attuned.combat.Resonance;
import dev.attuned.menu.FocusLayout;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.PactTacticals;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import dev.attuned.client.compat.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Compact gameplay HUD for the player's equipped Foci and active ability state. */
public final class FociHud {
	private FociHud() {}

	private static final ResourceLocation FRAME_TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/gui/foci_hud.png");

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
	private static final int OVERCHARGE_RING = 0xFFE8A317;
	private static final int BAR_TRACK = 0xB0111118;
	private static final int BAR_EMPTY_FILL = 0x663A2E64;
	private static final int APEX_MARK = 0xD8F4D06A;
	private static final int FRAME_GLOW = 0xB46D4FD8;
	private static final int FRAME_EDGE = 0xD49FC8FF;
	private static final int APEX_ARMED_RING = 0xFFFFE97A;
	private static final int CHARGED_MELEE_DOT = 0xFFE8C840;
	private static final float CHARGED_MELEE_THRESHOLD = 0.9F;
	private static final float APEX_APPROACHING = 0.40F;
	private static final double APEX_PULSE_PERIOD_TICKS = 20.0D;
	// Confluence count chip: small pips in the top gap between the ability well and the Apex gem.
	private static final int CONFLUENCE_CHIP_X = 28;
	private static final int CONFLUENCE_CHIP_Y = 5;
	private static final int CONFLUENCE_PIP = 2;
	private static final int CONFLUENCE_PIP_STEP = 3;
	private static final int CONFLUENCE_CHIP_COLUMNS = 2;
	private static final int CONFLUENCE_MAX_PIPS = 6;
	private static final int CONFLUENCE_PIP_COLOR = 0xE0B9E8FF;
	private static final int TEMPERED_TICK = 0xFFFFD37A;
	private static final double CONFLUENCE_PULSE_PERIOD_TICKS = 24.0D;
	// Pact trial pip: thin progress bar under the Apex resonance track.
	private static final int TRIAL_PIP_W = 20;
	private static final int TRIAL_PIP_H = 3;
	private static final int TRIAL_PIP_TRACK = 0x90080810;
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		HudRenderCallback.EVENT.register(FociHud::renderLayer);
	}

	public static boolean isVisible(Player player) {
		return AttunedClientConfig.get().showFociHud();
	}

	private static void renderLayer(PoseStack poseStack, float tickDelta) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || !isVisible(player)) {
			return;
		}
		GuiGraphics graphics = new GuiGraphics(minecraft, poseStack);
		draw(graphics, player);
	}

	private static void draw(GuiGraphics graphics, Player player) {
		AttunedClientConfig.HudLayout layout = HudAnchor.layout();
		float scale = layout.scale();
		boolean scaled = scale != 1.0F;
		// Position in scaled coordinate space so the anchored corner stays put.
		int screenW = scaled ? Math.round(graphics.guiWidth() / scale) : graphics.guiWidth();
		int screenH = scaled ? Math.round(graphics.guiHeight() / scale) : graphics.guiHeight();
		int x = primarySidecarX(screenW, HUD_W);
		int y = primarySidecarY(screenW, screenH, HUD_W);
		if (scaled) {
			graphics.pose().pushPose();
			graphics.pose().scale(scale, scale, 1.0F);
		}

		graphics.blit(FRAME_TEXTURE, x, y,
			0.0F, 0.0F, HUD_W, HUD_H, HUD_W, HUD_H);
		drawFrameGlow(graphics, x, y);

		AttunedInv inv = AttunedAttachments.getInventory(player);
		AttunementReadout.Snapshot readout = AttunementReadout.cached(player);
		List<Integer> activeSlots = readout.activeSlots();
		Map<Integer, BudgetResolver.DormantReason> dormantReasons = readout.dormantReasons();
		FocusDefinition[] slotDefinitions = activeSlotDefinitions(player, inv, activeSlots);

		drawAbilityWell(graphics, player, inv, activeSlots, slotDefinitions, readout,
			x + ABILITY_WELL_X, y + ABILITY_WELL_Y);
		drawApexBar(graphics, player, readout,
			x + APEX_GEM_X, y + APEX_GEM_Y, x + APEX_BAR_X, y + APEX_BAR_Y);
		drawTrialPip(graphics, player, readout,
			x + APEX_BAR_X + (APEX_BAR_W - TRIAL_PIP_W) / 2, y + APEX_BAR_Y + APEX_BAR_H + 2);
		drawFocusGrid(graphics, inv, activeSlots, dormantReasons, slotDefinitions,
			x + FOCUS_GRID_X, y + FOCUS_GRID_Y);
		drawConfluenceChip(graphics, readout.activeConfluences().size(),
			x + CONFLUENCE_CHIP_X, y + CONFLUENCE_CHIP_Y, apexPulseGameTime());

		if (scaled) {
			graphics.pose().popPose();
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

	private static void drawFrameGlow(GuiGraphics graphics, int x, int y) {
		graphics.fill(x + 4, y, x + HUD_W - 4, y + 1, FRAME_EDGE);
		graphics.fill(x + 4, y + HUD_H - 1, x + HUD_W - 4, y + HUD_H, FRAME_EDGE);
		graphics.fill(x, y + 4, x + 1, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + HUD_W - 1, y + 4, x + HUD_W, y + HUD_H - 4, FRAME_GLOW);
		graphics.fill(x + 4, y + 34, x + HUD_W - 4, y + 35, FRAME_GLOW);
	}

	private static void drawAbilityWell(GuiGraphics graphics, Player player, AttunedInv inv,
			List<Integer> activeSlots, FocusDefinition[] slotDefinitions,
			AttunementReadout.Snapshot readout, int x, int y) {
		int stanceArgb = readout.stanceArgb();
		int syncedSlot = FocusAbilityClientState.slot();
		if (syncedSlot == FocusAbilityStatusPayload.PACT_TACTICAL_SLOT) {
			graphics.fill(x + 4, y + 4, x + ABILITY_WELL_SIZE - 4, y + ABILITY_WELL_SIZE - 4,
				0x90000000 | (stanceArgb & 0x00FFFFFF));
		} else {
			int slot = selectedAbilitySlot(player, inv, activeSlots, slotDefinitions);
			if (slot >= 0) {
				ItemStack stack = inv.get(slot);
				graphics.renderItem(stack, x + (ABILITY_WELL_SIZE - 16) / 2, y + (ABILITY_WELL_SIZE - 16) / 2);
			}
		}
		int remaining = FocusAbilityClientState.remainingTicks();
		int total = FocusAbilityClientState.totalTicks();
		if (remaining > 0 && total > 0) {
			drawCooldownRing(graphics, x, y, ABILITY_WELL_SIZE, remaining, total);
		} else if (syncedSlot == FocusAbilityStatusPayload.PACT_TACTICAL_SLOT
				&& player.isCrouching()
				&& readout.atApex()
				&& readout.resonance() >= PactTacticals.OVERCHARGE_SPEND) {
			drawOverchargeRing(graphics, x, y, ABILITY_WELL_SIZE);
		}
		if (player.getAttackStrengthScale(0.0F) >= CHARGED_MELEE_THRESHOLD) {
			int dotX = x + ABILITY_WELL_SIZE - 4;
			int dotY = y + 1;
			graphics.fill(dotX, dotY, dotX + 3, dotY + 3, CHARGED_MELEE_DOT);
		}
	}

	private static void drawOverchargeRing(GuiGraphics graphics, int x, int y, int size) {
		long gameTime = apexPulseGameTime();
		float pulse = (float) (Math.sin((gameTime / 8.0) * Math.PI * 2.0) * 0.5 + 0.5);
		int alpha = Math.round(0x90 + pulse * 0x60);
		int color = (alpha << 24) | (OVERCHARGE_RING & 0x00FFFFFF);
		int x1 = x + size;
		int y1 = y + size;
		graphics.fill(x, y, x1, y + 1, color);
		graphics.fill(x, y1 - 1, x1, y1, color);
		graphics.fill(x, y + 1, x + 1, y1 - 1, color);
		graphics.fill(x1 - 1, y + 1, x1, y1 - 1, color);
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

	private static void drawFocusGrid(GuiGraphics graphics, AttunedInv inv,
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
			graphics.renderItem(stack, sx + FocusLayout.SLOT_INSET, sy + FocusLayout.SLOT_INSET);
			if (AttunedComponents.isTempered(stack)) {
				drawTemperedTick(graphics, sx, sy);
			}
		}
	}

	private static void drawTemperedTick(GuiGraphics graphics, int x, int y) {
		int x1 = x + FocusLayout.SLOT - 1;
		int y0 = y;
		graphics.fill(x1 - 2, y0, x1, y0 + 1, TEMPERED_TICK);
		graphics.fill(x1 - 1, y0, x1, y0 + 3, TEMPERED_TICK);
	}

	private static int focusColor(FocusDefinition definition) {
		return affinityOf(definition)
			.map(Affinity::argb)
			.orElse(AffinityColors.NEUTRAL_ARGB);
	}

	private static void drawActiveGlow(GuiGraphics graphics, int x, int y, int color) {
		int argb = (ACTIVE_GLOW_ALPHA << 24) | (color & 0x00FFFFFF);
		int x1 = x + FocusLayout.SLOT;
		int y1 = y + FocusLayout.SLOT;
		graphics.fill(x, y, x1, y + 1, argb);
		graphics.fill(x, y1 - 1, x1, y1, argb);
		graphics.fill(x, y + 1, x + 1, y1 - 1, argb);
		graphics.fill(x1 - 1, y + 1, x1, y1 - 1, argb);
	}

	private static void drawDormantOverlay(GuiGraphics graphics, int x, int y) {
		graphics.fill(x + 1, y + 1, x + FocusLayout.SLOT - 1, y + FocusLayout.SLOT - 1, DORMANT_DIM);
	}

	/**
	 * Paints one pip per active Confluence (up to a small cap) in the HUD's top gap.
	 * Fill-only to match the HUD idiom (no font batch); draws nothing when none are active.
	 */
	private static void drawConfluenceChip(GuiGraphics graphics, int count, int x, int y, long gameTime) {
		if (count <= 0) {
			return;
		}
		float pulse = (float) (Math.sin((gameTime / CONFLUENCE_PULSE_PERIOD_TICKS) * Math.PI * 2.0) * 0.5 + 0.5);
		int alpha = Math.round(0x90 + pulse * 0x50);
		int pipColor = (alpha << 24) | (CONFLUENCE_PIP_COLOR & 0x00FFFFFF);
		int pips = Math.min(count, CONFLUENCE_MAX_PIPS);
		for (int i = 0; i < pips; i++) {
			int px = x + (i % CONFLUENCE_CHIP_COLUMNS) * CONFLUENCE_PIP_STEP;
			int py = y + (i / CONFLUENCE_CHIP_COLUMNS) * CONFLUENCE_PIP_STEP;
			graphics.fill(px, py, px + CONFLUENCE_PIP, py + CONFLUENCE_PIP, pipColor);
		}
	}

	/**
	 * Thin pact-trial progress pip under the Apex bar. Skips when no pact is awake
	 * or the active pact's Tier 4 trial is already complete.
	 */
	private static void drawTrialPip(GuiGraphics graphics, Player player,
			AttunementReadout.Snapshot readout, int x, int y) {
		Optional<Pact> pact = readout.pact();
		if (pact.isEmpty()) {
			return;
		}
		AttunedAttachments.PactTrialState state = AttunedAttachments.getPactTrialProgress(player).get(pact.get());
		if (state == null || state.tier4Complete()) {
			return;
		}
		int goal = state.goal();
		if (goal <= 0) {
			return;
		}
		float fraction = Math.max(0.0F, Math.min(1.0F, state.progress() / (float) goal));
		graphics.fill(x, y, x + TRIAL_PIP_W, y + TRIAL_PIP_H, TRIAL_PIP_TRACK);
		int fill = Math.round(TRIAL_PIP_W * fraction);
		if (fill > 0) {
			int color = pact.get().argb();
			graphics.fill(x, y, x + fill, y + TRIAL_PIP_H, 0xD0000000 | (color & 0x00FFFFFF));
		}
	}

	private static void drawApexBar(GuiGraphics graphics, Player player,
			AttunementReadout.Snapshot readout, int gemX, int gemY, int barX, int barY) {
		CombatHud.drawPlayerGem(graphics, gemX, gemY, APEX_GEM_SIZE,
			readout.committed().orElse(null), readout.discord(), readout.capstone().orElse(null), readout.atApex());

		graphics.fill(barX - 1, barY - 1, barX + APEX_BAR_W + 1, barY + APEX_BAR_H + 1, BAR_EMPTY_FILL);
		graphics.fill(barX, barY, barX + APEX_BAR_W, barY + APEX_BAR_H, BAR_TRACK);
		graphics.fill(barX + 1, barY + 1, barX + APEX_BAR_W - 1, barY + APEX_BAR_H - 1, BAR_EMPTY_FILL);
		float resonance = AttunementReadout.displayResonance(player);
		int fill = Math.round(APEX_BAR_W * resonance);
		if (fill > 0) {
			int color = readout.stanceArgb();
			int alpha = 0xD0;
			if (resonance >= APEX_APPROACHING && resonance < Resonance.APEX_THRESHOLD) {
				float tension = (resonance - APEX_APPROACHING)
					/ (Resonance.APEX_THRESHOLD - APEX_APPROACHING);
				float pulse = (float) (Math.sin((apexPulseGameTime() / APEX_PULSE_PERIOD_TICKS) * Math.PI * 2.0) * 0.5 + 0.5);
				alpha = Math.round(0xA0 + tension * 0x50 + pulse * 0x20);
			}
			graphics.fill(barX, barY, barX + fill, barY + APEX_BAR_H, (alpha << 24) | (color & 0x00FFFFFF));
		}
		// Clamp so a threshold at/near 1.0 stays on the bar track instead of
		// painting one pixel past it into the border.
		int thresholdX = barX + Math.min(APEX_BAR_W - 1, Math.round(APEX_BAR_W * Resonance.APEX_THRESHOLD));
		long gameTime = apexPulseGameTime();
		boolean apexPulse = resonance >= Resonance.APEX_THRESHOLD;
		int markColor = apexPulse ? pulseArgb(APEX_MARK, gameTime) : APEX_MARK;
		graphics.fill(thresholdX, barY - 1, thresholdX + 1, barY + APEX_BAR_H + 1, markColor);
		if (apexPulse) {
			int glowColor = pulseArgb(FRAME_GLOW, gameTime);
			graphics.fill(gemX - 1, gemY - 1, gemX + APEX_GEM_SIZE + 1, gemY, glowColor);
			graphics.fill(gemX - 1, gemY + APEX_GEM_SIZE, gemX + APEX_GEM_SIZE + 1, gemY + APEX_GEM_SIZE + 1, glowColor);
			graphics.fill(gemX - 1, gemY, gemX, gemY + APEX_GEM_SIZE, glowColor);
			graphics.fill(gemX + APEX_GEM_SIZE, gemY, gemX + APEX_GEM_SIZE + 1, gemY + APEX_GEM_SIZE, glowColor);
		}
		if (readout.atApex()) {
			drawApexArmedRing(graphics, gemX, gemY, APEX_GEM_SIZE, APEX_ARMED_RING);
		}
	}

	private static long apexPulseGameTime() {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		return player == null ? 0L : player.getLevel().getGameTime();
	}

	private static int pulseArgb(int rgb, long gameTime) {
		float pulse = (float) (Math.sin((gameTime / APEX_PULSE_PERIOD_TICKS) * Math.PI * 2.0) * 0.5 + 0.5);
		int alpha = Math.round(0x38 + pulse * 0xC8);
		return (alpha << 24) | (rgb & 0x00FFFFFF);
	}

	private static void drawApexArmedRing(GuiGraphics graphics, int gemX, int gemY, int size, int color) {
		int x1 = gemX + size;
		int y1 = gemY + size;
		graphics.fill(gemX, gemY, x1, gemY + 1, color);
		graphics.fill(gemX, y1 - 1, x1, y1, color);
		graphics.fill(gemX, gemY + 1, gemX + 1, y1 - 1, color);
		graphics.fill(x1 - 1, gemY + 1, x1, y1 - 1, color);
	}

	private static void drawCooldownRing(GuiGraphics graphics, int x, int y, int size,
			int remainingTicks, int totalTicks) {
		graphics.fill(x + 3, y + 3, x + size - 3, y + size - 3, COOLDOWN_SHADE);
		float progress = Math.max(0.0F, Math.min(1.0F, remainingTicks / (float) totalTicks));
		int perimeter = (size - 1) * 4;
		int lit = Math.max(1, Math.round(perimeter * progress));
		drawCooldownEdge(graphics, x, y, size, lit);
	}

	private static void drawCooldownEdge(GuiGraphics graphics, int x, int y, int size, int lit) {
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
