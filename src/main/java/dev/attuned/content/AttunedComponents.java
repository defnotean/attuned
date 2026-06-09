package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.attunement.FocusHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registers custom item data components used by Attuned items. */
public final class AttunedComponents {
	private static boolean initialized;

	private AttunedComponents() {}

	public static final int SATCHEL_SIZE = 27;

	public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;

	public static FocusHolder emptyContents() {
		return FocusHolder.empty(SATCHEL_SIZE, 1);
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		SATCHEL_CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "satchel_contents"),
			DataComponentType.<FocusHolder>builder()
				.persistent(FocusHolder.codec(SATCHEL_SIZE, 1))
				.networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))
				.build());
	}
}
