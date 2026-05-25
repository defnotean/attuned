package dev.attuned.content;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Seeds the Foci into survival as exploration rewards: a chance at one Focus is
 * injected into a spread of vanilla structure chest loot tables.
 *
 * <p>Each targeted table has a {@link Tier} — deeper, more dangerous structures
 * yield Foci more often — and an affinity {@link Drop#theme theme} that weights
 * its pool toward Foci of one affinity, so where you explore softly steers which
 * Foci you find. The base drop chance is the {@code focus_loot_chance} config
 * value; each tier scales it.
 */
public final class AttunedLoot {
	private AttunedLoot() {}

	/** How rich a structure's Focus drop is — a multiplier on the configured base chance. */
	private enum Tier {
		COMMON(0.7F),
		RICH(1.0F),
		TREASURE(1.8F);

		final float multiplier;

		Tier(float multiplier) {
			this.multiplier = multiplier;
		}
	}

	/** A targeted loot table: how rich it is, and which affinity it favours ({@code null} = neutral). */
	private record Drop(Tier tier, Affinity theme) {}

	// Pool weights: a Focus matching the structure's theme is favoured, neutral Foci
	// fit anywhere, and off-theme Foci are still possible but rarer.
	private static final int WEIGHT_ON_THEME = 3;
	private static final int WEIGHT_NEUTRAL = 2;
	private static final int WEIGHT_OFF_THEME = 1;

	/** Vanilla structure chest loot tables that gain a chance at a Focus. */
	private static final Map<Identifier, Drop> TARGETS = Map.ofEntries(
		// Common — found often, modest odds.
		Map.entry(chest("simple_dungeon"), new Drop(Tier.COMMON, null)),
		Map.entry(chest("abandoned_mineshaft"), new Drop(Tier.COMMON, null)),
		Map.entry(chest("igloo_chest"), new Drop(Tier.COMMON, null)),
		Map.entry(chest("village/village_weaponsmith"), new Drop(Tier.COMMON, Affinity.FURY)),
		Map.entry(chest("village/village_armorer"), new Drop(Tier.COMMON, Affinity.BASTION)),
		Map.entry(chest("village/village_toolsmith"), new Drop(Tier.COMMON, null)),
		Map.entry(chest("village/village_temple"), new Drop(Tier.COMMON, Affinity.ZEPHYR)),
		Map.entry(chest("shipwreck_treasure"), new Drop(Tier.COMMON, Affinity.ZEPHYR)),
		// Rich — riskier places, better odds.
		Map.entry(chest("jungle_temple"), new Drop(Tier.RICH, Affinity.ZEPHYR)),
		Map.entry(chest("desert_pyramid"), new Drop(Tier.RICH, null)),
		Map.entry(chest("buried_treasure"), new Drop(Tier.RICH, Affinity.ZEPHYR)),
		Map.entry(chest("stronghold_corridor"), new Drop(Tier.RICH, Affinity.BASTION)),
		Map.entry(chest("stronghold_crossing"), new Drop(Tier.RICH, Affinity.BASTION)),
		Map.entry(chest("nether_bridge"), new Drop(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("pillager_outpost"), new Drop(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("ruined_portal"), new Drop(Tier.RICH, null)),
		Map.entry(chest("bastion_other"), new Drop(Tier.RICH, Affinity.BASTION)),
		// Treasure — the deep and dangerous, the best odds.
		Map.entry(chest("woodland_mansion"), new Drop(Tier.TREASURE, Affinity.FURY)),
		Map.entry(chest("ancient_city"), new Drop(Tier.TREASURE, Affinity.BASTION)),
		Map.entry(chest("end_city_treasure"), new Drop(Tier.TREASURE, Affinity.ZEPHYR)),
		Map.entry(chest("bastion_treasure"), new Drop(Tier.TREASURE, Affinity.BASTION))
	);

	private static Identifier chest(String name) {
		return Identifier.fromNamespaceAndPath("minecraft", "chests/" + name);
	}

	/** Registers the loot-table injection. Called from the mod initializer. */
	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			Drop drop = TARGETS.get(key.identifier());
			if (drop == null) {
				return;
			}
			float chance = Mth.clamp(
				AttunedConfig.get().focusLootChance() * drop.tier().multiplier, 0.0F, 1.0F);
			LootPool.Builder pool = LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(chance));
			Map<Item, Affinity> focusAffinities = focusAffinities(
				registries.lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS));
			for (Item focus : AttunedContent.FOCI) {
				pool.add(LootItem.lootTableItem(focus).setWeight(weightFor(focus, drop.theme(), focusAffinities)));
			}
			tableBuilder.withPool(pool);

			float fragmentChance = Mth.clamp(chance * 2.0F, 0.0F, 1.0F);
			tableBuilder.withPool(LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(fragmentChance))
				.add(LootItem.lootTableItem(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)));
		});
	}

	/** Affinities from the synced datapack registry, keyed by Focus item. Neutral Foci are absent. */
	private static Map<Item, Affinity> focusAffinities(HolderLookup.RegistryLookup<FocusDefinition> lookup) {
		Map<Item, Affinity> affinities = new IdentityHashMap<>();
		lookup.listElements().forEach(holder -> {
			FocusDefinition definition = holder.value();
			definition.affinity().ifPresent(affinity -> affinities.put(definition.item().value(), affinity));
		});
		return affinities;
	}

	/** The pool weight for a Focus in a structure with the given affinity theme. */
	private static int weightFor(Item focus, Affinity theme, Map<Item, Affinity> focusAffinities) {
		Affinity affinity = focusAffinities.get(focus);
		if (affinity == null) {
			return WEIGHT_NEUTRAL;
		}
		if (theme != null && affinity == theme) {
			return WEIGHT_ON_THEME;
		}
		return WEIGHT_OFF_THEME;
	}
}
