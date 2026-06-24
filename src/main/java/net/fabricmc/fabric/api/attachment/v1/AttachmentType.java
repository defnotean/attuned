package net.fabricmc.fabric.api.attachment.v1;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class AttachmentType<T> {
	private final ResourceLocation id;
	private final Supplier<T> initializer;
	private final boolean copyOnDeath;

	AttachmentType(ResourceLocation id, Supplier<T> initializer, boolean copyOnDeath) {
		this.id = Objects.requireNonNull(id, "id");
		this.initializer = Objects.requireNonNull(initializer, "initializer");
		this.copyOnDeath = copyOnDeath;
	}

	public ResourceLocation id() {
		return id;
	}

	public T initialValue() {
		return initializer.get();
	}

	@SuppressWarnings("unchecked")
	public T cast(Object value) {
		return (T) value;
	}

	public boolean copyOnDeath() {
		return copyOnDeath;
	}
}
