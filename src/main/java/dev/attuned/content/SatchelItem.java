package dev.attuned.content;

import dev.attuned.menu.SatchelMenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A stack-bound bag for storing spare Foci. */
public class SatchelItem extends Item {
	public SatchelItem(Item.Properties properties) {
		// AttunedComponents.SATCHEL_CONTENTS must already be registered when this item is
		// constructed during AttunedContent class-load. Attuned.onInitialize calls
		// AttunedComponents.init() before AttunedContent.init() to guarantee that ordering;
		// keep that order, or move this to a lazy lookup, before reordering bootstrap.
		super(properties.stacksTo(1)
			.component(AttunedComponents.SATCHEL_CONTENTS, AttunedComponents.emptyContents()));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(SatchelMenuType.provider(serverPlayer, hand));
		}
		return InteractionResult.SUCCESS;
	}
}
