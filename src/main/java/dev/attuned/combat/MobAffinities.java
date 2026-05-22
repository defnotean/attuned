package dev.attuned.combat;

import dev.attuned.api.focus.Affinity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;

/**
 * Assigns hostile {@link EntityType}s an {@link Affinity} so they participate in
 * the rock-paper-scissors counter-combat cycle alongside attuned players.
 *
 * <p>The mapping follows combat archetype rather than mob lore:
 * <ul>
 *   <li>{@link Affinity#FURY} — aggressive melee bruisers (zombies, illager
 *       axemen, ravager).</li>
 *   <li>{@link Affinity#BASTION} — durable, heavily-armoured threats (warden,
 *       hoglin, piglin brute).</li>
 *   <li>{@link Affinity#ZEPHYR} — ranged or fast skirmishers (skeletons,
 *       phantom, spider, blaze, breeze).</li>
 * </ul>
 * Any entity type not listed has no affinity and takes/deals normal damage.
 */
public final class MobAffinities {
	private MobAffinities() {}

	private static final Map<EntityType<?>, Affinity> AFFINITIES = buildMap();

	private static Map<EntityType<?>, Affinity> buildMap() {
		// Aggressive melee mobs.
		// Husk/Drowned/ZombieVillager are Zombie subclasses but distinct
		// EntityTypes, so each must be listed explicitly.
		Map<EntityType<?>, Affinity> map = new java.util.IdentityHashMap<>();
		map.put(EntityType.ZOMBIE, Affinity.FURY);
		map.put(EntityType.HUSK, Affinity.FURY);
		map.put(EntityType.DROWNED, Affinity.FURY);
		map.put(EntityType.ZOMBIE_VILLAGER, Affinity.FURY);
		map.put(EntityType.ZOMBIFIED_PIGLIN, Affinity.FURY);
		map.put(EntityType.ZOGLIN, Affinity.FURY);
		map.put(EntityType.RAVAGER, Affinity.FURY);
		map.put(EntityType.VINDICATOR, Affinity.FURY);

		// Durable / heavy mobs.
		map.put(EntityType.WARDEN, Affinity.BASTION);
		map.put(EntityType.HOGLIN, Affinity.BASTION);
		map.put(EntityType.PIGLIN_BRUTE, Affinity.BASTION);
		// Iron golem is the canonical heavy bruiser; included so a golem-vs-mob
		// or golem-vs-player exchange respects the cycle.
		map.put(EntityType.IRON_GOLEM, Affinity.BASTION);

		// Ranged / fast mobs.
		map.put(EntityType.SKELETON, Affinity.ZEPHYR);
		map.put(EntityType.STRAY, Affinity.ZEPHYR);
		map.put(EntityType.BOGGED, Affinity.ZEPHYR);
		map.put(EntityType.WITHER_SKELETON, Affinity.ZEPHYR);
		map.put(EntityType.PHANTOM, Affinity.ZEPHYR);
		map.put(EntityType.SPIDER, Affinity.ZEPHYR);
		map.put(EntityType.CAVE_SPIDER, Affinity.ZEPHYR);
		map.put(EntityType.BLAZE, Affinity.ZEPHYR);
		map.put(EntityType.BREEZE, Affinity.ZEPHYR);
		map.put(EntityType.PILLAGER, Affinity.ZEPHYR);
		return java.util.Collections.unmodifiableMap(map);
	}

	/** The combat affinity of a living entity by its {@link EntityType}, if mapped. */
	public static Optional<Affinity> of(LivingEntity entity) {
		if (entity == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(AFFINITIES.get(entity.getType()));
	}
}
