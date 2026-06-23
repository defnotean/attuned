package dev.attuned.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.Attuned;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public record AttunedClientConfig(
		boolean showOwnAffinityHud,
		boolean showEnemyAffinityHud,
		boolean showFociHud,
		boolean showPartyHud,
		HudLayout hudLayout,
		HudLayout partyHudLayout) {

	/**
	 * Where the Foci HUD anchors on screen. The combat readout keeps its
	 * hotbar-adjacent docking (it telegraphs combat state next to the hotbar by
	 * design); the corner Foci HUD is the piece that collides with minimap mods,
	 * so it is the piece that moves.
	 */
	public enum HudAnchor {
		BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT;

		public boolean left() {
			return this == BOTTOM_LEFT || this == TOP_LEFT;
		}

		public boolean top() {
			return this == TOP_RIGHT || this == TOP_LEFT;
		}

		static HudAnchor fromKey(String key, HudAnchor fallback) {
			if (key == null) {
				return fallback;
			}
			String normalized = key.trim().toLowerCase(Locale.ROOT);
			for (HudAnchor anchor : values()) {
				if (anchor.key().equals(normalized)) {
					return anchor;
				}
			}
			return fallback;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** User-positioned HUD placement: corner anchor, pixel offsets, and scale. */
	public record HudLayout(HudAnchor anchor, int offsetX, int offsetY, float scale) {
		public static final float MIN_SCALE = 0.5F;
		public static final float MAX_SCALE = 2.0F;
		private static final int MAX_OFFSET = 4096;
		private static final int PARTY_BOSS_BAR_CLEARANCE_Y = 36;
		public static final HudLayout DEFAULT = new HudLayout(HudAnchor.BOTTOM_RIGHT, 0, 0, 1.0F);
		public static final HudLayout PARTY_DEFAULT =
			new HudLayout(HudAnchor.TOP_LEFT, 0, PARTY_BOSS_BAR_CLEARANCE_Y, 1.0F);

		public HudLayout {
			anchor = anchor == null ? HudAnchor.BOTTOM_RIGHT : anchor;
			offsetX = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offsetX));
			offsetY = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offsetY));
			// Snap to quarter steps inside [0.5, 2.0] so layouts stay pixel-crisp.
			float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
			scale = Math.round(clamped * 4.0F) / 4.0F;
		}
	}

	public static final AttunedClientConfig DEFAULT =
		new AttunedClientConfig(true, true, true, true, HudLayout.DEFAULT, HudLayout.PARTY_DEFAULT);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AttunedClientConfig current = DEFAULT;
	private static boolean repairLogged;

	public static AttunedClientConfig get() {
		return current;
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				String raw = Files.readString(path);
				JsonElement element = JsonParser.parseString(raw);
				ReadResult result = readConfigResult(element);
				current = result.config();
				if (result.repaired()) {
					logRepairOnce(path);
				}
				// Rewrite only when normalization changed the on-disk shape
				// (missing/invalid keys); a clean load needs no write.
				if (!GSON.toJson(toJson(current)).equals(raw)) {
					save();
				}
			} catch (Exception e) {
				Attuned.LOGGER.error("Invalid {} - using default client HUD settings.", path, e);
				current = DEFAULT;
				save();
			}
		} else {
			current = DEFAULT;
			save();
		}
	}

	private static void logRepairOnce(Path path) {
		if (repairLogged) {
			return;
		}
		repairLogged = true;
		Attuned.LOGGER.warn("Repaired invalid Attuned HUD config at {}; values were reset to safe defaults.", path);
	}

	public static void toggleOwnAffinityHud() {
		setShowOwnAffinityHud(!current.showOwnAffinityHud());
	}

	public static void toggleEnemyAffinityHud() {
		setShowEnemyAffinityHud(!current.showEnemyAffinityHud());
	}

	public static void toggleFociHud() {
		setShowFociHud(!current.showFociHud());
	}

	public static void togglePartyHud() {
		setShowPartyHud(!current.showPartyHud());
	}

	public static void setShowOwnAffinityHud(boolean showOwnAffinityHud) {
		current = new AttunedClientConfig(showOwnAffinityHud, current.showEnemyAffinityHud(),
			current.showFociHud(), current.showPartyHud(), current.hudLayout(), current.partyHudLayout());
		save();
	}

	public static void setShowEnemyAffinityHud(boolean showEnemyAffinityHud) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), showEnemyAffinityHud,
			current.showFociHud(), current.showPartyHud(), current.hudLayout(), current.partyHudLayout());
		save();
	}

	public static void setShowFociHud(boolean showFociHud) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), current.showEnemyAffinityHud(),
			showFociHud, current.showPartyHud(), current.hudLayout(), current.partyHudLayout());
		save();
	}

	public static void setShowPartyHud(boolean showPartyHud) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), current.showEnemyAffinityHud(),
			current.showFociHud(), showPartyHud, current.hudLayout(), current.partyHudLayout());
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(toJson(current)));
		} catch (Exception e) {
			Attuned.LOGGER.error("Could not write {}", path, e);
		}
	}

	private static JsonObject toJson(AttunedClientConfig config) {
		JsonObject json = new JsonObject();
		json.addProperty("show_own_affinity_hud", config.showOwnAffinityHud());
		json.addProperty("show_enemy_affinity_hud", config.showEnemyAffinityHud());
		json.addProperty("show_foci_hud", config.showFociHud());
		json.addProperty("show_party_hud", config.showPartyHud());
		json.addProperty("hud_anchor", config.hudLayout().anchor().key());
		json.addProperty("hud_offset_x", config.hudLayout().offsetX());
		json.addProperty("hud_offset_y", config.hudLayout().offsetY());
		json.addProperty("hud_scale", config.hudLayout().scale());
		json.addProperty("party_hud_anchor", config.partyHudLayout().anchor().key());
		json.addProperty("party_hud_offset_x", config.partyHudLayout().offsetX());
		json.addProperty("party_hud_offset_y", config.partyHudLayout().offsetY());
		json.addProperty("party_hud_scale", config.partyHudLayout().scale());
		return json;
	}

	private record ReadResult(AttunedClientConfig config, boolean repaired) {
	}

	private static ReadResult readConfigResult(JsonElement element) {
		if (!element.isJsonObject()) {
			return new ReadResult(DEFAULT, true);
		}
		JsonObject json = element.getAsJsonObject();
		RepairTracker repairs = new RepairTracker();
		HudLayout layout = new HudLayout(
			anchorOr(json, "hud_anchor", HudLayout.DEFAULT.anchor(), repairs),
			intOr(json, "hud_offset_x", HudLayout.DEFAULT.offsetX()),
			intOr(json, "hud_offset_y", HudLayout.DEFAULT.offsetY()),
			scaleOr(json, "hud_scale", HudLayout.DEFAULT.scale(), repairs));
		HudLayout partyLayout = new HudLayout(
			anchorOr(json, "party_hud_anchor", HudLayout.PARTY_DEFAULT.anchor(), repairs),
			intOr(json, "party_hud_offset_x", HudLayout.PARTY_DEFAULT.offsetX()),
			intOr(json, "party_hud_offset_y", HudLayout.PARTY_DEFAULT.offsetY()),
			scaleOr(json, "party_hud_scale", HudLayout.PARTY_DEFAULT.scale(), repairs));
		return new ReadResult(new AttunedClientConfig(
			booleanOr(json, "show_own_affinity_hud", DEFAULT.showOwnAffinityHud()),
			booleanOr(json, "show_enemy_affinity_hud", DEFAULT.showEnemyAffinityHud()),
			booleanOr(json, "show_foci_hud", DEFAULT.showFociHud()),
			booleanOr(json, "show_party_hud", DEFAULT.showPartyHud()),
			layout,
			partyLayout), repairs.repaired());
	}

	private static HudAnchor anchorOr(JsonObject json, String key, HudAnchor fallback, RepairTracker repairs) {
		if (!json.has(key)) {
			return fallback;
		}
		if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString()) {
			repairs.mark();
			return fallback;
		}
		String value = json.get(key).getAsString();
		HudAnchor anchor = HudAnchor.fromKey(value, fallback);
		if (anchor == fallback && !fallback.key().equals(value.trim().toLowerCase(Locale.ROOT))) {
			repairs.mark();
		}
		return anchor;
	}

	private static float scaleOr(JsonObject json, String key, float fallback, RepairTracker repairs) {
		if (!json.has(key)) {
			return fallback;
		}
		if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
			repairs.mark();
			return fallback;
		}
		float scale = json.get(key).getAsFloat();
		if (!Float.isFinite(scale) || scale < HudLayout.MIN_SCALE || scale > HudLayout.MAX_SCALE) {
			repairs.mark();
		}
		return scale;
	}

	private static boolean booleanOr(JsonObject json, String key, boolean fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isBoolean()
			? json.get(key).getAsBoolean()
			: fallback;
	}

	private static int intOr(JsonObject json, String key, int fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
			? json.get(key).getAsInt()
			: fallback;
	}

	private static float floatOr(JsonObject json, String key, float fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
			? json.get(key).getAsFloat()
			: fallback;
	}

	private static final class RepairTracker {
		private boolean repaired;

		void mark() {
			repaired = true;
		}

		boolean repaired() {
			return repaired;
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("attuned-client.json");
	}
}
