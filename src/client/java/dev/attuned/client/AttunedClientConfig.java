package dev.attuned.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.Attuned;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public record AttunedClientConfig(
		boolean showOwnAffinityHud,
		boolean showEnemyAffinityHud,
		boolean showFociHud) {
	public static final AttunedClientConfig DEFAULT = new AttunedClientConfig(true, true, true);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AttunedClientConfig current = DEFAULT;

	public static AttunedClientConfig get() {
		return current;
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				JsonElement element = JsonParser.parseString(Files.readString(path));
				current = readConfig(element);
				// Rewrite only when normalization changed the on-disk shape
				// (missing/invalid keys); a clean load needs no write.
				if (!GSON.toJson(toJson(current)).equals(Files.readString(path))) {
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

	public static void toggleOwnAffinityHud() {
		setShowOwnAffinityHud(!current.showOwnAffinityHud());
	}

	public static void toggleEnemyAffinityHud() {
		setShowEnemyAffinityHud(!current.showEnemyAffinityHud());
	}

	public static void toggleFociHud() {
		setShowFociHud(!current.showFociHud());
	}

	public static void setShowOwnAffinityHud(boolean showOwnAffinityHud) {
		current = new AttunedClientConfig(showOwnAffinityHud, current.showEnemyAffinityHud(), current.showFociHud());
		save();
	}

	public static void setShowEnemyAffinityHud(boolean showEnemyAffinityHud) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), showEnemyAffinityHud, current.showFociHud());
		save();
	}

	public static void setShowFociHud(boolean showFociHud) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), current.showEnemyAffinityHud(), showFociHud);
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
		return json;
	}

	private static AttunedClientConfig readConfig(JsonElement element) {
		if (!element.isJsonObject()) {
			return DEFAULT;
		}
		JsonObject json = element.getAsJsonObject();
		return new AttunedClientConfig(
			booleanOr(json, "show_own_affinity_hud", DEFAULT.showOwnAffinityHud()),
			booleanOr(json, "show_enemy_affinity_hud", DEFAULT.showEnemyAffinityHud()),
			booleanOr(json, "show_foci_hud", DEFAULT.showFociHud()));
	}

	private static boolean booleanOr(JsonObject json, String key, boolean fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isBoolean()
			? json.get(key).getAsBoolean()
			: fallback;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("attuned-client.json");
	}
}
