package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** 1.18 compatibility facade for the later Fabric living-entity events used by Attuned. */
public final class ServerLivingEntityEvents {
	public static final Event<AllowDamage> ALLOW_DAMAGE = EventFactory.createArrayBacked(
		AllowDamage.class,
		callbacks -> (entity, source, amount) -> {
			for (AllowDamage callback : callbacks) {
				if (!callback.allowDamage(entity, source, amount)) {
					return false;
				}
			}
			return true;
		});

	public static final Event<AllowDeath> ALLOW_DEATH = EventFactory.createArrayBacked(
		AllowDeath.class,
		callbacks -> (entity, source, amount) -> {
			for (AllowDeath callback : callbacks) {
				if (!callback.allowDeath(entity, source, amount)) {
					return false;
				}
			}
			return true;
		});

	public static final Event<AfterDeath> AFTER_DEATH = EventFactory.createArrayBacked(
		AfterDeath.class,
		callbacks -> (entity, source) -> {
			for (AfterDeath callback : callbacks) {
				callback.afterDeath(entity, source);
			}
		});

	private ServerLivingEntityEvents() {}

	@FunctionalInterface
	public interface AllowDamage {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface AllowDeath {
		boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface AfterDeath {
		void afterDeath(LivingEntity entity, DamageSource source);
	}
}
