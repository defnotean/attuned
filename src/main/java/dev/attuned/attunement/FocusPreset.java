package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Registry-id snapshot of the six equipped Focus slots. */
public record FocusPreset(String name, List<String> slots, FocusPreset.SetupMetadata metadata) {
	private static final int MAX_NAME_LENGTH = 32;
	private static final int MAX_ROLE_LENGTH = 32;
	private static final int MAX_NOTE_LENGTH = 96;
	private static final int MAX_WARNING_LENGTH = 96;
	private static final int MAX_VERSION_LENGTH = 32;
	private static final int MAX_METADATA_LIST_SIZE = 16;
	private static final int MAX_PARTY_SIZE = 8;
	private static final String DEFAULT_NAME = "Preset";
	private static final String TEMPERED_SUFFIX = "#tempered";

	public FocusPreset(String name, List<String> slots) {
		this(name, slots, SetupMetadata.EMPTY);
	}

	public FocusPreset {
		name = normalizeName(name);
		slots = normalizeSlots(slots);
		metadata = metadata == null ? SetupMetadata.EMPTY : new SetupMetadata(
			metadata.role(),
			metadata.note(),
			metadata.preferredPartySize(),
			metadata.warnings(),
			metadata.requires(),
			metadata.minecraftVersion(),
			metadata.attunedVersion());
	}

	public static final Codec<FocusPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("name").forGetter(FocusPreset::name),
		Codec.STRING.listOf().fieldOf("slots").forGetter(FocusPreset::slots),
		Codec.STRING.optionalFieldOf("role", "").forGetter(FocusPreset::role),
		Codec.STRING.optionalFieldOf("note", "").forGetter(FocusPreset::note),
		Codec.intRange(0, MAX_PARTY_SIZE).optionalFieldOf("party_size", 0).forGetter(FocusPreset::preferredPartySize),
		Codec.STRING.listOf().optionalFieldOf("warnings", List.of()).forGetter(FocusPreset::warnings),
		Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(FocusPreset::requires),
		Codec.STRING.optionalFieldOf("mc", "").forGetter(FocusPreset::minecraftVersion),
		Codec.STRING.optionalFieldOf("attuned", "").forGetter(FocusPreset::attunedVersion)
	).apply(instance, (name, slots, role, note, preferredPartySize, warnings, requires, mc, attuned) ->
		new FocusPreset(name, slots, new SetupMetadata(role, note, preferredPartySize, warnings, requires, mc, attuned))));

	public String role() {
		return metadata.role();
	}

	public String note() {
		return metadata.note();
	}

	public int preferredPartySize() {
		return metadata.preferredPartySize();
	}

	public List<String> warnings() {
		return metadata.warnings();
	}

	public List<String> requires() {
		return metadata.requires();
	}

	public String minecraftVersion() {
		return metadata.minecraftVersion();
	}

	public String attunedVersion() {
		return metadata.attunedVersion();
	}

	public static String sanitizeName(String raw) {
		return normalizeName(raw);
	}

	private static String normalizeName(String raw) {
		String normalized = normalizePlainText(raw, MAX_NAME_LENGTH);
		if (normalized.isEmpty()) {
			normalized = DEFAULT_NAME;
		}
		return normalized;
	}

	private static String normalizePlainText(String raw, int maxLength) {
		String normalized = raw == null ? "" : raw.trim();
		StringBuilder sanitized = new StringBuilder(normalized.length());
		for (int index = 0; index < normalized.length(); index++) {
			char current = normalized.charAt(index);
			if (current == '\u00A7') {
				index++;
				continue;
			}
			if (Character.isISOControl(current)) {
				continue;
			}
			sanitized.append(current);
		}
		normalized = sanitized.toString().trim();
		return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
	}

	private static List<String> normalizeSlots(List<String> source) {
		List<String> normalized = new ArrayList<>(AttunedInv.SIZE);
		int sourceSize = source == null ? 0 : source.size();
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			String slot = i < sourceSize ? source.get(i) : "";
			normalized.add(normalizeSlot(slot));
		}
		return List.copyOf(normalized);
	}

	public static String slotKey(String id, boolean tempered) {
		String normalizedId = normalizeSlotId(id);
		if (normalizedId.isEmpty()) {
			return "";
		}
		return tempered ? normalizedId + TEMPERED_SUFFIX : normalizedId;
	}

	public static String slotId(String slot) {
		String normalized = normalizeSlot(slot);
		return requiresTempered(normalized)
			? normalized.substring(0, normalized.length() - TEMPERED_SUFFIX.length())
			: normalized;
	}

	public static boolean requiresTempered(String slot) {
		String normalized = slot == null ? "" : slot.trim();
		return normalized.endsWith(TEMPERED_SUFFIX)
			&& normalized.length() > TEMPERED_SUFFIX.length();
	}

	private static String normalizeSlot(String slot) {
		String normalized = slot == null ? "" : slot.trim();
		if (normalized.isEmpty()) {
			return "";
		}
		return slotKey(
			requiresTempered(normalized)
				? normalized.substring(0, normalized.length() - TEMPERED_SUFFIX.length())
				: normalized,
			requiresTempered(normalized));
	}

	private static String normalizeSlotId(String id) {
		return id == null ? "" : id.trim();
	}

	private static List<String> normalizePlainTextList(List<String> source, int maxLength) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		List<String> normalized = new ArrayList<>(Math.min(source.size(), MAX_METADATA_LIST_SIZE));
		for (String raw : source) {
			String value = normalizePlainText(raw, maxLength);
			if (value.isEmpty() || normalized.contains(value)) {
				continue;
			}
			normalized.add(value);
			if (normalized.size() >= MAX_METADATA_LIST_SIZE) {
				break;
			}
		}
		return List.copyOf(normalized);
	}

	private static List<String> normalizeRequires(List<String> source) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		List<String> normalized = new ArrayList<>(Math.min(source.size(), MAX_METADATA_LIST_SIZE));
		for (String raw : source) {
			String id = slotId(raw);
			if (id.isEmpty() || normalized.contains(id)) {
				continue;
			}
			normalized.add(id);
			if (normalized.size() >= MAX_METADATA_LIST_SIZE) {
				break;
			}
		}
		return List.copyOf(normalized);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
		buf.writeCollection(slots, FriendlyByteBuf::writeUtf);
		metadata.write(buf);
	}

	public static FocusPreset read(FriendlyByteBuf buf) {
		return new FocusPreset(
			buf.readUtf(MAX_NAME_LENGTH),
			// Bounded: serverbound via ImportPresetPayload — a hacked client must
			// not be able to force allocation of an arbitrarily long slots list.
			readBoundedStringList(buf, AttunedInv.SIZE),
			SetupMetadata.read(buf));
	}

	static List<String> readBoundedStringList(FriendlyByteBuf buf, int maxSize) {
		int size = buf.readVarInt();
		if (size < 0 || size > maxSize) {
			throw new io.netty.handler.codec.DecoderException(
				"Preset list length " + size + " exceeds bound " + maxSize);
		}
		List<String> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(buf.readUtf());
		}
		return list;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", name);
		ListTag list = new ListTag();
		for (String slot : slots) {
			list.add(StringTag.valueOf(slot));
		}
		tag.put("Slots", list);
		tag.put("Metadata", metadata.toTag());
		return tag;
	}

	public static FocusPreset fromTag(CompoundTag tag) {
		List<String> decoded = new ArrayList<>(AttunedInv.SIZE);
		if (tag != null) {
			ListTag list = tag.getList("Slots", Tag.TAG_STRING);
			for (int i = 0; i < list.size(); i++) {
				decoded.add(list.getString(i));
			}
		}
		SetupMetadata metadata = tag != null ? SetupMetadata.fromTag(tag.getCompound("Metadata")) : SetupMetadata.EMPTY;
		return new FocusPreset(tag == null ? DEFAULT_NAME : tag.getString("Name"), decoded, metadata);
	}

	public record SetupMetadata(
			String role,
			String note,
			int preferredPartySize,
			List<String> warnings,
			List<String> requires,
			String minecraftVersion,
			String attunedVersion) {
		public static final SetupMetadata EMPTY =
			new SetupMetadata("", "", 0, List.of(), List.of(), "", "");

		public SetupMetadata {
			role = normalizePlainText(role, MAX_ROLE_LENGTH);
			note = normalizePlainText(note, MAX_NOTE_LENGTH);
			preferredPartySize = Math.max(0, Math.min(MAX_PARTY_SIZE, preferredPartySize));
			warnings = normalizePlainTextList(warnings, MAX_WARNING_LENGTH);
			requires = normalizeRequires(requires);
			minecraftVersion = normalizePlainText(minecraftVersion, MAX_VERSION_LENGTH);
			attunedVersion = normalizePlainText(attunedVersion, MAX_VERSION_LENGTH);
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeUtf(role);
			buf.writeUtf(note);
			buf.writeVarInt(preferredPartySize);
			buf.writeCollection(warnings, FriendlyByteBuf::writeUtf);
			buf.writeCollection(requires, FriendlyByteBuf::writeUtf);
			buf.writeUtf(minecraftVersion);
			buf.writeUtf(attunedVersion);
		}

		public static SetupMetadata read(FriendlyByteBuf buf) {
			return new SetupMetadata(
				buf.readUtf(MAX_ROLE_LENGTH),
				buf.readUtf(MAX_NOTE_LENGTH),
				buf.readVarInt(),
				// Bounded list decode: this payload arrives serverbound from
				// ImportPresetPayload, so oversized attacker-controlled lists
				// must be rejected at the frame instead of after allocation.
				readBoundedStringList(buf, MAX_METADATA_LIST_SIZE),
				readBoundedStringList(buf, MAX_METADATA_LIST_SIZE),
				buf.readUtf(MAX_VERSION_LENGTH),
				buf.readUtf(MAX_VERSION_LENGTH));
		}

		private CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("Role", role);
			tag.putString("Note", note);
			tag.putInt("PreferredPartySize", preferredPartySize);
			tag.put("Warnings", stringList(warnings));
			tag.put("Requires", stringList(requires));
			tag.putString("MinecraftVersion", minecraftVersion);
			tag.putString("AttunedVersion", attunedVersion);
			return tag;
		}

		private static SetupMetadata fromTag(CompoundTag tag) {
			if (tag == null || tag.isEmpty()) {
				return EMPTY;
			}
			return new SetupMetadata(
				tag.getString("Role"),
				tag.getString("Note"),
				tag.getInt("PreferredPartySize"),
				strings(tag.getList("Warnings", Tag.TAG_STRING)),
				strings(tag.getList("Requires", Tag.TAG_STRING)),
				tag.getString("MinecraftVersion"),
				tag.getString("AttunedVersion"));
		}

		private static ListTag stringList(List<String> values) {
			ListTag list = new ListTag();
			for (String value : values) {
				list.add(StringTag.valueOf(value));
			}
			return list;
		}

		private static List<String> strings(ListTag list) {
			List<String> values = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				values.add(list.getString(i));
			}
			return values;
		}
	}
}
