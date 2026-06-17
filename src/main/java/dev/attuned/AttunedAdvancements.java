package dev.attuned;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Small helper for Attuned's code-awarded challenge advancements. The JSON uses
 * impossible criteria so these milestones only unlock when the corresponding
 * attunement event actually happens in code.
 */
public final class AttunedAdvancements {
	private AttunedAdvancements() {}

	private static final String CRITERION = "done";
	private static final String ROOT = "attunement/root";

	public static void award(ServerPlayer player, String path) {
		if (!ROOT.equals(path)) {
			awardSingle(player, ROOT);
		}
		awardSingle(player, path);
	}

	private static void awardSingle(ServerPlayer player, String path) {
		Advancement advancement = player.level().getServer().getAdvancements()
			.getAdvancement(new ResourceLocation(Attuned.MOD_ID, path));
		if (advancement != null) {
			player.getAdvancements().award(advancement, CRITERION);
		}
	}
}
