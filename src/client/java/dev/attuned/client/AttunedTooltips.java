package dev.attuned.client;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.Aspect;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.attunement.FocusLookup;
import dev.attuned.content.AttunedComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Appends Attuned flavour and stats to item tooltips: two lines of lore and a
 * green feature description on every Attuned item, then the colour-coded affinity
 * and attunement cost on registered Foci, plus a Unique tag where it applies.
 */
public final class AttunedTooltips {
	private AttunedTooltips() {}

	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

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
				// A Tempered Focus reads as a gold-named, stronger, costlier variant.
				if (stack.has(AttunedComponents.TEMPERED)) {
					if (!lines.isEmpty()) {
						// Recolor the name line (index 0) gold to mark the Tempered variant.
						lines.set(0, lines.get(0).copy().withStyle(ChatFormatting.GOLD));
					}
					lines.add(Component.empty());
					lines.add(Component.translatable("tooltip.attuned.tempered")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
				if (!definition.modifiers().isEmpty()) {
					lines.add(Component.empty());
					lines.add(Component.translatable("tooltip.attuned.modifier.header")
						.withStyle(ChatFormatting.GRAY));
					for (ModifierEntry modifier : definition.modifiers()) {
						lines.add(modifierSummary(modifier)
							.withStyle(ChatFormatting.AQUA));
					}
				}

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
				definition.aspect().ifPresent(aspect -> {
					lines.add(Component.translatable("tooltip.attuned.aspect.line",
						aspectName(aspect).withStyle(aspectColor(aspect), ChatFormatting.BOLD))
						.withStyle(ChatFormatting.GRAY));
					lines.add(Component.translatable("tooltip.attuned.aspect.matchups",
						aspectList(aspect.strongAgainst()), aspectList(aspect.weakAgainst()))
						.withStyle(ChatFormatting.DARK_AQUA));
				});
				// Show the effective budget cost this stack consumes, so a Tempered
				// Focus's tooltip agrees with the budget it actually charges.
				lines.add(Component.literal("Cost ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Attunement.effectiveCost(definition, stack) + " attunement")
						.withStyle(ChatFormatting.AQUA)));
				if (definition.unique()) {
					lines.add(Component.literal("Unique")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
						.append(Component.literal(" - only one can be active")
							.withStyle(ChatFormatting.GRAY)));
				}

				// Equipped status: AttunedInv returns defensive stack copies, so compare
				// item/components instead of relying on the mutable stack identity.
				var player = Minecraft.getInstance().player;
				if (player != null) {
					AttunedInv inv = AttunedAttachments.getInventory(player);
					for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
						if (ItemStack.matches(inv.get(slot), stack)) {
							lines.add(Component.empty());
							lines.add(Component.translatable("tooltip.attuned.equipped_slot", slot + 1)
								.withStyle(ChatFormatting.AQUA));
							var dormantReason = Attunement.dormantReason(player, slot);
							if (dormantReason.isPresent()) {
								lines.add(Component.translatable("tooltip.attuned.status.dormant",
										dormantSummary(dormantReason.get()))
									.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
								lines.add(dormantAdvice(dormantReason.get())
									.withStyle(ChatFormatting.GRAY));
							} else {
								lines.add(Component.translatable("tooltip.attuned.status.active")
									.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
							}
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
			case HOLY -> ChatFormatting.YELLOW;
		};
	}

	private static MutableComponent aspectName(Aspect aspect) {
		return Component.translatableWithFallback(
			"aspect.attuned." + aspect.getSerializedName(), humanize(aspect.getSerializedName()));
	}

	private static MutableComponent aspectList(Collection<Aspect> aspects) {
		String names = aspects.stream()
			.map(aspect -> humanize(aspect.getSerializedName()))
			.collect(Collectors.joining(", "));
		return Component.literal(names);
	}

	private static ChatFormatting aspectColor(Aspect aspect) {
		return switch (aspect) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
			case HOLY -> ChatFormatting.YELLOW;
			case TIDE -> ChatFormatting.BLUE;
			case FORGE -> ChatFormatting.DARK_RED;
			case VERDANT -> ChatFormatting.GREEN;
			case UMBRAL -> ChatFormatting.DARK_PURPLE;
		};
	}

	private static MutableComponent modifierSummary(ModifierEntry modifier) {
		Identifier attributeId = modifier.attribute().unwrapKey()
			.map(key -> key.identifier())
			.orElseGet(() -> BuiltInRegistries.ATTRIBUTE.getKey(modifier.attribute().value()));
		String attributePath = attributeId.getPath();
		MutableComponent attributeName = Component.translatableWithFallback(
			"tooltip.attuned.modifier.attribute." + attributePath, humanize(attributePath));
		return Component.translatable("tooltip.attuned.modifier.line",
			modifierAmount(attributePath, modifier.amount(), modifier.operation()), attributeName);
	}

	private static String modifierAmount(String attributePath, double amount, AttributeModifier.Operation operation) {
		boolean percent = operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
			|| operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			|| "knockback_resistance".equals(attributePath);
		double display = percent ? amount * 100.0D : amount;
		return signedNumber(display) + (percent ? "%" : "");
	}

	private static String signedNumber(double value) {
		String sign = value >= 0.0D ? "+" : "";
		double rounded = Math.rint(value);
		if (Math.abs(value - rounded) < 0.0001D) {
			return sign + Long.toString(Math.round(rounded));
		}
		return sign + String.format(Locale.ROOT, "%.1f", value);
	}

	private static String humanize(String path) {
		String[] words = path.split("_");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				builder.append(word.substring(1));
			}
		}
		return builder.toString();
	}

	private static MutableComponent dormantSummary(BudgetResolver.DormantReason reason) {
		return switch (reason) {
			case NOT_ENOUGH_CAPACITY -> Component.translatable("tooltip.attuned.dormant.capacity.summary");
			case DUPLICATE_UNIQUE -> Component.translatable("tooltip.attuned.dormant.duplicate.summary");
		};
	}

	private static MutableComponent dormantAdvice(BudgetResolver.DormantReason reason) {
		return switch (reason) {
			case NOT_ENOUGH_CAPACITY -> Component.translatable("tooltip.attuned.dormant.capacity.advice");
			case DUPLICATE_UNIQUE -> Component.translatable("tooltip.attuned.dormant.duplicate.advice");
		};
	}
}
