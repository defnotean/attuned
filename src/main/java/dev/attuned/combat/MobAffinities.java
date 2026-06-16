package dev.attuned.combat;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Assigns hostile entity types an {@link Affinity} so they participate in the
 * rock-paper-scissors counter-combat cycle alongside attuned players.
 *
 * <p>Membership is datapack-driven: an entity type's affinity is whichever of
 * the eight {@code attuned:*_mobs} entity-type tags it belongs to, one tag per
 * affinity in the Wheel of Refusals. The tags shipped with the mod mix combat
 * archetype with thematic identity: Fury for aggressive undead bruisers,
 * Bastion for durable heavies, Zephyr for ranged or fast skirmishers, Holy for
 * uncanny anomalies, Tide for aquatic threats, Forge for fire and nether mobs,
 * Verdant for cave and swamp creatures, and Umbral for dark or End dwellers. A
 * datapack may retag freely. The tags are expected to be pairwise disjoint; the
 * {@link #of(LivingEntity)} lookup checks them in declaration order and returns
 * the first match. An untagged entity has no affinity and deals and takes
 * normal damage.
 *
 * <p>Tag membership is read through {@link net.minecraft.world.entity.EntityType#is(net.minecraft.tags.TagKey)}, which
 * is the non-deprecated path to the entity's {@code Holder<EntityType<?>>} in
 * MC 26.1.2.
 */
public final class MobAffinities {
	private MobAffinities() {}

	private static final TagKey<EntityType<?>> FURY_MOBS = tag("fury_mobs");
	private static final TagKey<EntityType<?>> BASTION_MOBS = tag("bastion_mobs");
	private static final TagKey<EntityType<?>> ZEPHYR_MOBS = tag("zephyr_mobs");
	private static final TagKey<EntityType<?>> HOLY_MOBS = tag("holy_mobs");
	private static final TagKey<EntityType<?>> TIDE_MOBS = tag("tide_mobs");
	private static final TagKey<EntityType<?>> FORGE_MOBS = tag("forge_mobs");
	private static final TagKey<EntityType<?>> VERDANT_MOBS = tag("verdant_mobs");
	private static final TagKey<EntityType<?>> UMBRAL_MOBS = tag("umbral_mobs");

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Attuned.MOD_ID, name));
	}

	/** The combat affinity of a living entity by its entity-type tag, if any. */
	public static Optional<Affinity> of(LivingEntity entity) {
		if (entity == null) {
			return Optional.empty();
		}
		if (entity.getType().is(FURY_MOBS)) {
			return Optional.of(Affinity.FURY);
		}
		if (entity.getType().is(BASTION_MOBS)) {
			return Optional.of(Affinity.BASTION);
		}
		if (entity.getType().is(ZEPHYR_MOBS)) {
			return Optional.of(Affinity.ZEPHYR);
		}
		if (entity.getType().is(HOLY_MOBS)) {
			return Optional.of(Affinity.HOLY);
		}
		if (entity.getType().is(TIDE_MOBS)) {
			return Optional.of(Affinity.TIDE);
		}
		if (entity.getType().is(FORGE_MOBS)) {
			return Optional.of(Affinity.FORGE);
		}
		if (entity.getType().is(VERDANT_MOBS)) {
			return Optional.of(Affinity.VERDANT);
		}
		if (entity.getType().is(UMBRAL_MOBS)) {
			return Optional.of(Affinity.UMBRAL);
		}
		return Optional.empty();
	}
}
