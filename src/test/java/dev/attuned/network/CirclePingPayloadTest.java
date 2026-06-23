package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CirclePingPayloadTest {
	@Test
	void pingPayloadKeepsFiniteLocationCoordinates() {
		CirclePingPayload payload = new CirclePingPayload(1.25D, 64.0D, -9.5D);

		assertEquals(1.25D, payload.x());
		assertEquals(64.0D, payload.y());
		assertEquals(-9.5D, payload.z());
	}

	@Test
	void pingPayloadRejectsNonFiniteCoordinates() {
		assertThrows(IllegalArgumentException.class,
			() -> new CirclePingPayload(Double.NaN, 64.0D, 0.0D));
		assertThrows(IllegalArgumentException.class,
			() -> new CirclePingPayload(0.0D, Double.POSITIVE_INFINITY, 0.0D));
		assertThrows(IllegalArgumentException.class,
			() -> new CirclePingPayload(0.0D, 64.0D, Double.NEGATIVE_INFINITY));
	}
}
