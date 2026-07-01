package dev.attuned;

/**
 * Duck-typing accessor implemented by {@code ThrownTrident} (via mixin) so the client renderer can
 * tell that an in-flight/landed trident is a temporary Offshore Harpoon.
 *
 * <p>The marker lives in the projectile's pickup {@code ItemStack}, which is server-only state
 * persisted to NBT and never synced to clients. Mirroring how vanilla syncs render-relevant trident
 * data ({@code ID_FOIL}, {@code ID_LOYALTY}) through {@code SynchedEntityData}, the harpoon flag is
 * synced the same way and read here on the client.</p>
 */
public interface AttunedThrownHarpoonEntity {
	boolean attuned$isTemporaryHarpoon();
}
