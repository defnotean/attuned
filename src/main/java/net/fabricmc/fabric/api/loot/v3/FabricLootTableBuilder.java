package net.fabricmc.fabric.api.loot.v3;

import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootPool;

public interface FabricLootTableBuilder {
	default void modifyPools(Consumer<LootPool.Builder> consumer) {
	}
}
