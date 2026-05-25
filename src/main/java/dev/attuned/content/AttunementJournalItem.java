package dev.attuned.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * A compact in-game guide to Attuned's core rules. It behaves like a vanilla
 * written book so right-clicking opens Minecraft's normal page-turning book UI,
 * while the item texture and contents stay custom to the mod.
 */
public class AttunementJournalItem extends WrittenBookItem {
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
		return super.use(level, player, hand);
	}

	public static void showGuide(Player player) {
		player.sendSystemMessage(Component.translatable("journal.attuned.title")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (int i = 1; i <= 7; i++) {
			player.sendSystemMessage(Component.translatable("journal.attuned.line" + i)
				.withStyle(ChatFormatting.GRAY));
		}
	}

	private static WrittenBookContent createGuideContent() {
		List<Filterable<Component>> pages = List.of(
			Filterable.passThrough(Component.translatable("journal.attuned.page1")),
			Filterable.passThrough(Component.translatable("journal.attuned.page2")),
			Filterable.passThrough(Component.translatable("journal.attuned.page3")),
			Filterable.passThrough(Component.translatable("journal.attuned.page4")),
			Filterable.passThrough(Component.translatable("journal.attuned.page5")),
			Filterable.passThrough(Component.translatable("journal.attuned.page6")),
			Filterable.passThrough(Component.translatable("journal.attuned.page7"))
		);
		return new WrittenBookContent(
			Filterable.passThrough("Attunement Journal"),
			"Attuned",
			0,
			pages,
			false);
	}
}
