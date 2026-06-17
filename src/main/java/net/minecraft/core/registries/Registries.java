package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/** 1.18 compatibility facade for the registry keys renamed in 1.19. */
public final class Registries {
	public static final ResourceKey<Registry<Item>> ITEM = Registry.ITEM_REGISTRY;
	public static final ResourceKey<Registry<Block>> BLOCK = Registry.BLOCK_REGISTRY;
	public static final ResourceKey<Registry<Biome>> BIOME = Registry.BIOME_REGISTRY;
	public static final ResourceKey<Registry<EntityType<?>>> ENTITY_TYPE = Registry.ENTITY_TYPE_REGISTRY;

	private Registries() {}
}
