package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleInvitePromptPayloadTest {
	private static final UUID INVITER = UUID.fromString("00000000-0000-0000-0000-000000000901");

	@Test
	void invitePromptSanitizesNamesAndClampsCounts() {
		CircleInvitePromptPayload prompt = new CircleInvitePromptPayload(INVITER,
			"\u00A7aKael\n", "  Ember\u00A7c Watch\t", -3, 0, 160L);

		assertEquals(INVITER, prompt.inviter());
		assertEquals("Kael", prompt.senderName());
		assertEquals("Ember Watch", prompt.circleName());
		assertEquals(0, prompt.partySize());
		assertEquals(1, prompt.maxMembers());
		assertEquals(160L, prompt.expiresAtTick());
	}

	@Test
	void invitePromptRejectsMissingInviter() {
		assertThrows(NullPointerException.class,
			() -> new CircleInvitePromptPayload(null, "Kael", "Circle", 1, 4, 160L));
	}
}
