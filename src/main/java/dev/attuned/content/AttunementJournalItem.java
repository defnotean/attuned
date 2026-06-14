package dev.attuned.content;

import dev.attuned.network.OpenJournalPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
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
		"journal.attuned.page7",
		"journal.attuned.page30",
		"journal.attuned.page31",
		"journal.attuned.page8",
		"journal.attuned.page9",
		"journal.attuned.page10",
		"journal.attuned.page11",
		"journal.attuned.page12",
		"journal.attuned.page13",
		"journal.attuned.page14",
		"journal.attuned.page15",
		"journal.attuned.page16",
		"journal.attuned.page17",
		"journal.attuned.page29",
		"journal.attuned.page18",
		"journal.attuned.page19",
		"journal.attuned.page20",
		"journal.attuned.page21",
		"journal.attuned.page22",
		"journal.attuned.page23",
		"journal.attuned.page24",
		"journal.attuned.page25",
		"journal.attuned.page33",
		"journal.attuned.page26",
		"journal.attuned.page27",
		"journal.attuned.page32",
		"journal.attuned.page28"
	);
	private static final WrittenBookContent GUIDE_CONTENT = createGuideContent();

	public AttunementJournalItem(Properties properties) {
		super(properties.stacksTo(1)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, GUIDE_CONTENT));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
			stack.set(DataComponents.WRITTEN_BOOK_CONTENT, GUIDE_CONTENT);
		}
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new OpenJournalPayload());
		}
		return InteractionResult.SUCCESS;
	}

	public static void showGuide(Player player) {
		player.sendSystemMessage(Component.translatable("journal.attuned.title")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (int i = 1; i <= 8; i++) {
			player.sendSystemMessage(Component.translatable("journal.attuned.line" + i)
				.withStyle(ChatFormatting.GRAY));
		}
	}

	private static WrittenBookContent createGuideContent() {
		List<Filterable<Component>> pages = GUIDE_PAGE_KEYS.stream()
			.map(key -> {
				Component page = Component.translatable(key);
				return Filterable.passThrough(page);
			})
			.toList();
		return new WrittenBookContent(
			Filterable.passThrough("Attunement Journal"),
			"Attuned",
			0,
			pages,
			false);
	}
}
