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
public record FocusPreset(String name, List<String> slots) {
	private static final int MAX_NAME_LENGTH = 32;
	private static final String DEFAULT_NAME = "Preset";

	public FocusPreset {
		name = normalizeName(name);
		slots = normalizeSlots(slots);
	}

	public static final Codec<FocusPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("name").forGetter(FocusPreset::name),
		Codec.STRING.listOf().fieldOf("slots").forGetter(FocusPreset::slots)
	).apply(instance, FocusPreset::new));

	private static String normalizeName(String raw) {
		String normalized = raw == null ? "" : raw.trim();
		if (normalized.isEmpty()) {
			normalized = DEFAULT_NAME;
		}
		return normalized.length() > MAX_NAME_LENGTH ? normalized.substring(0, MAX_NAME_LENGTH) : normalized;
	}

	private static List<String> normalizeSlots(List<String> source) {
		List<String> normalized = new ArrayList<>(AttunedInv.SIZE);
		int sourceSize = source == null ? 0 : source.size();
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			String id = i < sourceSize ? source.get(i) : "";
			normalized.add(id == null ? "" : id.trim());
		}
		return List.copyOf(normalized);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
		buf.writeCollection(slots, FriendlyByteBuf::writeUtf);
	}

	public static FocusPreset read(FriendlyByteBuf buf) {
		return new FocusPreset(buf.readUtf(MAX_NAME_LENGTH), buf.readList(FriendlyByteBuf::readUtf));
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", name);
		ListTag list = new ListTag();
		for (String slot : slots) {
			list.add(StringTag.valueOf(slot));
		}
		tag.put("Slots", list);
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
		return new FocusPreset(tag == null ? DEFAULT_NAME : tag.getString("Name"), decoded);
	}
}
