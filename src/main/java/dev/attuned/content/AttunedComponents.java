package dev.attuned.content;

import com.mojang.serialization.Codec;
import dev.attuned.Attuned;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.platform.NeoForgeDeferredRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

/** Registers custom item data components used by Attuned items. */
public final class AttunedComponents {
	private static boolean initialized;

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
		SATCHEL_CONTENTS = NeoForgeDeferredRegistries.dataComponent(
			new ResourceLocation(Attuned.MOD_ID, "satchel_contents"),
			DataComponentType.<FocusHolder>builder()
				.persistent(FocusHolder.codec(SATCHEL_SIZE, 1))
				.networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))
				.build());
		GRAND_SATCHEL_CONTENTS = NeoForgeDeferredRegistries.dataComponent(
			new ResourceLocation(Attuned.MOD_ID, "grand_satchel_contents"),
			DataComponentType.<FocusHolder>builder()
				.persistent(FocusHolder.codec(GRAND_SATCHEL_SIZE, 1))
				.networkSynchronized(FocusHolder.streamCodec(GRAND_SATCHEL_SIZE, 1))
				.build());
		TEMPERED = NeoForgeDeferredRegistries.dataComponent(
			new ResourceLocation(Attuned.MOD_ID, "tempered"),
			DataComponentType.<Unit>builder()
				.persistent(Codec.unit(Unit.INSTANCE))
				.networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
				.build());
	}
}
