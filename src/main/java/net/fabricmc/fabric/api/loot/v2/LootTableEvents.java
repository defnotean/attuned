package net.fabricmc.fabric.api.loot.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class LootTableEvents {
	public static final Modify MODIFY = new Modify();

	private LootTableEvents() {}

	public static final class Modify {
		private final List<Callback> callbacks = new ArrayList<>();

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void modify(ResourceManager resourceManager, Object lootManager,
			ResourceLocation id, FabricLootTableBuilder tableBuilder, Object source);
	}
}
