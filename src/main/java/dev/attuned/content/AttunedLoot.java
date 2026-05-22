package dev.attuned.content;

import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Seeds the Foci into survival without making them craftable: a chance at one
 * random Focus is injected into a spread of structure chest loot tables, so the
 * accessories are something a player discovers by exploring.
 */
public final class AttunedLoot {
	private AttunedLoot() {}

	/** Roughly how often a targeted chest yields a Focus. */
	private static final float FOCUS_CHANCE = 0.25F;

	/** Vanilla structure chest loot tables that gain a chance at a Focus. */
	private static final Set<Identifier> TARGETS = Set.of(
		chest("simple_dungeon"),
		chest("abandoned_mineshaft"),
		chest("stronghold_corridor"),
		chest("stronghold_crossing"),
		chest("jungle_temple"),
		chest("desert_pyramid"),
		chest("woodland_mansion"),
		chest("ancient_city"),
		chest("end_city_treasure"),
		chest("bastion_treasure"),
		chest("nether_bridge"),
		chest("pillager_outpost")
	);

	private static Identifier chest(String name) {
		return Identifier.fromNamespaceAndPath("minecraft", "chests/" + name);
	}

	/** Registers the loot-table injection. Called from the mod initializer. */
	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!TARGETS.contains(key.identifier())) {
				return;
			}
			LootPool.Builder pool = LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(FOCUS_CHANCE));
			for (Item focus : AttunedContent.FOCI) {
				pool.add(LootItem.lootTableItem(focus));
			}
			tableBuilder.withPool(pool);
		});
	}
}
