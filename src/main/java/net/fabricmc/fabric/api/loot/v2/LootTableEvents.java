package net.fabricmc.fabric.api.loot.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

public final class LootTableEvents {
	public static final Modify MODIFY = new Modify();

	private LootTableEvents() {}

	public static final class Modify {
		private final List<Callback> callbacks = new ArrayList<>();

		private Modify() {
			NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> {
				ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, event.getName());
				FabricLootTableBuilder builder = new NeoForgeLootTableBuilder(event.getTable());
				for (Callback callback : List.copyOf(callbacks)) {
					callback.modify(key, builder, null);
				}
			});
		}

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void modify(ResourceKey<LootTable> key, FabricLootTableBuilder tableBuilder, Object source);
	}

	private record NeoForgeLootTableBuilder(LootTable table) implements FabricLootTableBuilder {
		@Override
		public void withPool(LootPool.Builder pool) {
			table.addPool(pool.build());
		}
	}
}
