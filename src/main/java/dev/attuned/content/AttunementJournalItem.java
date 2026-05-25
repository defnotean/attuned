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
		List<Filterable<Component>> pages = List.of(
			Filterable.passThrough(Component.translatable("journal.attuned.page1")),
			Filterable.passThrough(Component.translatable("journal.attuned.page2")),
			Filterable.passThrough(Component.translatable("journal.attuned.page3")),
			Filterable.passThrough(Component.translatable("journal.attuned.page4")),
			Filterable.passThrough(Component.translatable("journal.attuned.page5")),
			Filterable.passThrough(Component.translatable("journal.attuned.page6")),
			Filterable.passThrough(Component.translatable("journal.attuned.page7")),
			Filterable.passThrough(Component.translatable("journal.attuned.page8")),
			Filterable.passThrough(Component.translatable("journal.attuned.page9")),
			Filterable.passThrough(Component.translatable("journal.attuned.page10")),
			Filterable.passThrough(Component.translatable("journal.attuned.page11")),
			Filterable.passThrough(Component.translatable("journal.attuned.page12")),
			Filterable.passThrough(Component.translatable("journal.attuned.page13")),
			Filterable.passThrough(Component.translatable("journal.attuned.page14")),
			Filterable.passThrough(Component.translatable("journal.attuned.page15")),
			Filterable.passThrough(Component.translatable("journal.attuned.page16")),
			Filterable.passThrough(Component.translatable("journal.attuned.page17")),
			Filterable.passThrough(Component.translatable("journal.attuned.page18")),
			Filterable.passThrough(Component.translatable("journal.attuned.page19"))
		);
		return new WrittenBookContent(
			Filterable.passThrough("Attunement Journal"),
			"Attuned",
			0,
			pages,
			false);
	}
}
