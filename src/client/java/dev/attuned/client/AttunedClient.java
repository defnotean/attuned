package dev.attuned.client;

import dev.attuned.client.hud.CombatHud;
import dev.attuned.client.hud.FociHud;
import dev.attuned.client.hud.PartyHud;
import dev.attuned.client.screen.AltarScreens;
import dev.attuned.client.screen.AttunementJournalScreen;
import net.fabricmc.api.ClientModInitializer;

public class AttunedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AttunedClientConfig.load();
		AttunedTooltips.init();
		AttunedKeybinds.init();
		FocusAbilityClientState.init();
		CircleClientState.init();
		AffinityInspectClient.init();
		UpdraftLiftClient.init();
		PartyHud.init();
		FociHud.init();
		CombatHud.init();
		AltarScreens.init();
		AttunementJournalScreen.initNetworking();
		TremorOreOutlines.init();
	}
}
