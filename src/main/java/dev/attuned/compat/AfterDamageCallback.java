package dev.attuned.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface AfterDamageCallback {
	Event<AfterDamageCallback> EVENT = EventFactory.createArrayBacked(
		AfterDamageCallback.class,
		callbacks -> (defender, source, originalDamage, dealtDamage, blocked) -> {
			for (AfterDamageCallback callback : callbacks) {
				callback.afterDamage(defender, source, originalDamage, dealtDamage, blocked);
			}
		});

	void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked);
}
