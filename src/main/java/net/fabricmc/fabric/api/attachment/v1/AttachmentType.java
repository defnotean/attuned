package net.fabricmc.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class AttachmentType<T> {
	private final ResourceLocation id;
	private final Supplier<T> initializer;
	private final boolean copyOnDeath;
	private final Codec<T> persistentCodec;

	AttachmentType(ResourceLocation id, Supplier<T> initializer, boolean copyOnDeath, Codec<T> persistentCodec) {
		this.id = Objects.requireNonNull(id, "id");
		this.initializer = Objects.requireNonNull(initializer, "initializer");
		this.copyOnDeath = copyOnDeath;
		this.persistentCodec = persistentCodec;
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

	public Codec<T> persistentCodec() {
		return persistentCodec;
	}
}
