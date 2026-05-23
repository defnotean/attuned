package dev.attuned.client.hud;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.MobAffinities;
import dev.attuned.combat.Resonance;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The combat heads-up overlay: a compact panel anchored above the hotbar that
 * shows the player's stance gem, a resonance arc, the targeted mob's affinity
 * (when the crosshair sits on one within {@link #TARGET_RANGE_BLOCKS} blocks),
 * and a matchup tint on the player's gem so the rock-paper-scissors state reads
 * without opening the inventory.
 *
 * <p>The panel only renders when the player has something worth showing — a
 * committed affinity, the Discord stance, or a non-zero Resonance gauge — so it
 * stays out of the way for unattuned players.</p>
 */
public final class CombatHud {
	private CombatHud() {}

	// Crosshair-target reach: only mobs within this many blocks of the player
	// contribute their gem to the readout.
	private static final double TARGET_RANGE_BLOCKS = 12.0;

	// Gem and bar dimensions, in GUI pixels.
	private static final int PLAYER_GEM_SIZE = 10;
	private static final int TARGET_GEM_SIZE = 8;
	private static final int RESONANCE_BAR_W = 24;
	private static final int RESONANCE_BAR_H = 2;

	// Gap between the player gem and the target gem.
	private static final int GEM_GAP = 4;
	// Vertical gap between the resonance bar and the gem row.
	private static final int BAR_GAP = 2;
	// Distance from the bottom edge of the screen up to the panel — sits clear
	// of the hotbar (22 px) and its surrounding margin without crowding it.
	private static final int BOTTOM_OFFSET = 48;

	// Empowered-state pulse cadence — one full cycle every PULSE_PERIOD_TICKS so
	// the gold halo breathes rather than flickers. Game ticks (not wall time) so
	// the pulse freezes alongside the world when the player pauses single-player.
	// 24 ticks ≈ 1200 ms at 20 TPS, preserving the original visual cadence.
	private static final long PULSE_PERIOD_TICKS = 24L;

	// Gem bezel and tile colours, match the Focus panel's slot well palette.
	private static final int BEZEL_ARGB = 0xFF373737;
	// The white glyph stroked over the gem face, and the bright ring drawn when
	// the gem represents the crosshair-targeted mob.
	private static final int GLYPH_ARGB = 0xFFFFFFFF;
	private static final int TARGETED_RING_ARGB = 0xFFFFFFFF;
	// Neutral-state pip: a single white pixel centred on an otherwise empty gem
	// face, so an unattuned-but-resonance-armed player still sees something.
	private static final int NEUTRAL_PIP_ARGB = 0xFFFFFFFF;
	// Resonance track and bar fill alpha gating.
	private static final int RESONANCE_TRACK_ARGB = 0x80000000;
	private static final int RESONANCE_BELOW_APEX_ALPHA = 140;
	private static final int RESONANCE_AT_APEX_ALPHA = 255;

	// Glyph bitmasks: 8 rows of 8 bits each, MSB of byte = column 0, row 0 lives
	// in the top byte. Decoded by drawGlyph below. Tweak a glyph by editing the
	// hex literal — the draw loop is identity for any 8x8 sprite.
	private static final long FURY_GLYPH    = 0x1038549244380000L;
	private static final long BASTION_GLYPH = 0xFF81999942241800L;
	private static final long ZEPHYR_GLYPH  = 0x0102060C183060C0L;
	private static final long DISCORD_GLYPH = 0x8142241818244281L;

	// Empowered halo: a gold ring traced one pixel outside the player gem whose
	// alpha breathes between the two bounds.
	private static final int EMPOWERED_HALO_ARGB = 0xFFFFD555;
	private static final int HALO_ALPHA_MIN = 100;
	private static final int HALO_ALPHA_MAX = 220;
	// Neutralized tint: a translucent red overlaid on the player gem.
	private static final int NEUTRALIZED_TINT_ARGB = 0x80AA1111;

	// The matchup of the player's affinity against the targeted mob's.
	private enum Matchup { EMPOWERED, NEUTRAL, NEUTRALIZED, NONE }

	/**
	 * Registers the combat HUD layer so it draws between the hotbar and the chat
	 * line, sitting just above the hotbar without competing with any vanilla
	 * status bars. Called once from the client mod initializer.
	 */
	public static void init() {
		Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "combat_hud");
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id, CombatHud::renderLayer);
	}

	// The HudElement render entry point. Pulled out as a method reference so the
	// registration call stays single-line and free of inline lambda bodies.
	private static void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null) {
			return;
		}
		draw(graphics, minecraft, player);
	}

	// The actual draw routine — gates on visibility, then lays down the bar,
	// player gem, halo or dim, and target gem in screen-bottom-anchored order.
	// All player-state reads happen once at the top of this method so the per-frame
	// HUD draw never re-walks the Focus slots or repeats a registry lookup.
	private static void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, Player player) {
		// Cheapest gate first — a plain float read off the player attachment.
		float resonance = Resonance.get(player);
		Optional<Affinity> committed = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		// Skip the panel entirely for unattuned players with no resonance — there
		// is nothing combat-relevant to telegraph.
		if (committed.isEmpty() && !discord && resonance <= 0.0F) {
			return;
		}
		boolean apexArmed = Resonance.atApex(player);

		// The affinity actually painted on the player gem — committed first, then
		// the Apex fallback for legacy state, so the glyph routing matches the
		// bezel colour rather than diverging from it.
		Optional<Affinity> playerAffinity = committed.isPresent() ? committed : Apex.affinityOf(player);
		int playerColor = playerArgb(playerAffinity, discord);
		LivingEntity target = targetedLiving(minecraft, player);
		Optional<Affinity> targetAffinity = target == null ? Optional.empty() : MobAffinities.of(target);
		Matchup matchup = matchup(committed, targetAffinity);

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		boolean hasTarget = target != null && targetAffinity.isPresent();

		// Lay the panel out centred horizontally above the hotbar. With a target,
		// the gem row's full width includes both gems and the gap between them.
		int rowWidth = PLAYER_GEM_SIZE + (hasTarget ? GEM_GAP + TARGET_GEM_SIZE : 0);
		int rowX = screenW / 2 - rowWidth / 2;
		int rowY = screenH - BOTTOM_OFFSET;

		// Resonance bar first, just above the gem row, so the gem itself sits on
		// top in the visual stack and never gets clipped by the bar.
		drawResonanceBar(graphics, screenW, rowY - BAR_GAP - RESONANCE_BAR_H, playerColor, resonance, apexArmed);

		// Player gem with a dark bezel — same construction as the Focus panel gem.
		// The player's own gem is never "targeted" — that flag is for the mob gem.
		drawGem(graphics, rowX, rowY, PLAYER_GEM_SIZE, playerAffinity.orElse(null), discord, false);

		// Matchup state markers — gold pulse halo for empowered, red dim overlay
		// for neutralized. Neutral and "no target" cases leave the gem plain.
		if (matchup == Matchup.EMPOWERED) {
			drawEmpoweredHalo(graphics, rowX, rowY, PLAYER_GEM_SIZE);
		} else if (matchup == Matchup.NEUTRALIZED) {
			drawNeutralizedTint(graphics, rowX, rowY, PLAYER_GEM_SIZE);
		}

		// Target gem to the right of the player gem, vertically centred against it.
		if (hasTarget) {
			int targetX = rowX + PLAYER_GEM_SIZE + GEM_GAP;
			int targetY = rowY + (PLAYER_GEM_SIZE - TARGET_GEM_SIZE) / 2;
			drawGem(graphics, targetX, targetY, TARGET_GEM_SIZE, targetAffinity.get(), false, true);
		}
	}

	/**
	 * Paints one affinity gem at {@code (x, y)}: a 1-pixel dark bezel, a
	 * coloured face for the stance, and a white iconographic glyph (flame /
	 * shield / wing / woven cross) on top so the affinity reads at a glance
	 * without depending on colour alone. The bezel colour is sourced from
	 * {@link AffinityColors#argbOf(Optional, boolean)} so this readout stays
	 * locked to the same palette every other surface uses.
	 *
	 * <p>An empty {@code affinity} with {@code discord = false} draws a
	 * neutral grey bezel with a single white centre pip — the unattuned-but-
	 * resonance-armed state. When {@code targeted} is true, a 1-pixel white
	 * highlight ring is traced around the bezel to mark the crosshair-picked
	 * mob.</p>
	 *
	 * <p>Glyph rendering assumes {@code size == 8}. For larger gems the glyph
	 * is drawn centred at native 8x8 rather than upscaled — pixel-art icons
	 * read better unscaled than at integer multiples, and the player gem at
	 * size 10 leaves a comfortable 1-pixel border around the 8x8 sprite.</p>
	 *
	 * @param graphics the HUD draw context
	 * @param x        top-left X of the gem in GUI pixels
	 * @param y        top-left Y of the gem in GUI pixels
	 * @param size     edge length of the gem; bezel + face fit inside this square
	 * @param affinity the affinity to render, or {@code null} for the neutral state
	 * @param discord  whether the gem represents the Discord stance (overrides {@code affinity})
	 * @param targeted whether to add the bright outline that marks the crosshair-picked mob
	 */
	private static void drawGem(GuiGraphicsExtractor graphics, int x, int y, int size,
			@Nullable Affinity affinity, boolean discord, boolean targeted) {
		int colorArgb = AffinityColors.argbOf(Optional.ofNullable(affinity), discord);
		// 1px dark bezel for contrast against the world behind the HUD, then the
		// stance-coloured face inset by one pixel on every side.
		graphics.fill(x, y, x + size, y + size, BEZEL_ARGB);
		graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, colorArgb);

		// Glyph stage: the bitmap sprite goes on top of the face, white-on-colour
		// so silhouette plus hue both contribute to readability. Discord wins over
		// affinity to match the colour-routing rule in AffinityColors#argbOf.
		long bits = discord ? DISCORD_GLYPH : glyphFor(affinity);
		if (bits != 0L) {
			int glyphX = x + (size - 8) / 2;
			int glyphY = y + (size - 8) / 2;
			drawGlyph(graphics, glyphX, glyphY, bits, GLYPH_ARGB);
		} else {
			// Unattuned-but-resonance-armed: a single white pip in the centre so
			// the gem is not just a featureless grey square.
			int cx = x + size / 2;
			int cy = y + size / 2;
			graphics.fill(cx, cy, cx + 1, cy + 1, NEUTRAL_PIP_ARGB);
		}

		if (targeted) {
			// Bright ring one pixel outside the bezel — marks the crosshair-picked
			// mob without overlapping the gem face.
			int rx0 = x - 1;
			int ry0 = y - 1;
			int rx1 = x + size + 1;
			int ry1 = y + size + 1;
			graphics.fill(rx0, ry0, rx1, ry0 + 1, TARGETED_RING_ARGB);
			graphics.fill(rx0, ry1 - 1, rx1, ry1, TARGETED_RING_ARGB);
			graphics.fill(rx0, ry0 + 1, rx0 + 1, ry1 - 1, TARGETED_RING_ARGB);
			graphics.fill(rx1 - 1, ry0 + 1, rx1, ry1 - 1, TARGETED_RING_ARGB);
		}
	}

	// The 8x8 glyph for an affinity, or 0L for the neutral state (no affinity).
	// Discord is handled by the caller because it isn't an Affinity enum value.
	private static long glyphFor(@Nullable Affinity affinity) {
		if (affinity == null) {
			return 0L;
		}
		return switch (affinity) {
			case FURY -> FURY_GLYPH;
			case BASTION -> BASTION_GLYPH;
			case ZEPHYR -> ZEPHYR_GLYPH;
		};
	}

	// Decodes an 8x8 bitmap from a 64-bit packed long and paints each set bit as
	// a single-pixel fill. Bit 63 is row 0 column 0; bit 0 is row 7 column 7.
	// This matches a "left-to-right, top-to-bottom" reading of the hex literal.
	private static void drawGlyph(GuiGraphicsExtractor graphics, int x, int y, long bits, int colorArgb) {
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				if (((bits >>> (63 - (row * 8 + col))) & 1L) == 1L) {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, colorArgb);
				}
			}
		}
	}

	// A flat dark track centred over the screen with a coloured fill scaled to
	// the player's resonance. Mirrors the Focus panel's budget bar styling so the
	// two readouts feel paired. Below the Apex threshold the fill sits dim; once
	// at Apex it brightens to full opacity to telegraph the capstone gating. The
	// apex flag is passed in so the draw loop can read {@link Resonance#atApex}
	// once per frame instead of once per bar paint.
	private static void drawResonanceBar(GuiGraphicsExtractor graphics, int screenW, int barY,
			int colorArgb, float resonance, boolean apexArmed) {
		int barX0 = screenW / 2 - RESONANCE_BAR_W / 2;
		int barX1 = barX0 + RESONANCE_BAR_W;
		graphics.fill(barX0, barY, barX1, barY + RESONANCE_BAR_H, RESONANCE_TRACK_ARGB);
		float clamped = Math.max(0.0F, Math.min(1.0F, resonance));
		if (clamped <= 0.0F) {
			return;
		}
		int alpha = apexArmed ? RESONANCE_AT_APEX_ALPHA : RESONANCE_BELOW_APEX_ALPHA;
		int fillArgb = (alpha << 24) | (colorArgb & 0x00FFFFFF);
		int fill = Math.max(1, Math.round(RESONANCE_BAR_W * clamped));
		graphics.fill(barX0, barY, barX0 + fill, barY + RESONANCE_BAR_H, fillArgb);
	}

	// A breathing gold border one pixel outside the player gem — empowered state.
	// Four edge fills rather than an outer fill-then-inner-cut so the halo blends
	// over whatever is behind the HUD without painting a solid square. The pulse
	// phase is driven by the client level's game tick so it freezes whenever the
	// world does (single-player pause); if the client has no level (title screen
	// can briefly route through this path during teardown), skip the halo entirely
	// rather than fall back to wall-clock time.
	private static void drawEmpoweredHalo(GuiGraphicsExtractor graphics, int gemX, int gemY, int size) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		int alpha = haloAlpha(level.getGameTime());
		int haloArgb = (alpha << 24) | (EMPOWERED_HALO_ARGB & 0x00FFFFFF);
		int hx0 = gemX - 1;
		int hy0 = gemY - 1;
		int hx1 = gemX + size + 1;
		int hy1 = gemY + size + 1;
		// Top and bottom edges of the halo.
		graphics.fill(hx0, hy0, hx1, hy0 + 1, haloArgb);
		graphics.fill(hx0, hy1 - 1, hx1, hy1, haloArgb);
		// Left and right edges, inset to avoid double-blending the corners.
		graphics.fill(hx0, hy0 + 1, hx0 + 1, hy1 - 1, haloArgb);
		graphics.fill(hx1 - 1, hy0 + 1, hx1, hy1 - 1, haloArgb);
	}

	// A translucent red overlay across the gem face — neutralized state.
	// Painted inside the bezel so the gem still reads as the player's colour
	// underneath, just dimmed and warned.
	private static void drawNeutralizedTint(GuiGraphicsExtractor graphics, int gemX, int gemY, int size) {
		graphics.fill(gemX + 1, gemY + 1, gemX + size - 1, gemY + size - 1, NEUTRALIZED_TINT_ARGB);
	}

	// The empowered-halo alpha at game-tick {@code nowTicks} — a sine modulation
	// across PULSE_PERIOD_TICKS mapped onto [HALO_ALPHA_MIN, HALO_ALPHA_MAX].
	// Driven by ticks rather than wall time so the pulse pauses with the world.
	private static int haloAlpha(long nowTicks) {
		float phase = (float) ((nowTicks % PULSE_PERIOD_TICKS) / (double) PULSE_PERIOD_TICKS);
		float pulse = 0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2.0);
		return HALO_ALPHA_MIN + Math.round((HALO_ALPHA_MAX - HALO_ALPHA_MIN) * pulse);
	}

	// The ARGB colour of the player's gem: Discord first, then the resolved
	// affinity (already merged with Apex fallback by the caller), else neutral.
	// Centralized through AffinityColors so the bezel paint and every other
	// readout stay in lockstep on a palette change.
	private static int playerArgb(Optional<Affinity> resolvedAffinity, boolean discord) {
		return AffinityColors.argbOf(resolvedAffinity, discord);
	}

	// The crosshair-picked living target if it is within range, else null. An
	// out-of-range pick (the field can hold an entity even when picked at the
	// edge of reach) is treated as no target so the panel stays quiet.
	private static LivingEntity targetedLiving(Minecraft minecraft, Player player) {
		Entity picked = minecraft.crosshairPickEntity;
		if (!(picked instanceof LivingEntity living) || living == player || !living.isAlive()) {
			return null;
		}
		if (player.distanceToSqr(living) > TARGET_RANGE_BLOCKS * TARGET_RANGE_BLOCKS) {
			return null;
		}
		return living;
	}

	// The matchup between the player's committed affinity and the target's.
	// Empty player affinity or empty target affinity falls through to NONE so the
	// gem renders plain — neither empowered nor neutralized.
	private static Matchup matchup(Optional<Affinity> player, Optional<Affinity> target) {
		if (player.isEmpty() || target.isEmpty()) {
			return Matchup.NONE;
		}
		Affinity mine = player.get();
		Affinity theirs = target.get();
		if (mine.beats(theirs)) {
			return Matchup.EMPOWERED;
		}
		if (theirs.beats(mine)) {
			return Matchup.NEUTRALIZED;
		}
		return Matchup.NEUTRAL;
	}
}
