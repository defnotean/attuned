package dev.attuned;

import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.command.AttunedCommands;
import dev.attuned.content.AttunedContent;
import dev.attuned.effect.AttunedEffects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Attuned implements ModInitializer {
	public static final String MOD_ID = "attuned";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_DEFINITIONS, FocusDefinition.CODEC);
		AttunedAttachments.init();
		AttunedContent.init();
		AttunedEffects.init();
		AttunedCommands.init();
		AttunedCombat.init();
		LOGGER.info("Attuned initializing");
	}
}
