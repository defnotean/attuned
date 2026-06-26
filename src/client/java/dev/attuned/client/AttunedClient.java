package dev.attuned.client;

import dev.attuned.client.hud.CombatHud;
import dev.attuned.client.hud.FociHud;
import dev.attuned.client.screen.AltarScreens;
import dev.attuned.client.screen.AttunementJournalScreen;

public final class AttunedClient {
	private AttunedClient() {}

	public static void init() {
		AttunedClientConfig.load();
		AttunedTooltips.init();
		AttunedKeybinds.init();
		FocusAbilityClientState.init();
		AttunementStateClient.init();
		AffinityInspectClient.init();
		UpdraftLiftClient.init();
		FociHud.init();
		CombatHud.init();
		AltarScreens.init();
		AttunementJournalScreen.initNetworking();
		TremorOreOutlines.init();
	}
}
