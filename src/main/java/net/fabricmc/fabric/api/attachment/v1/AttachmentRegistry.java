package net.fabricmc.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class AttachmentRegistry {
	private AttachmentRegistry() {}

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	public static <T> AttachmentType<T> create(ResourceLocation id, Consumer<Builder<T>> consumer) {
		Builder<T> builder = new Builder<>();
		consumer.accept(builder);
		return new AttachmentType<>(id, builder.initializer, builder.copyOnDeath);
	}

	public static final class Builder<T> {
		private Supplier<T> initializer = () -> null;
		private boolean copyOnDeath;

		public Builder<T> initializer(Supplier<T> initializer) {
			this.initializer = Objects.requireNonNull(initializer, "initializer");
			return this;
		}

		public Builder<T> persistent(Codec<T> codec) {
			return this;
		}

		public <B> Builder<T> syncWith(StreamCodec<B, T> codec, AttachmentSyncPredicate predicate) {
			return this;
		}

		public Builder<T> copyOnDeath() {
			this.copyOnDeath = true;
			return this;
		}

		public AttachmentType<T> buildAndRegister(ResourceLocation id) {
			return new AttachmentType<>(id, initializer, copyOnDeath);
		}
	}
}
