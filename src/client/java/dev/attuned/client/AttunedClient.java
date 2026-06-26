package dev.attuned.client;

import dev.attuned.client.hud.CombatHud;
import dev.attuned.client.hud.FociHud;
import dev.attuned.client.hud.PartyHud;
import dev.attuned.client.render.BlockbenchMeshSpecialRenderer;
import dev.attuned.client.render.GltfMeshSpecialRenderer;
import dev.attuned.client.screen.AltarScreens;
import dev.attuned.client.screen.AttunementJournalScreen;

public final class AttunedClient {
	private AttunedClient() {}

	public static void init() {
		AttunedClientConfig.load();
		AttunedTooltips.init();
		AttunedKeybinds.init();
		AttunementStateClient.init();
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
		BlockbenchMeshSpecialRenderer.init();
		GltfMeshSpecialRenderer.init();
	}
}
