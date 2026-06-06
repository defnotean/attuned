package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class AttunedInvTest {
	@Test
	void publicConstructorNormalizesToSixSlots() {
		AttunedInv inv = new AttunedInv(List.of());

		assertEquals(AttunedInv.SIZE, inv.items().size());
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			assertEquals(ItemStack.EMPTY, inv.get(slot));
		}
	}

	@Test
	void itemsViewCannotMutateSnapshot() {
		AttunedInv inv = AttunedInv.empty();

		assertThrows(UnsupportedOperationException.class,
			() -> inv.items().set(0, ItemStack.EMPTY));
	}
}
