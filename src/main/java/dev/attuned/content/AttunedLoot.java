package dev.attuned.content;

import dev.attuned.AttunedConfig;
import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Seeds the Foci into survival as exploration rewards: a chance at one Focus is
 * injected into a spread of vanilla loot tables.
 *
 * <p>Each targeted table has a {@link Tier} — deeper, more dangerous structures
 * yield Foci more often — and an affinity {@link Drop#theme theme} that weights
 * its pool toward Foci of one affinity, so where you explore softly steers which
 * Foci you find. The base drop chance is the {@code focus_loot_chance} config
 * value; each tier scales it.
 *
 * <p>Compatibility note: this modifies loot tables, not chest block entities.
 * Per-player loot mods such as Lootr resolve those same tables, so every shipped
 * Focus and shard fragment remains available through their containers.
 */
public final class AttunedLoot {
	private static boolean initialized;

	private AttunedLoot() {}

	/** How rich a reward source's Focus drop is — a multiplier on the configured base chance. */
	enum Tier {
		LOW(0.35F),
		COMMON(0.7F),
		RICH(1.0F),
		TREASURE(1.8F);

		final float multiplier;

		Tier(float multiplier) {
			this.multiplier = multiplier;
		}
	}

	/** A targeted loot table: how rich it is, which affinity and faction it favours. */
	record Drop(Tier tier, Affinity theme, boolean unseenTheme, boolean fishingTheme) {
		Drop {
			tier = Objects.requireNonNull(tier, "tier");
		}
	}

	// Pool weights: a Focus matching the structure's theme is favoured, neutral Foci
	// fit anywhere, and off-theme Foci are still possible but rarer.
	private static final int WEIGHT_ON_THEME = 3;
	private static final int WEIGHT_NEUTRAL = 2;
	private static final int WEIGHT_OFF_THEME = 1;
	private static final int WEIGHT_UNSEEN_THEME_BONUS = 3;
	private static final int WEIGHT_SEAFARERS_FISHING_BONUS = 3;
	private static final ResourceLocation UNSEEN_FACTION =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "unseen");
	private static final ResourceLocation SEAFARERS_FACTION =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "seafarers");

	private record FocusMeta(Affinity affinity, ResourceLocation faction) {}
	private record FocusEntry(Item item, FocusMeta meta) {}

	/** Vanilla loot tables that gain a chance at a Focus. Grouped by source family. */
	private static final Map<ResourceLocation, Drop> TARGETS = Map.ofEntries(
		// Structure chests — common exploration, modest odds.
		Map.entry(chest("simple_dungeon"), normal(Tier.COMMON, null)),
		Map.entry(chest("abandoned_mineshaft"), unseen(Tier.COMMON, null)),
		Map.entry(chest("igloo_chest"), normal(Tier.COMMON, null)),
		Map.entry(chest("village/village_weaponsmith"), normal(Tier.COMMON, Affinity.FURY)),
		Map.entry(chest("village/village_armorer"), normal(Tier.COMMON, Affinity.BASTION)),
		Map.entry(chest("village/village_toolsmith"), normal(Tier.COMMON, null)),
		Map.entry(chest("village/village_temple"), normal(Tier.COMMON, Affinity.HOLY)),
		Map.entry(chest("shipwreck_treasure"), unseen(Tier.COMMON, Affinity.ZEPHYR)),
		// Structure chests — riskier places, better odds.
		Map.entry(chest("jungle_temple"), normal(Tier.RICH, Affinity.ZEPHYR)),
		Map.entry(chest("desert_pyramid"), unseen(Tier.RICH, null)),
		Map.entry(chest("buried_treasure"), normal(Tier.RICH, Affinity.ZEPHYR)),
		Map.entry(chest("stronghold_corridor"), unseen(Tier.RICH, Affinity.BASTION)),
		Map.entry(chest("stronghold_crossing"), unseen(Tier.RICH, Affinity.BASTION)),
		Map.entry(chest("nether_bridge"), normal(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("pillager_outpost"), unseen(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("ruined_portal"), unseen(Tier.RICH, null)),
		Map.entry(chest("bastion_other"), normal(Tier.RICH, Affinity.BASTION)),
		// Structure chests — the deep and dangerous, the best odds.
		Map.entry(chest("woodland_mansion"), unseen(Tier.TREASURE, Affinity.FURY)),
		Map.entry(chest("ancient_city"), unseen(Tier.TREASURE, Affinity.BASTION)),
		Map.entry(chest("end_city_treasure"), unseen(Tier.TREASURE, Affinity.ZEPHYR)),
		Map.entry(chest("bastion_treasure"), normal(Tier.TREASURE, Affinity.BASTION)),
		// Repeatable treasure - pleasant finds, deliberately low odds.
		Map.entry(vanilla("gameplay/fishing/treasure"), fishing(Tier.LOW)),
		// Archaeology - quiet exploration, with Unseen-leaning ruins and desert sites.
		Map.entry(vanilla("archaeology/desert_pyramid"), unseen(Tier.RICH, null)),
		Map.entry(vanilla("archaeology/desert_well"), unseen(Tier.COMMON, null)),
		Map.entry(vanilla("archaeology/trail_ruins_common"), unseen(Tier.LOW, null)),
		Map.entry(vanilla("archaeology/trail_ruins_rare"), unseen(Tier.COMMON, Affinity.ZEPHYR)),
		Map.entry(vanilla("archaeology/ocean_ruin_warm"), normal(Tier.COMMON, Affinity.ZEPHYR)),
		Map.entry(vanilla("archaeology/ocean_ruin_cold"), normal(Tier.COMMON, Affinity.ZEPHYR)),
		// Trial reward children only. The parent reward tables delegate to these.
		Map.entry(chest("trial_chambers/reward_common"), normal(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("trial_chambers/reward_rare"), normal(Tier.TREASURE, Affinity.BASTION)),
		Map.entry(chest("trial_chambers/reward_unique"), normal(Tier.TREASURE, Affinity.HOLY)),
		Map.entry(chest("trial_chambers/reward_ominous_common"), unseen(Tier.RICH, Affinity.FURY)),
		Map.entry(chest("trial_chambers/reward_ominous_rare"), unseen(Tier.TREASURE, Affinity.FURY)),
		Map.entry(chest("trial_chambers/reward_ominous_unique"), unseen(Tier.TREASURE, null))
	);

	private static Drop normal(Tier tier, Affinity theme) {
		return new Drop(tier, theme, false, false);
	}

	private static Drop unseen(Tier tier, Affinity theme) {
		return new Drop(tier, theme, true, false);
	}

	private static Drop fishing(Tier tier) {
		return new Drop(tier, null, false, true);
	}

	private static ResourceLocation chest(String name) {
		return vanilla("chests/" + name);
	}

	private static ResourceLocation vanilla(String path) {
		return ResourceLocation.fromNamespaceAndPath("minecraft", path);
	}

	static Map<ResourceLocation, Drop> targetDrops() {
		return TARGETS;
	}

	static boolean modifiesExistingPools(ResourceLocation table) {
		return table.getNamespace().equals("minecraft") && table.getPath().startsWith("archaeology/");
	}

	/** Registers the loot-table injection. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			Drop drop = TARGETS.get(key.location());
			if (drop == null) {
				return;
			}
			AttunedConfig config = AttunedConfig.get();
			float chance = focusChance(config, drop);
			float fragmentChance = fragmentChance(config, chance);
			List<FocusEntry> focusEntries = focusEntries(
				registries.lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS));
			if (modifiesExistingPools(key.location())) {
				((FabricLootTableBuilder) tableBuilder).modifyPools(pool -> {
					float focusEntryChance = chance / Math.max(1, focusEntries.size());
					for (FocusEntry focus : focusEntries) {
						pool.add(LootItem.lootTableItem(focus.item())
							.setWeight(weightFor(focus, drop))
							.when(LootItemRandomChanceCondition.randomChance(focusEntryChance)));
					}
					pool.add(LootItem.lootTableItem(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)
						.setWeight(WEIGHT_NEUTRAL)
						.when(LootItemRandomChanceCondition.randomChance(fragmentChance)));
				});
				return;
			}
			LootPool.Builder pool = LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(chance));
			for (FocusEntry focus : focusEntries) {
				pool.add(LootItem.lootTableItem(focus.item()).setWeight(weightFor(focus, drop)));
			}
			tableBuilder.withPool(pool);

			tableBuilder.withPool(LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1.0F))
				.when(LootItemRandomChanceCondition.randomChance(fragmentChance))
				.add(LootItem.lootTableItem(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)));
		});
	}

	static float focusChance(AttunedConfig config, Drop drop) {
		return Mth.clamp(
			config.focusLootChance() * drop.tier().multiplier * lootMultiplier(config, drop.tier()),
			0.0F,
			1.0F);
	}

	static float fragmentChance(AttunedConfig config, float focusChance) {
		return Mth.clamp(focusChance * 2.0F * config.shardFragmentLootMultiplier(), 0.0F, 1.0F);
	}

	private static float lootMultiplier(AttunedConfig config, Tier tier) {
		return switch (tier) {
			case LOW -> config.lowLootMultiplier();
			case COMMON -> config.commonLootMultiplier();
			case RICH -> config.richLootMultiplier();
			case TREASURE -> config.treasureLootMultiplier();
		};
	}

	/** Affinities and factions from the synced datapack registry, keyed by Focus item. */
	private static List<FocusEntry> focusEntries(HolderLookup.RegistryLookup<FocusDefinition> lookup) {
		return lookup.listElements()
			.map(holder -> {
				FocusDefinition definition = holder.value();
				return new FocusEntry(definition.item().value(), new FocusMeta(
					definition.affinity().orElse(null),
					definition.faction().orElse(null)));
			})
			.sorted(Comparator.comparing(focus ->
				BuiltInRegistries.ITEM.getKey(focus.item()).toString()))
			.toList();
	}

	/** The pool weight for a Focus in a structure with the given affinity theme. */
	private static int weightFor(FocusEntry focus, Drop drop) {
		return weightForMeta(focus.meta().affinity(), focus.meta().faction(), drop);
	}

	static int weightForMeta(Affinity affinity, ResourceLocation faction, Drop drop) {
		int weight;
		if (affinity == null) {
			weight = WEIGHT_NEUTRAL;
		} else if (drop.theme() != null && affinity == drop.theme()) {
			weight = WEIGHT_ON_THEME;
		} else {
			weight = WEIGHT_OFF_THEME;
		}
		if (drop.unseenTheme() && UNSEEN_FACTION.equals(faction)) {
			weight += WEIGHT_UNSEEN_THEME_BONUS;
		}
		if (drop.fishingTheme() && SEAFARERS_FACTION.equals(faction)) {
			weight += WEIGHT_SEAFARERS_FISHING_BONUS;
		}
		return weight;
	}
}
