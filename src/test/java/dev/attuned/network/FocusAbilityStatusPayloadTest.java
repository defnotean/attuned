package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.attunement.AttunedInv;
import org.junit.jupiter.api.Test;

class FocusAbilityStatusPayloadTest {
	@Test
	void invalidSlotClearsCooldownState() {
		FocusAbilityStatusPayload payload = new FocusAbilityStatusPayload(AttunedInv.SIZE, 10, 20);

		assertEquals(FocusAbilityStatusPayload.NO_ABILITY_SLOT, payload.slot());
		assertEquals(0, payload.remainingTicks());
		assertEquals(0, payload.totalTicks());
	}

	@Test
	void noAbilitySlotClearsCooldownState() {
		FocusAbilityStatusPayload payload =
			new FocusAbilityStatusPayload(FocusAbilityStatusPayload.NO_ABILITY_SLOT, 10, 20);

		assertEquals(FocusAbilityStatusPayload.NO_ABILITY_SLOT, payload.slot());
		assertEquals(0, payload.remainingTicks());
		assertEquals(0, payload.totalTicks());
	}

	@Test
	void remainingTicksCannotExceedTotalTicks() {
		FocusAbilityStatusPayload payload = new FocusAbilityStatusPayload(0, 50, 20);

		assertEquals(0, payload.slot());
		assertEquals(20, payload.remainingTicks());
		assertEquals(20, payload.totalTicks());
	}

	@Test
	void negativeTicksClampToZeroWithoutClearingValidSlot() {
		FocusAbilityStatusPayload payload = new FocusAbilityStatusPayload(0, -5, -1);

		assertEquals(0, payload.slot());
		assertEquals(0, payload.remainingTicks());
		assertEquals(0, payload.totalTicks());
	}
}
