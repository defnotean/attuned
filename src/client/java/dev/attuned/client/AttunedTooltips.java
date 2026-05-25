package dev.attuned.client;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusLookup;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Appends Attuned flavour and stats to item tooltips: two lines of lore and a
 * green feature description on every Attuned item, then the colour-coded affinity
 * and attunement cost on registered Foci, plus a Unique tag where it applies.
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

			// Feature description on every Attuned item.
			lines.add(Component.empty());
			lines.add(Component.translatable("item.attuned." + path + ".effect")
				.withStyle(ChatFormatting.GREEN));

			// Affinity and attunement cost on items that are registered Foci.
			FocusDefinition definition = definitionFor(stack);
			if (definition != null) {
				Affinity affinity = definition.affinity().orElse(null);
				lines.add(Component.empty());
				lines.add(Component.literal("Affinity ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(affinityName(affinity))
						.withStyle(affinityColor(affinity), ChatFormatting.BOLD)));
				definition.faction().ifPresent(faction -> lines.add(Component.literal("Faction: ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.translatableWithFallback(
						"faction." + faction.getNamespace() + "." + faction.getPath(), faction.toString())
						.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))));
				lines.add(Component.literal("Cost ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(definition.cost() + " attunement")
						.withStyle(ChatFormatting.AQUA)));
				if (definition.unique()) {
					lines.add(Component.literal("Unique")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
						.append(Component.literal(" - only one can be active")
							.withStyle(ChatFormatting.GRAY)));
				}

				// Dormant marker — when this exact stack occupies an inactive Focus slot.
				var player = Minecraft.getInstance().player;
				if (player != null) {
					AttunedInv inv = AttunedAttachments.getInventory(player);
					for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
						if (inv.get(slot) == stack && !Attunement.isActive(player, slot)) {
							lines.add(Component.empty());
							lines.add(Component.literal("Dormant")
								.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
							lines.add(Component.literal("Raise your capacity or remove a Focus.")
								.withStyle(ChatFormatting.GRAY));
							break;
						}
					}
				}
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
		return FocusLookup.forItem(registry, stack.getItem()).orElse(null);
	}

	private static String affinityName(Affinity affinity) {
		if (affinity == null) {
			return "Neutral";
		}
		String lower = affinity.name().toLowerCase(Locale.ROOT);
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
