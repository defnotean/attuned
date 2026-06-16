package dev.attuned.pacts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Per-player Pact Trial counters and permanent Tier 4 completions. */
public record PactTrialProgress(Map<String, Integer> counters, List<String> tier4Completed) {
	public static final PactTrialProgress EMPTY = new PactTrialProgress(Map.of(), List.of());

	public PactTrialProgress {
		counters = counters == null || counters.isEmpty() ? Map.of() : Map.copyOf(counters);
		tier4Completed = tier4Completed == null ? List.of() : List.copyOf(tier4Completed);
	}

	public static final Codec<PactTrialProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of())
			.forGetter(PactTrialProgress::counters),
		Codec.STRING.listOf().optionalFieldOf("tier4_completed", List.of())
			.forGetter(PactTrialProgress::tier4Completed)
	).apply(instance, PactTrialProgress::new));

	public PactTrialProgress withCounter(String pactId, int value) {
		Map<String, Integer> updated = new HashMap<>(counters);
		updated.put(pactId, value);
		return new PactTrialProgress(updated, tier4Completed);
	}

	public PactTrialProgress withTier4Completed(String pactId) {
		if (tier4Completed.contains(pactId)) {
			return this;
		}
		List<String> updated = new java.util.ArrayList<>(tier4Completed);
		updated.add(pactId);
		return new PactTrialProgress(counters, List.copyOf(updated));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeMap(counters, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
		buf.writeCollection(tier4Completed, FriendlyByteBuf::writeUtf);
	}

	public static PactTrialProgress read(FriendlyByteBuf buf) {
		Map<String, Integer> counters = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
		List<String> completed = buf.readList(FriendlyByteBuf::readUtf);
		return new PactTrialProgress(counters, completed);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag countersTag = new CompoundTag();
		for (Map.Entry<String, Integer> entry : counters.entrySet()) {
			countersTag.putInt(entry.getKey(), entry.getValue());
		}
		tag.put("Counters", countersTag);
		ListTag completed = new ListTag();
		for (String id : tier4Completed) {
			completed.add(StringTag.valueOf(id));
		}
		tag.put("Tier4Completed", completed);
		return tag;
	}

	public static PactTrialProgress fromTag(CompoundTag tag) {
		if (tag == null) {
			return EMPTY;
		}
		Map<String, Integer> counters = new HashMap<>();
		CompoundTag countersTag = tag.getCompound("Counters");
		for (String key : countersTag.getAllKeys()) {
			counters.put(key, countersTag.getInt(key));
		}
		List<String> completed = new ArrayList<>();
		ListTag completedTag = tag.getList("Tier4Completed", Tag.TAG_STRING);
		for (int i = 0; i < completedTag.size(); i++) {
			completed.add(completedTag.getString(i));
		}
		return new PactTrialProgress(counters, completed);
	}
}
