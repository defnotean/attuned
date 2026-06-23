package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PresetPayloadSanitizationTest {
	@Test
	void saveDeleteAndImportPresetPayloadNamesArePlainSingleLineText() {
		String hostileName = "\n§cRaid\r\nWatch\t§l!";

		assertEquals("RaidWatch!", new SavePresetPayload(hostileName).name());
		assertEquals("RaidWatch!", new DeletePresetPayload(0, hostileName).name());
		assertEquals("RaidWatch!", new ImportPresetPayload(hostileName, List.of()).name());
	}
}
