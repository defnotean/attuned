package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirclePingClientPayloadTest {
	private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000921");

	@Test
	void clientPingPayloadSanitizesSourceNameAndKeepsExpiry() {
		CirclePingClientPayload payload = new CirclePingClientPayload(SOURCE,
			"\u00A7bKael\n", 12.0D, 65.0D, -4.0D, 340L);

		assertEquals(SOURCE, payload.source());
		assertEquals("Kael", payload.sourceName());
		assertEquals(12.0D, payload.x());
		assertEquals(65.0D, payload.y());
		assertEquals(-4.0D, payload.z());
		assertEquals(340L, payload.expiresAtTick());
	}

	@Test
	void clientPingPayloadRejectsMissingSourceOrNonFiniteCoordinates() {
		assertThrows(NullPointerException.class,
			() -> new CirclePingClientPayload(null, "Kael", 0.0D, 64.0D, 0.0D, 340L));
		assertThrows(IllegalArgumentException.class,
			() -> new CirclePingClientPayload(SOURCE, "Kael", Double.NaN, 64.0D, 0.0D, 340L));
	}
}
