package net.fabricmc.fabric.api.entity.event.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingAttackEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ServerLivingEntityEvents {
	public static final AllowDamage ALLOW_DAMAGE = new AllowDamage();
	public static final AfterDamage AFTER_DAMAGE = new AfterDamage();
	public static final AllowDeath ALLOW_DEATH = new AllowDeath();
	public static final AfterDeath AFTER_DEATH = new AfterDeath();

	private ServerLivingEntityEvents() {}

	public static final class AllowDamage {
		private final List<AllowDamageCallback> callbacks = new ArrayList<>();

		private AllowDamage() {
			NeoForge.EVENT_BUS.addListener((LivingAttackEvent event) -> {
				for (AllowDamageCallback callback : List.copyOf(callbacks)) {
					if (!callback.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
						event.setCanceled(true);
						return;
					}
				}
			});
		}

		public void register(AllowDamageCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class AfterDamage {
		private final List<AfterDamageCallback> callbacks = new ArrayList<>();

		private AfterDamage() {
			NeoForge.EVENT_BUS.addListener((LivingDamageEvent event) -> {
				for (AfterDamageCallback callback : List.copyOf(callbacks)) {
					callback.afterDamage(event.getEntity(), event.getSource(),
						event.getAmount(), event.getAmount(), false);
				}
			});
		}

		public void register(AfterDamageCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class AllowDeath {
		private final List<AllowDeathCallback> callbacks = new ArrayList<>();

		private AllowDeath() {
			NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
				for (AllowDeathCallback callback : List.copyOf(callbacks)) {
					if (!callback.allowDeath(event.getEntity(), event.getSource(), 0.0F)) {
						event.setCanceled(true);
						return;
					}
				}
			});
		}

		public void register(AllowDeathCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	public static final class AfterDeath {
		private final List<AfterDeathCallback> callbacks = new ArrayList<>();

		private AfterDeath() {
			NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
				if (event.isCanceled()) {
					return;
				}
				for (AfterDeathCallback callback : List.copyOf(callbacks)) {
					callback.afterDeath(event.getEntity(), event.getSource());
				}
			});
		}

		public void register(AfterDeathCallback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface AllowDamageCallback {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface AfterDamageCallback {
		void afterDamage(LivingEntity entity, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked);
	}

	@FunctionalInterface
	public interface AllowDeathCallback {
		boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface AfterDeathCallback {
		void afterDeath(LivingEntity entity, DamageSource source);
	}
}
