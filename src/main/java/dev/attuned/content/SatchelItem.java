package dev.attuned.content;

import dev.attuned.attunement.FocusHolder;
import dev.attuned.menu.SatchelMenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A stack-bound bag for storing spare Foci. The same class backs both reliquary
 * tiers: the small 27-slot satchel and the 54-slot Grand Focus Reliquary. The
 * contents component type and grid size are parameterized so each tier attaches
 * (and opens) the right-sized {@link FocusHolder}.
 */
public class SatchelItem extends Item {
	private final FocusHolder emptyContents;
	private final int size;
	private final boolean grand;

	public SatchelItem(Item.Properties properties) {
		this(properties, AttunedComponents.emptyContents(), AttunedComponents.SATCHEL_SIZE, false);
	}

	protected SatchelItem(Item.Properties properties, FocusHolder emptyContents, int size, boolean grand) {
		super(properties.stacksTo(1));
		this.emptyContents = emptyContents;
		this.size = size;
		this.grand = grand;
	}

	/** Builds the Grand Focus Reliquary tier (54 slots, grand contents component). */
	public static SatchelItem grand(Item.Properties properties) {
		return new SatchelItem(properties, AttunedComponents.emptyGrandContents(),
			AttunedComponents.GRAND_SATCHEL_SIZE, true);
	}

	public FocusHolder emptyContents() {
		return emptyContents;
	}

	public int size() {
		return size;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(SatchelMenuType.provider(serverPlayer, hand, grand));
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
