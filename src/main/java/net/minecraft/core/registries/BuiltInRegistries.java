package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.effect.MobEffect;

/** 1.18 compatibility facade for the registry constants renamed in 1.19. */
public final class BuiltInRegistries {
	public static final Registry<Item> ITEM = Registry.ITEM;
	public static final Registry<Block> BLOCK = Registry.BLOCK;
	public static final Registry<EntityType<?>> ENTITY_TYPE = Registry.ENTITY_TYPE;
	public static final Registry<MobEffect> MOB_EFFECT = Registry.MOB_EFFECT;
	public static final Registry<Attribute> ATTRIBUTE = Registry.ATTRIBUTE;
	public static final Registry<MenuType<?>> MENU = Registry.MENU;

	private BuiltInRegistries() {}
}
