package dev.attuned.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.menu.SavePresetPayload;
import dev.attuned.network.AbilityPayload;
import dev.attuned.network.CircleSnapshotPayload;
import org.junit.jupiter.api.Test;

final class AttunedPayloadKeyContractTest {
	@Test
	void payloadKeysDeclareStableIdsAndDirections() {
		assertEquals("attuned:ability", AttunedPayloadKey.ABILITY.id().toString());
		assertEquals(AttunedPayloadKey.Direction.SERVERBOUND, AttunedPayloadKey.ABILITY.direction());

		assertEquals("attuned:save_preset", AttunedPayloadKey.SAVE_PRESET.id().toString());
		assertEquals(AttunedPayloadKey.Direction.SERVERBOUND, AttunedPayloadKey.SAVE_PRESET.direction());

		assertEquals("attuned:circle_snapshot", AttunedPayloadKey.CIRCLE_SNAPSHOT.id().toString());
		assertEquals(AttunedPayloadKey.Direction.CLIENTBOUND, AttunedPayloadKey.CIRCLE_SNAPSHOT.direction());
	}

	@Test
	void payloadTypesUseSharedKeys() {
		assertEquals(AttunedPayloadKey.ABILITY.id(), AbilityPayload.TYPE.id());
		assertEquals(AttunedPayloadKey.SAVE_PRESET.id(), SavePresetPayload.TYPE.id());
		assertEquals(AttunedPayloadKey.CIRCLE_SNAPSHOT.id(), CircleSnapshotPayload.TYPE.id());
	}
}
