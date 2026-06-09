package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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

	public static final StreamCodec<RegistryFriendlyByteBuf, FocusPreset> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, FocusPreset::name,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FocusPreset::slots,
			FocusPreset::new).cast();

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
}
