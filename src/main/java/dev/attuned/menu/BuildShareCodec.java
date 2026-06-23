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
		if (!preset.role().isEmpty()) {
			json.addProperty("role", preset.role());
		}
		if (!preset.note().isEmpty()) {
			json.addProperty("note", preset.note());
		}
		if (preset.preferredPartySize() > 0) {
			json.addProperty("party_size", preset.preferredPartySize());
		}
		addStringArray(json, "warnings", preset.warnings());
		addStringArray(json, "requires", preset.requires());
		if (!preset.minecraftVersion().isEmpty()) {
			json.addProperty("mc", preset.minecraftVersion());
		}
		if (!preset.attunedVersion().isEmpty()) {
			json.addProperty("attuned", preset.attunedVersion());
		}
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
		JsonElement nameElement = json.get("name");
		if (!isStringPrimitive(nameElement)) {
			return Optional.empty();
		}
		String name = nameElement.getAsString();
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
			if (!isStringPrimitive(element)) {
				return Optional.empty();
			}
			slots.add(element.getAsString());
		}
		Optional<String> role = optionalString(json, "role");
		Optional<String> note = optionalString(json, "note");
		Optional<Integer> partySize = optionalInt(json, "party_size");
		Optional<List<String>> warnings = optionalStringList(json, "warnings");
		Optional<List<String>> requires = optionalStringList(json, "requires");
		Optional<String> minecraftVersion = optionalString(json, "mc");
		Optional<String> attunedVersion = optionalString(json, "attuned");
		if (role.isEmpty() || note.isEmpty() || partySize.isEmpty()
				|| warnings.isEmpty() || requires.isEmpty()
				|| minecraftVersion.isEmpty() || attunedVersion.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new FocusPreset(name, slots,
			new FocusPreset.SetupMetadata(
				role.orElseThrow(),
				note.orElseThrow(),
				partySize.orElseThrow(),
				warnings.orElseThrow(),
				requires.orElseThrow(),
				minecraftVersion.orElseThrow(),
				attunedVersion.orElseThrow())));
	}

	private static boolean isStringPrimitive(JsonElement element) {
		return element != null && element.isJsonPrimitive()
			&& element.getAsJsonPrimitive().isString();
	}

	private static void addStringArray(JsonObject json, String field, List<String> values) {
		if (values.isEmpty()) {
			return;
		}
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		json.add(field, array);
	}

	private static Optional<String> optionalString(JsonObject json, String field) {
		if (!json.has(field)) {
			return Optional.of("");
		}
		JsonElement element = json.get(field);
		return isStringPrimitive(element) ? Optional.of(element.getAsString()) : Optional.empty();
	}

	private static Optional<Integer> optionalInt(JsonObject json, String field) {
		if (!json.has(field)) {
			return Optional.of(0);
		}
		JsonElement element = json.get(field);
		if (element == null || !element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isNumber()) {
			return Optional.empty();
		}
		try {
			return Optional.of(element.getAsInt());
		} catch (NumberFormatException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<List<String>> optionalStringList(JsonObject json, String field) {
		if (!json.has(field)) {
			return Optional.of(List.of());
		}
		JsonElement element = json.get(field);
		if (element == null || !element.isJsonArray()) {
			return Optional.empty();
		}
		JsonArray array = element.getAsJsonArray();
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement entry : array) {
			if (!isStringPrimitive(entry)) {
				return Optional.empty();
			}
			values.add(entry.getAsString());
		}
		return Optional.of(values);
	}
}
