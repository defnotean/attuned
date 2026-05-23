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
 * the three {@code attuned:*_mobs} entity-type tags it belongs to. The tags
 * shipped with the mod follow combat archetype rather than mob lore: Fury for
 * aggressive bruisers, Bastion for durable threats, Zephyr for ranged, fast or
 * flying skirmishers. A datapack may retag freely. An untagged entity has no
 * affinity and deals and takes normal damage.
 *
 * <p>Tag membership is read through {@link LivingEntity#typeHolder()}, which
 * is the non-deprecated path to the entity's {@code Holder<EntityType<?>>} in
 * MC 26.1.2.
 */
public final class MobAffinities {
	private MobAffinities() {}

	private static final TagKey<EntityType<?>> FURY_MOBS = tag("fury_mobs");
	private static final TagKey<EntityType<?>> BASTION_MOBS = tag("bastion_mobs");
	private static final TagKey<EntityType<?>> ZEPHYR_MOBS = tag("zephyr_mobs");

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Attuned.MOD_ID, name));
	}

	/** The combat affinity of a living entity by its entity-type tag, if any. */
	public static Optional<Affinity> of(LivingEntity entity) {
		if (entity == null) {
			return Optional.empty();
		}
		if (entity.typeHolder().is(FURY_MOBS)) {
			return Optional.of(Affinity.FURY);
		}
		if (entity.typeHolder().is(BASTION_MOBS)) {
			return Optional.of(Affinity.BASTION);
		}
		if (entity.typeHolder().is(ZEPHYR_MOBS)) {
			return Optional.of(Affinity.ZEPHYR);
		}
		return Optional.empty();
	}
}
