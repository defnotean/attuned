package dev.attuned.client;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Resonance;
import dev.attuned.combat.Apex;
import dev.attuned.client.hud.CombatHud;
import dev.attuned.menu.FocusLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the Focus side panel: a custom-textured inventory extension with six
 * recessed slot wells, priority ticks, a committed-affinity gem above them, and
 * an attunement-budget bar below. Shared by the survival and creative inventory
 * screens so both render an identical panel — only the column's position differs.
 */
public final class FocusPanel {
	private FocusPanel() {}

	private static final Identifier PANEL_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/focus_panel.png");

	// Padding between the slot column and the panel edge, in GUI pixels.
	private static final int PAD_X = 5;
	private static final int PAD_Y = 8;
	private static final int PANEL_WIDTH = 28;
	private static final int PANEL_HEIGHT = 124;

	// Affinity gem (top padding band) and budget bar (bottom padding band).
	private static final int GEM_SIZE = 8;
	private static final int BAR_HEIGHT = 4;

	// ARGB palette used by dynamic overlays drawn above the custom panel art.
	private static final int WELL_SHADOW = 0xFF373737;
	private static final int DORMANT_DIM = 0x55000000;
	private static final int PRIORITY_TICK_DIM = 0x80373737;
	private static final int SINGLE_AFFINITY_PACT_THRESHOLD = 3;

	// Idle-glow pulse: a sine modulation across PULSE_PERIOD_TICKS whose alpha
	// breathes between two narrow bounds so the highlight reads as ambient rather
	// than animated. Alpha lives in [GLOW_ALPHA_MIN, GLOW_ALPHA_MAX] / 255. Driven
	// by game ticks (not wall time) so the pulse freezes alongside the world when
	// the player pauses single-player. 40 ticks ≈ 2000 ms at 20 TPS, preserving
	// the original visual cadence.
	private static final long PULSE_PERIOD_TICKS = 40L;
	private static final int GLOW_ALPHA_MIN = 60;
	private static final int GLOW_ALPHA_MAX = 120;
	private static final int PACT_STABILITY_ALPHA_BONUS = 70;
	private static final int PACT_STABILITY_ALPHA_MAX = 185;
	private static final int PACT_STABILITY_MARK_LEN = 4;

	// Resonance ring: a 1-pixel border traced just outside the affinity gem.
	// Below Apex the ring sits at a dim base alpha; once at Apex it brightens to
	// full opacity to telegraph the capstone gating.
	private static final int RESONANCE_ALPHA_DIM = 80;
	private static final int RESONANCE_ALPHA_APEX = 255;
	private static final int RESONANCE_TRACK_ARGB = 0x40000000;

	/**
	 * Draws the panel — six slot wells, the affinity gem and the budget bar — for
	 * {@code player}, with the slot column's top-left corner at ({@code slotX},
	 * {@code slotY}) relative to the screen's top-left ({@code leftPos},
	 * {@code topPos}).
	 *
	 * <p>All player-state reads happen once at the top of this method and are
	 * threaded through helpers as locals, so the per-frame panel draw never walks
	 * a player's Focus slots more than once or repeats a registry lookup.</p>
	 */
	public static void draw(GuiGraphicsExtractor graphics, int leftPos, int topPos,
			int slotX, int slotY, Player player) {
		int x0 = leftPos + slotX - PAD_X;
		int x1 = leftPos + slotX + FocusLayout.SLOT + PAD_X;
		int y0 = topPos + slotY - PAD_Y;
		int y1 = topPos + slotY + AttunedInv.SIZE * FocusLayout.SLOT + PAD_Y;

		graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL_TEXTURE, x0, y0,
			0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

		// Cache the breathing-pulse alpha once per draw — every active slot shares
		// the same phase so the wells pulse as a chord rather than out of sync. The
		// phase is driven by the client level's game tick so it freezes whenever the
		// world does (single-player pause); if the client has no level, drop to
		// the minimum alpha so the highlight reads as a steady dim outline rather
		// than falling back to wall-clock time.
		var level = Minecraft.getInstance().level;
		int pulseAlpha = level == null ? GLOW_ALPHA_MIN : pulseAlpha(level.getGameTime());

		// Resolve every player-state read once. The active-slot list and its
		// matching FocusDefinition lookups are the expensive piece: each slot
		// otherwise costs a registry lookupOrThrow. After this block, no further
		// Attunement calls are needed for the entire draw — used, capacity, the
		// stance colour and the per-slot glow colours are all derived locally.
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<Integer> activeSlotsList = Attunement.activeSlots(player);
		FocusDefinition[] slotDefinitions = new FocusDefinition[AttunedInv.SIZE];
		List<Optional<Affinity>> orderedActiveAffinities = new ArrayList<>(activeSlotsList.size());
		int used = 0;
		Set<Affinity> distinctActiveAffinities = EnumSet.noneOf(Affinity.class);
		EnumMap<Affinity, Integer> activeAffinityCounts = new EnumMap<>(Affinity.class);
		for (int slot : activeSlotsList) {
			FocusDefinition def = Attunement.definitionFor(player, inv.get(slot)).orElse(null);
			if (def == null) {
				orderedActiveAffinities.add(Optional.empty());
				continue;
			}
			slotDefinitions[slot] = def;
			used += def.cost();
			Optional<Affinity> affinity = def.affinity();
			orderedActiveAffinities.add(affinity);
			affinity.ifPresent(activeAffinity -> {
				distinctActiveAffinities.add(activeAffinity);
				activeAffinityCounts.merge(activeAffinity, 1, Integer::sum);
			});
		}
		int capacity = Attunement.capacity(player);
		boolean discord = distinctActiveAffinities.size() >= 2;
		Optional<Affinity> committed = distinctActiveAffinities.size() == 1
			? Optional.of(distinctActiveAffinities.iterator().next())
			: Optional.empty();
		Optional<Apex.Capstone> capstone =
			Apex.resolveCapstone(orderedActiveAffinities, used, capacity);
		float resonance = Resonance.get(player);
		boolean atApex = capstone.isPresent() && resonance >= Resonance.APEX_THRESHOLD;
		int affinityColor = AttunementReadout.stanceArgb(capstone, discord, committed);
		Optional<Affinity> pactStabilityAffinity = pactStabilityAffinity(discord, activeAffinityCounts);

		for (int i = 0; i < AttunedInv.SIZE; i++) {
			int sx = leftPos + slotX;
			int sy = topPos + slotY + i * FocusLayout.SLOT;
			boolean active = slotDefinitions[i] != null;
			drawPriorityTick(graphics, sx - 3, sy, i, active ? affinityColor : PRIORITY_TICK_DIM);
			// Dim only an equipped-but-dormant Focus (over budget) — empty wells stay clean.
			if (!inv.get(i).isEmpty() && !active) {
				graphics.fill(sx + 1, sy + 1, sx + FocusLayout.SLOT - 1, sy + FocusLayout.SLOT - 1, DORMANT_DIM);
			}
			// Idle glow: a soft pulsing 1-pixel border tinted by the active Focus's
			// affinity. Drawn after the bevel and dormant dim so it crowns active
			// wells only and never competes with the dormant signal.
			if (active) {
				int baseColor = focusAffinityColor(slotDefinitions[i]);
				int glowArgb = (pulseAlpha << 24) | (baseColor & 0x00FFFFFF);
				int gx0 = sx + 1;
				int gy0 = sy + 1;
				int gx1 = sx + FocusLayout.SLOT - 1;
				int gy1 = sy + FocusLayout.SLOT - 1;
				// Top and bottom edges of the inner border.
				graphics.fill(gx0, gy0, gx1, gy0 + 1, glowArgb);
				graphics.fill(gx0, gy1 - 1, gx1, gy1, glowArgb);
				// Left and right edges, inset to avoid double-blending the corners.
				graphics.fill(gx0, gy0 + 1, gx0 + 1, gy1 - 1, glowArgb);
				graphics.fill(gx1 - 1, gy0 + 1, gx1, gy1 - 1, glowArgb);
				if (hasPactStability(slotDefinitions[i], pactStabilityAffinity)) {
					drawPactStabilityCue(graphics, sx, sy, baseColor, pulseAlpha);
				}
			}
		}

		// Affinity gem, centred in the panel's top padding band: a 3D cut gemstone
		// with a custom status glyph inside it.
		int gemX0 = leftPos + slotX + FocusLayout.SLOT / 2 - GEM_SIZE / 2;
		CombatHud.drawPlayerGem(graphics, gemX0, y0, GEM_SIZE,
			committed.orElse(null), discord, capstone.orElse(null),
			atApex);

		// Resonance ring: a 1-pixel square border traced one pixel outside the
		// gem's bezel. The full ring is laid down as a faint track, then overlaid
		// with a coloured arc whose length scales with the player's resonance.
		// At Apex the overlay is the full affinity colour; below threshold it sits
		// dim so the build's gating state reads at a glance.
		drawResonanceRing(graphics, gemX0, y0, affinityColor, resonance, atApex);

		// Budget bar, centred in the panel's bottom padding band: a dark track
		// with a fill proportional to the attunement points in use.
		int barX0 = leftPos + slotX;
		int barX1 = leftPos + slotX + FocusLayout.SLOT;
		int barY0 = y1 - PAD_Y + (PAD_Y - BAR_HEIGHT) / 2;
		graphics.fill(barX0, barY0, barX1, barY0 + BAR_HEIGHT, WELL_SHADOW);
		int fill = AttunementReadout.budgetFillWidth(barX1 - barX0, used, capacity);
		if (fill > 0) {
			graphics.fill(barX0, barY0, barX0 + fill, barY0 + BAR_HEIGHT, affinityColor);
		}
	}

	private static void drawPriorityTick(GuiGraphicsExtractor graphics, int x, int y, int slot, int colorArgb) {
		int ticks = slot + 1;
		for (int i = 0; i < Math.min(ticks, 3); i++) {
			int ty = y + 4 + i * 3;
			graphics.fill(x, ty, x + 1, ty + 2, colorArgb);
		}
		if (ticks > 3) {
			graphics.fill(x + 1, y + 4, x + 2, y + 4 + (ticks - 3) * 3 - 1, colorArgb);
		}
	}

	/**
	 * True when the point ({@code relX}, {@code relY}) — measured from the
	 * screen's top-left — lies within the Focus panel whose slot column starts at
	 * ({@code slotX}, {@code slotY}). Used so the inventory screens treat clicks
	 * on the panel as inside the GUI, even though it sits past the window edge.
	 */
	public static boolean withinPanel(int slotX, int slotY, double relX, double relY) {
		return relX >= slotX - PAD_X
			&& relX < slotX + FocusLayout.SLOT + PAD_X
			&& relY >= slotY - PAD_Y
			&& relY < slotY + AttunedInv.SIZE * FocusLayout.SLOT + PAD_Y;
	}

	/**
	 * True when the point lies on the panel but clear of the six slot wells — the
	 * surrounding frame, including the affinity gem and budget bar. This is the
	 * hover zone for the attunement readout tooltip, kept distinct from the slots
	 * so it never competes with their own item tooltips.
	 */
	public static boolean overReadout(int slotX, int slotY, double relX, double relY) {
		if (!withinPanel(slotX, slotY, relX, relY)) {
			return false;
		}
		boolean overWells = relX >= slotX && relX < slotX + FocusLayout.SLOT
			&& relY >= slotY && relY < slotY + AttunedInv.SIZE * FocusLayout.SLOT;
		return !overWells;
	}

	/**
	 * The current pulse alpha for the idle Focus glow at game-tick
	 * {@code nowTicks} — a sine-wave modulation across {@link #PULSE_PERIOD_TICKS}
	 * mapped onto {@code [GLOW_ALPHA_MIN, GLOW_ALPHA_MAX]}. Computed once per draw
	 * so every slot shares phase. Driven by ticks rather than wall time so the
	 * pulse pauses with the world.
	 */
	private static int pulseAlpha(long nowTicks) {
		float phase = (float) ((nowTicks % PULSE_PERIOD_TICKS) / (double) PULSE_PERIOD_TICKS);
		float pulse = 0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2.0);
		return GLOW_ALPHA_MIN + Math.round((GLOW_ALPHA_MAX - GLOW_ALPHA_MIN) * pulse);
	}

	/**
	 * The ARGB colour used to tint an active Focus's idle glow. Mirrors the
	 * affinity palette used elsewhere on the panel; an active but affinity-neutral
	 * Focus glows in a soft grey rather than a stance colour. Takes a pre-resolved
	 * {@link FocusDefinition} so the per-frame draw loop avoids a fresh registry
	 * lookup for every active slot.
	 */
	private static int focusAffinityColor(FocusDefinition definition) {
		return definition.affinity().map(Affinity::argb).orElse(AffinityColors.NEUTRAL_ARGB);
	}

	/**
	 * The affinity whose active Focus wells should receive the Pact stability cue.
	 * This mirrors the single-affinity Pact wake condition while reusing the slot
	 * scan already done by draw(): at least three active affinity-bearing Foci,
	 * all from one lane. Discord and Untethered deliberately stay quiet.
	 */
	private static Optional<Affinity> pactStabilityAffinity(boolean discord,
			EnumMap<Affinity, Integer> activeAffinityCounts) {
		if (discord || activeAffinityCounts.size() != 1) {
			return Optional.empty();
		}
		var only = activeAffinityCounts.entrySet().iterator().next();
		return only.getValue() >= SINGLE_AFFINITY_PACT_THRESHOLD
			? Optional.of(only.getKey())
			: Optional.empty();
	}

	private static boolean hasPactStability(FocusDefinition definition, Optional<Affinity> pactAffinity) {
		return pactAffinity.isPresent()
			&& definition.affinity().filter(affinity -> affinity == pactAffinity.get()).isPresent();
	}

	/**
	 * A tiny, textless stability mark for active Foci that are sustaining a
	 * single-affinity Pact: four short corner brackets inside the well. It shares
	 * the existing pulse phase but caps alpha tightly so it reads as clarity, not
	 * a second full-slot effect.
	 */
	private static void drawPactStabilityCue(GuiGraphicsExtractor graphics, int sx, int sy,
			int baseColor, int pulseAlpha) {
		int alpha = Math.min(PACT_STABILITY_ALPHA_MAX, pulseAlpha + PACT_STABILITY_ALPHA_BONUS);
		int argb = (alpha << 24) | (baseColor & 0x00FFFFFF);
		int x0 = sx + 2;
		int y0 = sy + 2;
		int x1 = sx + FocusLayout.SLOT - 2;
		int y1 = sy + FocusLayout.SLOT - 2;
		int len = PACT_STABILITY_MARK_LEN;

		graphics.fill(x0, y0, x0 + len, y0 + 1, argb);
		graphics.fill(x0, y0, x0 + 1, y0 + len, argb);
		graphics.fill(x1 - len, y0, x1, y0 + 1, argb);
		graphics.fill(x1 - 1, y0, x1, y0 + len, argb);
		graphics.fill(x0, y1 - 1, x0 + len, y1, argb);
		graphics.fill(x0, y1 - len, x0 + 1, y1, argb);
		graphics.fill(x1 - len, y1 - 1, x1, y1, argb);
		graphics.fill(x1 - 1, y1 - len, x1, y1, argb);
	}

	/**
	 * Draws the resonance ring as a 1-pixel square border one pixel outside the
	 * affinity gem. The full ring is laid down as a faint dark track and then
	 * overlaid clockwise from the top-left with a coloured fill whose length
	 * tracks {@link Resonance#get(Player)}. Apex thickens the overlay to the
	 * stance colour at full opacity so the gating reads instantly.
	 *
	 * <p>Each of the four edges owns exactly one corner pixel and runs for
	 * {@code sideLen - 1} pixels, so the perimeter walks unique pixels without
	 * overdraw and without skipping a corner. With a {@link #GEM_SIZE} of 8 the
	 * ring side length is 10 and the perimeter is 36 pixels: {@code top} writes
	 * (rx0, ry0) -> (rx1-2, ry0), {@code right} writes (rx1-1, ry0) ->
	 * (rx1-1, ry1-2), {@code bottom} writes (rx1-1, ry1-1) -> (rx0+1, ry1-1),
	 * {@code left} writes (rx0, ry1-1) -> (rx0, ry0+1).</p>
	 */
	private static void drawResonanceRing(GuiGraphicsExtractor graphics, int gemX0, int gemY0,
			int affinityColor, float resonance, boolean atApex) {
		int rx0 = gemX0 - 1;
		int ry0 = gemY0 - 1;
		int rx1 = gemX0 + GEM_SIZE + 1;
		int ry1 = gemY0 + GEM_SIZE + 1;
		// Faint track: the unfilled portion of the ring still reads as a complete
		// outline so an empty gauge does not look broken. The track edges overlap
		// at corners with the same colour, so the painted pixel set is identical
		// to the overlay's; only the overlay needs corner-ownership accounting.
		graphics.fill(rx0, ry0, rx1, ry0 + 1, RESONANCE_TRACK_ARGB);
		graphics.fill(rx0, ry1 - 1, rx1, ry1, RESONANCE_TRACK_ARGB);
		graphics.fill(rx0, ry0 + 1, rx0 + 1, ry1 - 1, RESONANCE_TRACK_ARGB);
		graphics.fill(rx1 - 1, ry0 + 1, rx1, ry1 - 1, RESONANCE_TRACK_ARGB);

		float clampedResonance = Math.max(0.0F, Math.min(1.0F, resonance));
		if (clampedResonance <= 0.0F) {
			return;
		}

		int alpha = atApex ? RESONANCE_ALPHA_APEX : RESONANCE_ALPHA_DIM;
		int fillArgb = (alpha << 24) | (affinityColor & 0x00FFFFFF);

		// Trace the border clockwise from the top-left corner. Each edge owns its
		// starting corner only, so an edge writes at most sideLen - 1 unique pixels
		// and the four edges together cover exactly 4 * (sideLen - 1) pixels — the
		// full perimeter, with no pixel overdrawn and none past the ring's bounds.
		int sideLen = GEM_SIZE + 2;
		int edgeMax = sideLen - 1;
		int total = 4 * edgeMax;
		int filled = Math.max(1, Math.round(total * clampedResonance));

		// Top edge, left-to-right, owning the top-left corner (rx0, ry0).
		int onEdge = Math.min(filled, edgeMax);
		if (onEdge > 0) {
			graphics.fill(rx0, ry0, rx0 + onEdge, ry0 + 1, fillArgb);
		}
		int remaining = filled - edgeMax;
		// Right edge, top-to-bottom, owning the top-right corner (rx1-1, ry0).
		if (remaining > 0) {
			int len = Math.min(remaining, edgeMax);
			graphics.fill(rx1 - 1, ry0, rx1, ry0 + len, fillArgb);
			remaining -= edgeMax;
		}
		// Bottom edge, right-to-left, owning the bottom-right corner (rx1-1, ry1-1).
		if (remaining > 0) {
			int len = Math.min(remaining, edgeMax);
			graphics.fill(rx1 - len, ry1 - 1, rx1, ry1, fillArgb);
			remaining -= edgeMax;
		}
		// Left edge, bottom-to-top, owning the bottom-left corner (rx0, ry1-1).
		if (remaining > 0) {
			int len = Math.min(remaining, edgeMax);
			graphics.fill(rx0, ry1 - len, rx0 + 1, ry1, fillArgb);
		}
	}
}
