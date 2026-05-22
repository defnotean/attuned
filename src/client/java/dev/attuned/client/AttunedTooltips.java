package dev.attuned.client;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Appends Attuned flavour and stats to item tooltips: two lines of lore on every
 * Attuned item, then — for Foci — a spacer and the colour-coded affinity and
 * attunement cost.
 */
public final class AttunedTooltips {
	private AttunedTooltips() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (id == null || !id.getNamespace().equals(Attuned.MOD_ID)) {
				return;
			}
			String path = id.getPath();

			// Two lines of flavour lore on every Attuned item.
			lines.add(Component.translatable("item.attuned." + path + ".lore")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			lines.add(Component.translatable("item.attuned." + path + ".lore2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

			// Affinity and attunement cost on items that are registered Foci.
			FocusDefinition definition = definitionFor(stack);
			if (definition != null) {
				Affinity affinity = definition.affinity().orElse(null);
				lines.add(Component.empty());
				lines.add(Component.literal("Affinity: ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(affinityName(affinity))
						.withStyle(affinityColor(affinity), ChatFormatting.BOLD)));
				lines.add(Component.literal("Attunement Cost: ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Integer.toString(definition.cost()))
						.withStyle(ChatFormatting.AQUA)));
			}
		});
	}

	private static FocusDefinition definitionFor(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		Registry<FocusDefinition> registry =
			minecraft.level.registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		for (FocusDefinition definition : registry) {
			if (definition.item().value() == stack.getItem()) {
				return definition;
			}
		}
		return null;
	}

	private static String affinityName(Affinity affinity) {
		if (affinity == null) {
			return "Neutral";
		}
		String lower = affinity.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static ChatFormatting affinityColor(Affinity affinity) {
		if (affinity == null) {
			return ChatFormatting.GRAY;
		}
		return switch (affinity) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
		};
	}
}
