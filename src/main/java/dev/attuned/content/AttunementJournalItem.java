package dev.attuned.content;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.compat.NetworkPackets;
import dev.attuned.network.OpenJournalPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

/**
 * A compact in-game guide to Attuned's core rules. It keeps written-book content
 * on the stack for data compatibility, but right-clicking opens Attuned's custom
 * client screen instead of Minecraft's vanilla written-book screen.
 */
public class AttunementJournalItem extends WrittenBookItem {
	public static final List<String> GUIDE_PAGE_KEYS = List.of(
		"journal.attuned.page1",
		"journal.attuned.page2",
		"journal.attuned.page3",
		"journal.attuned.page4",
		"journal.attuned.page34",
		"journal.attuned.page35",
		"journal.attuned.page36",
		"journal.attuned.page5",
		"journal.attuned.page6",
		"journal.attuned.page15",
		"journal.attuned.page16",
		"journal.attuned.page17",
		"journal.attuned.page29",
		"journal.attuned.page37",
		"journal.attuned.page38",
		"journal.attuned.page39",
		"journal.attuned.page40",
		"journal.attuned.page18",
		"journal.attuned.page19",
		"journal.attuned.page7",
		"journal.attuned.page41",
		"journal.attuned.page30",
		"journal.attuned.page31",
		"journal.attuned.page8",
		"journal.attuned.page14",
		"journal.attuned.page9",
		"journal.attuned.page24",
		"journal.attuned.page10",
		"journal.attuned.page11",
		"journal.attuned.page12",
		"journal.attuned.page13",
		"journal.attuned.page_tempering",
		"journal.attuned.page20",
		"journal.attuned.page21",
		"journal.attuned.page22",
		"journal.attuned.page25",
		"journal.attuned.page33",
		"journal.attuned.page23",
		"journal.attuned.page26",
		"journal.attuned.page27",
		"journal.attuned.page32",
		"journal.attuned.page28"
	);
	public AttunementJournalItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack stack = super.getDefaultInstance();
		ensureGuideContent(stack);
		return stack;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ensureGuideContent(stack);
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			NetworkPackets.send(serverPlayer, new OpenJournalPayload());
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	public static void showGuide(Player player) {
		PlayerMessages.system(player, new net.minecraft.network.chat.TranslatableComponent("journal.attuned.title")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (int i = 1; i <= 8; i++) {
			PlayerMessages.system(player, new net.minecraft.network.chat.TranslatableComponent("journal.attuned.line" + i)
				.withStyle(ChatFormatting.GRAY));
		}
	}

	private static void ensureGuideContent(ItemStack stack) {
		CompoundTag tag = stack.getOrCreateTag();
		tag.putString(WrittenBookItem.TAG_TITLE, "Attunement Journal");
		tag.putString(WrittenBookItem.TAG_AUTHOR, "Attuned");
		tag.putInt(WrittenBookItem.TAG_GENERATION, 0);
		tag.putBoolean(WrittenBookItem.TAG_RESOLVED, true);
		ListTag pages = new ListTag();
		for (String key : GUIDE_PAGE_KEYS) {
			pages.add(StringTag.valueOf(Component.Serializer.toJson(new net.minecraft.network.chat.TranslatableComponent(key))));
		}
		tag.put(WrittenBookItem.TAG_PAGES, pages);
	}
}
