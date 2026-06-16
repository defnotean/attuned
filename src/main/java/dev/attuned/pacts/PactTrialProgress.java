package dev.attuned.pacts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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

	private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> COUNTERS_STREAM_CODEC =
		ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

	public static final StreamCodec<RegistryFriendlyByteBuf, PactTrialProgress> STREAM_CODEC =
		StreamCodec.composite(
			COUNTERS_STREAM_CODEC, PactTrialProgress::counters,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PactTrialProgress::tier4Completed,
			PactTrialProgress::new);

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
}
