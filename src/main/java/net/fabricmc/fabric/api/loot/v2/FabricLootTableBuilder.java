package net.fabricmc.fabric.api.loot.v2;

import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootPool;

public interface FabricLootTableBuilder {
	void withPool(LootPool.Builder pool);

	default void modifyPools(Consumer<LootPool.Builder> consumer) {
		LootPool.Builder pool = LootPool.lootPool();
		consumer.accept(pool);
		withPool(pool);
	}
}
