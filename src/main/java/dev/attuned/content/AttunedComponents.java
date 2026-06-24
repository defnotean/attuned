package dev.attuned.content;

import com.mojang.serialization.Codec;
import dev.attuned.Attuned;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.platform.ForgeRegistration;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

/** Registers custom item data components used by Attuned items. */
public final class AttunedComponents {
	private static boolean initialized;
	private static final Codec<Unit> UNIT_CODEC = Codec.unit(Unit.INSTANCE);
	private static final StreamCodec<RegistryFriendlyByteBuf, Unit> UNIT_STREAM_CODEC =
		StreamCodec.unit(Unit.INSTANCE);

	private AttunedComponents() {}

	public static final int SATCHEL_SIZE = 27;
	/** Grand Focus Reliquary capacity: a 9x6 Foci grid, twice the small satchel. */
	public static final int GRAND_SATCHEL_SIZE = 54;

	public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;

	/** Contents of the second-tier Grand Focus Reliquary (a wider FocusHolder). */
	public static DataComponentType<FocusHolder> GRAND_SATCHEL_CONTENTS;

	/** Marker on a Focus that has been tempered at the Altar of Reweaving. */
	public static DataComponentType<Unit> TEMPERED;

	public static FocusHolder emptyContents() {
		return FocusHolder.empty(SATCHEL_SIZE, 1);
	}

	public static FocusHolder emptyGrandContents() {
		return FocusHolder.empty(GRAND_SATCHEL_SIZE, 1);
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		SATCHEL_CONTENTS = ForgeRegistration.dataComponent("satchel_contents",
			DataComponentType.<FocusHolder>builder()
				.persistent(FocusHolder.codec(SATCHEL_SIZE, 1))
				.networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))
				.build());
		GRAND_SATCHEL_CONTENTS = ForgeRegistration.dataComponent("grand_satchel_contents",
			DataComponentType.<FocusHolder>builder()
				.persistent(FocusHolder.codec(GRAND_SATCHEL_SIZE, 1))
				.networkSynchronized(FocusHolder.streamCodec(GRAND_SATCHEL_SIZE, 1))
				.build());
		TEMPERED = ForgeRegistration.dataComponent("tempered",
			DataComponentType.<Unit>builder()
				.persistent(UNIT_CODEC)
				.networkSynchronized(UNIT_STREAM_CODEC)
				.build());
	}
}
