package dev.attuned;

import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.synergy.SynergyDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.GravebindSave;
import dev.attuned.combat.RevenantCombat;
import dev.attuned.combat.Resonance;
import dev.attuned.combat.UnseenCombat;
import dev.attuned.command.AttunedCommands;
import dev.attuned.content.AltarAnimations;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.AttunedLoot;
import dev.attuned.effect.AttunedEffects;
import dev.attuned.menu.AltarMenuType;
import dev.attuned.menu.AltarNetworking;
import dev.attuned.menu.PresetNetworking;
import dev.attuned.menu.ReweavingMenuType;
import dev.attuned.menu.ReweavingNetworking;
import dev.attuned.menu.SatchelMenuType;
import dev.attuned.network.AttunedNetworking;
import dev.attuned.network.JournalNetworking;
import dev.attuned.onboarding.Onboarding;
import dev.attuned.pacts.PactDeathMessages;
import dev.attuned.pacts.Pacts;
import dev.attuned.synergy.Synergies;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Attuned implements ModInitializer {
	public static final String MOD_ID = "attuned";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		AttunedConfig.load();
		DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_DEFINITIONS, FocusDefinition.CODEC);
		DynamicRegistries.registerSynced(AttunedRegistries.SYNERGY_DEFINITIONS, SynergyDefinition.CODEC);
		AttunedAttachments.init();
		AttunedPlayerCleanup.init();
		AttunedServerCleanup.init();
		// Register generic Focus teardown before Focus behaviors add fallback cleanup callbacks.
		AttunedEffects.init();
		AttunedComponents.init();
		AttunedContent.init();
		AltarAnimations.init();
		AttunedLoot.init();
		AttunedCommands.init();
		AttunedCombat.init();
		UnseenCombat.init();
		RevenantCombat.init();
		Apex.init();
		Resonance.init();
		Pacts.init();
		Synergies.init();
		PactDeathMessages.init();
		AttunedNetworking.init();
		JournalNetworking.init();
		AltarMenuType.init();
		SatchelMenuType.init();
		AltarNetworking.init();
		PresetNetworking.init();
		ReweavingMenuType.init();
		ReweavingNetworking.init();
		GravebindSave.init();
		Milestones.init();
		Onboarding.init();
		LOGGER.info("Attuned initializing");
	}
}
