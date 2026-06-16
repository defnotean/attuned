package dev.attuned.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.FocusPreset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Clipboard export/import codec for saved Focus builds. */
public final class BuildShareCodec {
	public static final String PREFIX = "attuned:v1:";
	private static final int MAX_NAME_LENGTH = 32;

	private BuildShareCodec() {}

	public static String encode(FocusPreset preset) {
		JsonObject json = new JsonObject();
		json.addProperty("name", preset.name());
		JsonArray slots = new JsonArray();
		for (String slot : preset.slots()) {
			slots.add(slot);
		}
		json.add("slots", slots);
		byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static Optional<FocusPreset> decode(String raw) {
		if (raw == null || !raw.startsWith(PREFIX)) {
			return Optional.empty();
		}
		String encoded = raw.substring(PREFIX.length());
		if (encoded.isEmpty()) {
			return Optional.empty();
		}
		byte[] bytes;
		try {
			bytes = Base64.getUrlDecoder().decode(encoded);
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
		if (bytes.length == 0) {
			return Optional.empty();
		}
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
		} catch (Exception ignored) {
			return Optional.empty();
		}
		if (!parsed.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject json = parsed.getAsJsonObject();
		if (!json.has("name") || !json.has("slots") || !json.get("slots").isJsonArray()) {
			return Optional.empty();
		}
		String name = json.get("name").getAsString();
		if (name == null) {
			return Optional.empty();
		}
		String trimmedName = name.trim();
		if (trimmedName.length() > MAX_NAME_LENGTH) {
			return Optional.empty();
		}
		JsonArray slotsArray = json.getAsJsonArray("slots");
		if (slotsArray.size() > AttunedInv.SIZE) {
			return Optional.empty();
		}
		List<String> slots = new ArrayList<>(slotsArray.size());
		for (JsonElement element : slotsArray) {
			if (!element.isJsonPrimitive()) {
				return Optional.empty();
			}
			slots.add(element.getAsString());
		}
		return Optional.of(new FocusPreset(name, slots));
	}
}
