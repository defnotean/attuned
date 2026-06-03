package dev.attuned.client.hud;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import dev.attuned.client.AttunedClientConfig;
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

import java.util.Locale;
import java.util.Optional;

/**
 * The combat heads-up overlay: a compact panel anchored above the hotbar that
 * shows the player's stance gem, a resonance arc, the targeted entity's affinity
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
	private static final int PLAYER_GEM_SIZE = 20;
	private static final int TARGET_GEM_SIZE = 16;
	private static final int RESONANCE_BAR_W = 14;
	private static final int RESONANCE_BAR_H = 3;
	private static final int HUD_BACKPLATE_W = 50;
	private static final int HUD_BACKPLATE_H = 24;

	// Gap between the player gem and the target gem.
	private static final int GEM_GAP = 8;
	private static final int MATCHUP_LINK_W = 8;
	private static final int MATCHUP_LINK_H = 4;
	// Vanilla hotbar width is 182 GUI pixels; dock beside it instead of over XP.
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int HOTBAR_GAP = 8;
	private static final int SCREEN_MARGIN = 4;
	private static final int FOCI_HUD_HEIGHT = FociHud.HUD_H;
	// HUD sprite identifiers resolve against the GUI sprite atlas at
	// {@code assets/attuned/textures/gui/sprites/hud/<name>.png}. Routing through
	// the sprite atlas (same path vanilla heart/hunger icons use) gets us proper
	// filtering and mipmapping at any HUD render size, so the gem face and the
	// centred icon stay readable when the gem is downscaled to 16-20 px.
	private static final Identifier FURY_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_fury");
	private static final Identifier BASTION_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_bastion");
	private static final Identifier ZEPHYR_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_zephyr");
	private static final Identifier HOLY_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_holy");
	private static final Identifier DISCORD_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_discord");
	private static final Identifier NEUTRAL_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/affinity_neutral");
	private static final Identifier TARGET_RING_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_target_ring");
	private static final Identifier EMPOWERED_RING_SPRITE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_empowered_ring");
	private static final Identifier NEUTRALIZED_OVERLAY_SPRITE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_neutralized_overlay");
	private static final Identifier LINK_NEUTRAL_SPRITE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_link_neutral");
	private static final Identifier LINK_EMPOWERED_SPRITE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_link_empowered");
	private static final Identifier LINK_NEUTRALIZED_SPRITE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "hud/hud_link_neutralized");
	private static final Identifier HUD_BACKPLATE_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/hud_backplate.png");
	private static final Identifier RESONANCE_TRACK_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/sprites/hud/hud_resonance_track.png");
	private static final Identifier RESONANCE_FILL_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/sprites/hud/hud_resonance_fill.png");

	// The matchup of the player's affinity against the targeted entity's.
	private enum Matchup { EMPOWERED, NEUTRAL, NEUTRALIZED, NONE }

	private record TargetStance(@Nullable Affinity affinity, boolean discord,
			@Nullable Apex.Capstone capstone, boolean apexArmed) {
		Optional<Affinity> matchupAffinity() {
			return discord ? Optional.empty() : Optional.ofNullable(affinity);
		}
	}

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
		boolean showOwn = AttunedClientConfig.get().showOwnAffinityHud();
		boolean showEnemy = AttunedClientConfig.get().showEnemyAffinityHud();
		if (!showOwn && !showEnemy) {
			return;
		}

		LivingEntity target = showEnemy ? targetedLiving(minecraft, player) : null;
		TargetStance targetStance = showEnemy ? targetedStance(target) : null;
		boolean hasTarget = targetStance != null;
		Optional<Affinity> targetAffinity = hasTarget ? targetStance.matchupAffinity() : Optional.empty();
		// Cheapest gate first — a plain float read off the player attachment.
		float resonance = Resonance.get(player);
		Optional<Affinity> committed = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		// Skip the panel entirely for unattuned players with no resonance — there
		// is nothing combat-relevant to telegraph.
		if (showOwn && committed.isEmpty() && !discord && resonance <= 0.0F && !hasTarget) {
			return;
		}
		if (!showOwn && !hasTarget) {
			return;
		}
		Optional<Apex.Capstone> capstone = Apex.capstoneOf(player);
		boolean apexArmed = capstone.isPresent() && Resonance.atApex(player);

		// The affinity actually painted on the player gem — committed first, then
		// the Apex fallback for legacy state, so the glyph routing matches the
		// bezel colour rather than diverging from it.
		Matchup matchup = matchup(committed, targetAffinity);

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();

		// Lay the panel out centred horizontally above the hotbar. With a target,
		// the gem row's full width includes both gems and the gap between them.
		int rowWidth = showOwn ? PLAYER_GEM_SIZE + (hasTarget ? GEM_GAP + TARGET_GEM_SIZE : 0) : TARGET_GEM_SIZE;
		int backplateW = backplateWidth(showOwn, hasTarget);
		boolean fociHudVisible = FociHud.isVisible(player);
		int backplateX = fociHudVisible ? secondarySidecarX(screenW, backplateW) : sidecarX(screenW, backplateW);
		int backplateY = fociHudVisible ? secondarySidecarY(screenW, screenH, backplateW) : sidecarY(screenW, screenH, backplateW);
		int rowX = backplateX + (backplateW - rowWidth) / 2;
		int rowY = backplateY + (showOwn ? 2 : (HUD_BACKPLATE_H - TARGET_GEM_SIZE) / 2);
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			HUD_BACKPLATE_TEXTURE, backplateX, backplateY,
			0.0F, 0.0F, backplateW, HUD_BACKPLATE_H, HUD_BACKPLATE_W, HUD_BACKPLATE_H);

		// Resonance bar first, just above the gem row, so the gem itself sits on
		// top in the visual stack and never gets clipped by the bar.
		if (showOwn) {
			drawResonanceBar(graphics, rowX + 3, rowY + PLAYER_GEM_SIZE - 3,
				RESONANCE_BAR_W, resonance);

		// Player gem with a dark bezel — same construction as the Focus panel gem.
		// The player's own gem is never "targeted" — that flag is for the mob gem.
			drawPlayerGem(graphics, rowX, rowY, PLAYER_GEM_SIZE,
				committed.orElse(null), discord, capstone.orElse(null), apexArmed);

		// Matchup state markers — gold pulse halo for empowered, red dim overlay
		// for neutralized. Neutral and "no target" cases leave the gem plain.
			if (matchup == Matchup.EMPOWERED) {
				drawOverlaySprite(graphics, EMPOWERED_RING_SPRITE, rowX, rowY, PLAYER_GEM_SIZE);
			} else if (matchup == Matchup.NEUTRALIZED) {
				drawOverlaySprite(graphics, NEUTRALIZED_OVERLAY_SPRITE, rowX, rowY, PLAYER_GEM_SIZE);
			}
		}

		// Target gem to the right of the player gem, vertically centred against it.
		if (hasTarget) {
			int targetX = showOwn ? rowX + PLAYER_GEM_SIZE + GEM_GAP : rowX;
			int targetY = showOwn ? rowY + (PLAYER_GEM_SIZE - TARGET_GEM_SIZE) / 2 : rowY;
			if (showOwn) {
				drawMatchupLink(graphics, rowX + PLAYER_GEM_SIZE, rowY + PLAYER_GEM_SIZE / 2 - 2, matchup);
			}
			drawTargetGem(graphics, targetX, targetY, TARGET_GEM_SIZE, targetStance);
		}
	}

	private static TargetStance targetedStance(@Nullable LivingEntity target) {
		if (target == null) {
			return null;
		}
		if (target instanceof Player targetPlayer) {
			if (Attunement.activeSlots(targetPlayer).isEmpty()
					&& Resonance.get(targetPlayer) <= 0.0F
					&& Apex.capstoneOf(targetPlayer).isEmpty()) {
				return null;
			}
			Optional<Apex.Capstone> capstone = Apex.capstoneOf(targetPlayer);
			return new TargetStance(
				Attunement.committedAffinity(targetPlayer).orElse(null),
				Attunement.isDiscord(targetPlayer),
				capstone.orElse(null),
				capstone.isPresent() && Resonance.atApex(targetPlayer));
		}
		return MobAffinities.of(target)
			.map(affinity -> new TargetStance(affinity, false, null, false))
			.orElse(null);
	}

	private static int backplateWidth(boolean showOwn, boolean hasTarget) {
		return HUD_BACKPLATE_W;
	}

	private static int sidecarX(int screenW, int backplateW) {
		int right = screenW / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
		if (right + backplateW <= screenW - SCREEN_MARGIN) {
			return right;
		}
		int left = screenW / 2 - HOTBAR_HALF_WIDTH - HOTBAR_GAP - backplateW;
		if (left >= SCREEN_MARGIN) {
			return left;
		}
		return Math.max(SCREEN_MARGIN, screenW - backplateW - SCREEN_MARGIN);
	}

	private static int secondarySidecarX(int screenW, int backplateW) {
		int primary = FociHud.primarySidecarX(screenW, FociHud.HUD_W);
		int right = screenW / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
		int left = screenW / 2 - HOTBAR_HALF_WIDTH - HOTBAR_GAP - backplateW;
		if (primary >= screenW / 2 && left >= SCREEN_MARGIN) {
			return left;
		}
		if (primary < screenW / 2 && right + backplateW <= screenW - SCREEN_MARGIN) {
			return right;
		}
		return sidecarX(screenW, backplateW);
	}

	private static int sidecarY(int screenW, int screenH, int backplateW) {
		if (!hasSidecarRoom(screenW, backplateW)) {
			return SCREEN_MARGIN;
		}
		return Math.max(SCREEN_MARGIN, screenH - HUD_BACKPLATE_H - SCREEN_MARGIN);
	}

	private static int secondarySidecarY(int screenW, int screenH, int backplateW) {
		if (!hasSidecarRoom(screenW, backplateW)) {
			return Math.min(
				Math.max(SCREEN_MARGIN, screenH - HUD_BACKPLATE_H - SCREEN_MARGIN),
				SCREEN_MARGIN + FOCI_HUD_HEIGHT + SCREEN_MARGIN);
		}
		return sidecarY(screenW, screenH, backplateW);
	}

	private static boolean hasSidecarRoom(int screenW, int backplateW) {
		int right = screenW / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
		int left = screenW / 2 - HOTBAR_HALF_WIDTH - HOTBAR_GAP - backplateW;
		return right + backplateW <= screenW - SCREEN_MARGIN || left >= SCREEN_MARGIN;
	}

	private static void drawMatchupLink(GuiGraphicsExtractor graphics, int x, int y, Matchup matchup) {
		Identifier sprite = switch (matchup) {
			case EMPOWERED -> LINK_EMPOWERED_SPRITE;
			case NEUTRALIZED -> LINK_NEUTRALIZED_SPRITE;
			case NEUTRAL, NONE -> LINK_NEUTRAL_SPRITE;
		};
		graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			sprite, x, y, MATCHUP_LINK_W, MATCHUP_LINK_H);
	}

	/**
	 * Paints one texture-backed affinity gem. Normal affinity, Discord, neutral,
	 * target ring, matchup ring, and Apex states all resolve to PNG assets in the
	 * HUD sprite atlas instead of being drawn one pixel at a time.
	 */
	public static void drawGem(GuiGraphicsExtractor graphics, int x, int y, int size,
			@Nullable Affinity affinity, boolean discord, boolean targeted, boolean atApex) {
		Identifier sprite = atApex && affinity != null && !discord
			? capstoneSpriteFor(Apex.Capstone.ofAffinity(affinity))
			: affinitySpriteFor(affinity, discord);
		drawGemSprite(graphics, x, y, size, targeted, sprite);
	}

	public static void drawPlayerGem(GuiGraphicsExtractor graphics, int x, int y, int size,
			@Nullable Affinity affinity, boolean discord, @Nullable Apex.Capstone capstone, boolean atApex) {
		Identifier sprite = atApex && capstone != null
			? capstoneSpriteFor(capstone)
			: affinitySpriteFor(affinity, discord);
		drawGemSprite(graphics, x, y, size, false, sprite);
	}

	private static void drawTargetGem(GuiGraphicsExtractor graphics, int x, int y, int size,
			TargetStance stance) {
		drawPlayerGem(graphics, x, y, size, stance.affinity(), stance.discord(), stance.capstone(), stance.apexArmed());
		drawOverlaySprite(graphics, TARGET_RING_SPRITE, x, y, size);
	}

	private static void drawGemSprite(GuiGraphicsExtractor graphics, int x, int y, int size,
			boolean targeted, Identifier sprite) {
		graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			sprite, x, y, size, size);
		if (targeted) {
			drawOverlaySprite(graphics, TARGET_RING_SPRITE, x, y, size);
		}
	}

	private static Identifier affinitySpriteFor(@Nullable Affinity affinity, boolean discord) {
		if (discord) {
			return DISCORD_SPRITE;
		}
		if (affinity == null) {
			return NEUTRAL_SPRITE;
		}
		return switch (affinity) {
			case FURY -> FURY_SPRITE;
			case BASTION -> BASTION_SPRITE;
			case ZEPHYR -> ZEPHYR_SPRITE;
			case HOLY -> HOLY_SPRITE;
		};
	}

	private static Identifier capstoneSpriteFor(Apex.Capstone capstone) {
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID,
			"hud/" + capstone.name().toLowerCase(Locale.ROOT));
	}

	private static void drawOverlaySprite(GuiGraphicsExtractor graphics, Identifier sprite, int gemX, int gemY, int size) {
		graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			sprite, gemX - 2, gemY - 2, size + 4, size + 4);
	}

	private static void drawResonanceBar(GuiGraphicsExtractor graphics, int barX0, int barY, int barWidth,
			float resonance) {
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			RESONANCE_TRACK_TEXTURE, barX0, barY,
			0.0F, 0.0F, barWidth, RESONANCE_BAR_H, RESONANCE_BAR_W, RESONANCE_BAR_H);
		float clamped = Math.max(0.0F, Math.min(1.0F, resonance));
		if (clamped <= 0.0F) {
			return;
		}
		int fill = Math.max(1, Math.round(barWidth * clamped));
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
			RESONANCE_FILL_TEXTURE, barX0, barY,
			0.0F, 0.0F, fill, RESONANCE_BAR_H, RESONANCE_BAR_W, RESONANCE_BAR_H);
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
