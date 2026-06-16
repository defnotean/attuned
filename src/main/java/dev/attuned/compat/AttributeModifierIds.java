package dev.attuned.compat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class AttributeModifierIds {
	private AttributeModifierIds() {}

	public static UUID uuid(ResourceLocation id) {
		return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
	}

	public static String name(ResourceLocation id) {
		return id.toString();
	}
}
