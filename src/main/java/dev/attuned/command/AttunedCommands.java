package dev.attuned.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Admin/debug commands for Attuned. Provides {@code /attuned capacity} for
 * reading or setting the attunement budget, and {@code /attuned status} as a
 * one-shot diagnostic dump of the player's full attunement state.
 */
public final class AttunedCommands {
	private AttunedCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("attuned")
				// Operator-only (permission level 2). In 26.1 the old
				// CommandSourceStack#hasPermission(int) is gone; gating now uses
				// Commands.hasPermission(PermissionCheck) with a LEVEL_* constant.
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("capacity")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						int capacity = AttunedAttachments.getCapacity(player);
						ctx.getSource().sendSuccess(
							() -> Component.literal("Attunement capacity: " + capacity), false);
						return capacity;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int amount = IntegerArgumentType.getInteger(ctx, "amount");
							AttunedAttachments.setCapacity(player, amount);
							ctx.getSource().sendSuccess(
								() -> Component.literal("Attunement capacity set to " + amount), false);
							return amount;
						})))
				.then(Commands.literal("status")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						printStatus(ctx.getSource(), player);
						return 1;
					}))));
	}

	/** Dumps the player's attunement state to the command source as styled chat lines. */
	private static void printStatus(CommandSourceStack source, ServerPlayer player) {
		int capacity = Attunement.capacity(player);
		int used = Attunement.used(player);
		List<Integer> activeSlots = Attunement.activeSlots(player);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		Optional<Affinity> committed = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		Optional<Pact> pact = Pacts.activeOf(player);
		float resonance = Resonance.get(player);
		Optional<Affinity> apexAffinity = Apex.affinityOf(player);
		boolean apexFiring = apexAffinity.isPresent() && Resonance.atApex(player);

		source.sendSuccess(() -> Component.literal("=== Attuned status for " + player.getName().getString() + " ===")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

		source.sendSuccess(() -> label("Capacity: ")
			.append(Component.literal(used + " / " + capacity).withStyle(ChatFormatting.AQUA)), false);

		source.sendSuccess(() -> label("Used: ")
			.append(Component.literal(Integer.toString(used)).withStyle(ChatFormatting.AQUA)), false);

		source.sendSuccess(() -> label("Title: ")
			.append(Component.literal(titleText(activeSlots.size(), used))
				.withStyle(rankColor(used, activeSlots.size()))), false);

		source.sendSuccess(() -> label("Stance: ")
			.append(stanceComponent(committed, discord)), false);

		source.sendSuccess(() -> label("Active Foci (" + activeSlots.size() + "):")
			.withStyle(ChatFormatting.GRAY), false);
		if (activeSlots.isEmpty()) {
			source.sendSuccess(() -> Component.literal("  (none)")
				.withStyle(ChatFormatting.DARK_GRAY), false);
		} else {
			for (int slot : activeSlots) {
				ItemStack stack = inv.get(slot);
				String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
				Optional<FocusDefinition> def = Attunement.definitionFor(player, stack);
				int cost = def.map(FocusDefinition::cost).orElse(0);
				Optional<Affinity> aff = def.flatMap(FocusDefinition::affinity);
				String affName = aff.map(a -> a.name().toLowerCase(Locale.ROOT)).orElse("neutral");
				source.sendSuccess(() -> Component.literal("  - ")
					.withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(name).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (cost " + cost + ", ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(affName).withStyle(affinityColor(aff)))
					.append(Component.literal(")").withStyle(ChatFormatting.GRAY)), false);
			}
		}

		source.sendSuccess(() -> label("Pact: ")
			.append(pact.map(p -> (Component) p.displayName().withStyle(p.chatColor(), ChatFormatting.BOLD))
				.orElse(Component.literal("none").withStyle(ChatFormatting.DARK_GRAY))), false);

		source.sendSuccess(() -> label("Resonance: ")
			.append(Component.literal(String.format(Locale.ROOT, "%.2f", resonance))
				.withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" (Apex threshold " + String.format(Locale.ROOT, "%.2f", Resonance.APEX_THRESHOLD) + ")")
				.withStyle(ChatFormatting.DARK_GRAY)), false);

		source.sendSuccess(() -> {
			if (apexAffinity.isEmpty()) {
				return label("Apex: ")
					.append(Component.literal("Foci do not qualify").withStyle(ChatFormatting.DARK_GRAY));
			}
			Affinity capstone = apexAffinity.get();
			String name = Apex.capstoneName(capstone);
			if (apexFiring) {
				return label("Apex: ")
					.append(Component.literal("active — " + name)
						.withStyle(affinityChatColor(capstone), ChatFormatting.BOLD));
			}
			return label("Apex: ")
				.append(Component.literal("not active (would be " + name + " when resonance >= "
					+ String.format(Locale.ROOT, "%.2f", Resonance.APEX_THRESHOLD) + ")")
					.withStyle(ChatFormatting.GRAY));
		}, false);
	}

	private static net.minecraft.network.chat.MutableComponent label(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	/** Same two-word scheme as the client-side AttunementReadout, computed inline. */
	private static String titleText(int activeFoci, int used) {
		if (activeFoci == 0) {
			return "Unattuned";
		}
		return countWord(activeFoci) + " " + rankWord(used);
	}

	private static String countWord(int activeFoci) {
		if (activeFoci <= 1) {
			return "Lone";
		}
		if (activeFoci <= 3) {
			return "Bound";
		}
		if (activeFoci <= 5) {
			return "Woven";
		}
		return "Manifold";
	}

	private static String rankWord(int used) {
		if (used <= 5) {
			return "Initiate";
		}
		if (used <= 10) {
			return "Adept";
		}
		if (used <= 15) {
			return "Channeler";
		}
		return "Paragon";
	}

	private static ChatFormatting rankColor(int used, int activeFoci) {
		if (activeFoci == 0) {
			return ChatFormatting.DARK_GRAY;
		}
		if (used <= 5) {
			return ChatFormatting.WHITE;
		}
		if (used <= 10) {
			return ChatFormatting.GREEN;
		}
		if (used <= 15) {
			return ChatFormatting.AQUA;
		}
		return ChatFormatting.GOLD;
	}

	private static Component stanceComponent(Optional<Affinity> committed, boolean discord) {
		if (discord) {
			return Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		if (committed.isPresent()) {
			Affinity a = committed.get();
			String lower = a.name().toLowerCase(Locale.ROOT);
			String capitalized = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
			return Component.literal(capitalized + " (committed)")
				.withStyle(affinityChatColor(a));
		}
		return Component.literal("Unattuned").withStyle(ChatFormatting.DARK_GRAY);
	}

	private static ChatFormatting affinityChatColor(Affinity affinity) {
		return switch (affinity) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
		};
	}

	private static ChatFormatting affinityColor(Optional<Affinity> affinity) {
		return affinity.map(AttunedCommands::affinityChatColor).orElse(ChatFormatting.GRAY);
	}
}
