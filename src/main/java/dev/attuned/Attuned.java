package dev.attuned;

import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.synergy.SynergyDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.combat.Apex;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.GravebindSave;
import dev.attuned.combat.ResonantSurges;
import dev.attuned.combat.RevenantCombat;
import dev.attuned.combat.Resonance;
import dev.attuned.combat.UnseenCombat;
import dev.attuned.command.AttunedCommands;
import dev.attuned.content.AltarAnimations;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.AttunedLoot;
import dev.attuned.content.behavior.FactionSetBonuses;
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
import dev.attuned.pacts.PactTrials;
import dev.attuned.pacts.Pacts;
import dev.attuned.platform.NeoForgeDeferredRegistries;
import dev.attuned.platform.NeoForgeEventBuses;
import dev.attuned.synergy.Synergies;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Attuned.MOD_ID)
public class Attuned {
	public static final String MOD_ID = "attuned";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public Attuned(IEventBus modEventBus) {
		NeoForgeEventBuses.setModEventBus(modEventBus);
		DynamicRegistries.setModEventBus(modEventBus);
		PayloadTypeRegistry.setModEventBus(modEventBus);
		NeoForgeDeferredRegistries.register(modEventBus);
		init();
		initClientWhenPresent();
	}

	public static void init() {
		AttunedConfig.load();
		DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_DEFINITIONS, FocusDefinition.CODEC);
		DynamicRegistries.registerSynced(AttunedRegistries.SYNERGY_DEFINITIONS, SynergyDefinition.CODEC);
		DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_BEHAVIORS, FocusBehaviorDef.CODEC);
		AttunedAttachments.init();
		AttunedPlayerCleanup.init();
		AttunedServerCleanup.init();
		// Register generic Focus teardown before Focus behaviors add fallback cleanup callbacks.
		AttunedEffects.init();
		FactionSetBonuses.init();
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
		ResonantSurges.init();
		Pacts.init();
		PactTrials.init();
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
		PayloadTypeRegistry.buildForgeChannel();
		GravebindSave.init();
		Milestones.init();
		Onboarding.init();
		LOGGER.info("Attuned initializing");
	}

	private static void initClientWhenPresent() {
		if (FMLEnvironment.getDist() != Dist.CLIENT) {
			return;
		}
		try {
			Class.forName("dev.attuned.client.AttunedClient")
				.getMethod("init")
				.invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to initialize Attuned client", e);
		}
	}
}
