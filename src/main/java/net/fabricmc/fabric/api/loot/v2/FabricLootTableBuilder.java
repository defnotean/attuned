package net.fabricmc.fabric.api.loot.v2;

import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootPool;

public interface FabricLootTableBuilder {
	void modifyPools(Consumer<LootPool.Builder> modifier);

	default void withPool(LootPool.Builder pool) {
	}
}
