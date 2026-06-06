package dev.attuned.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.AttunedRegistries;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.AttunementJournalItem;
import dev.attuned.menu.AltarMenu;
import dev.attuned.menu.AltarMenuType;
import dev.attuned.menu.ReweavingMenu;
import dev.attuned.menu.ReweavingMenuType;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Admin/debug commands for Attuned. Provides {@code /attuned capacity} for
 * reading or setting the attunement budget, and {@code /attuned status} as a
 * one-shot diagnostic dump of the player's full attunement state.
 */
public final class AttunedCommands {
	private static boolean initialized;

	private AttunedCommands() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("attuned")
				.then(Commands.literal("journal")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						AttunementJournalItem.showGuide(player);
						return 1;
					}))
				.then(Commands.literal("gui")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("altar")
						.executes(ctx -> openAltarGuiPreview(ctx.getSource())))
					.then(Commands.literal("reweaving")
						.executes(ctx -> openReweavingGuiPreview(ctx.getSource()))))
				.then(Commands.literal("focus")
					.then(Commands.literal("up")
						.then(Commands.argument("slot", IntegerArgumentType.integer(1, AttunedInv.SIZE))
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
								return moveFocus(ctx.getSource(), player, slot, slot - 1);
							})))
					.then(Commands.literal("down")
						.then(Commands.argument("slot", IntegerArgumentType.integer(1, AttunedInv.SIZE))
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
								return moveFocus(ctx.getSource(), player, slot, slot + 1);
							})))
					.then(Commands.literal("move")
						.then(Commands.argument("from", IntegerArgumentType.integer(1, AttunedInv.SIZE))
							.then(Commands.argument("to", IntegerArgumentType.integer(1, AttunedInv.SIZE))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									int from = IntegerArgumentType.getInteger(ctx, "from") - 1;
									int to = IntegerArgumentType.getInteger(ctx, "to") - 1;
									return moveFocus(ctx.getSource(), player, from, to);
								})))))
				// Operator-only (permission level 2). In 26.1 the old
				// CommandSourceStack#hasPermission(int) is gone; gating now uses
				// Commands.hasPermission(PermissionCheck) with a LEVEL_* constant.
				.then(Commands.literal("capacity")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
							int capacity = AttunedAttachments.getCapacity(player);
							ctx.getSource().sendSuccess(
								() -> Component.literal("Attunement capacity set to " + capacity), false);
							return capacity;
						})))
				.then(Commands.literal("status")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						printStatus(ctx.getSource(), player);
						return 1;
					}))
				.then(Commands.literal("validate")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> validateContent(ctx.getSource())))));
	}

	private static int openAltarGuiPreview(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		player.openMenu(new SimpleMenuProvider(
			(containerId, inventory, ignored) -> new AltarMenu(containerId, inventory, previewAltarInput(), ContainerLevelAccess.NULL),
			AltarMenuType.DISPLAY_NAME));
		source.sendSuccess(() -> Component.literal("Opened Attunement Altar GUI preview."), false);
		return 1;
	}

	private static SimpleContainer previewAltarInput() {
		SimpleContainer input = new SimpleContainer(AltarMenu.INPUT_SIZE);
		input.setItem(AltarMenu.INPUT_SLOT, new ItemStack(AttunedContent.ATTUNEMENT_SHARD, 8));
		return input;
	}

	private static int openReweavingGuiPreview(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		player.openMenu(new SimpleMenuProvider(
			(containerId, inventory, ignored) -> new ReweavingMenu(containerId, inventory, previewReweavingContainer(), ContainerLevelAccess.NULL),
			ReweavingMenuType.DISPLAY_NAME));
		source.sendSuccess(() -> Component.literal("Opened Altar of Reweaving GUI preview."), false);
		return 1;
	}

	private static SimpleContainer previewReweavingContainer() {
		SimpleContainer container = new SimpleContainer(ReweavingMenu.CONTAINER_SIZE);
		container.setItem(0, new ItemStack(AttunedContent.SWIFT_FOCUS));
		container.setItem(1, new ItemStack(AttunedContent.EDGE_FOCUS));
		container.setItem(2, new ItemStack(AttunedContent.IRON_FOCUS));
		container.setItem(ReweavingMenu.CATALYST_SLOT,
			new ItemStack(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT, 8));
		container.setItem(ReweavingMenu.OUTPUT_SLOT, new ItemStack(AttunedContent.BEACON_FOCUS));
		return container;
	}

	private static int moveFocus(CommandSourceStack source, ServerPlayer player, int from, int to) {
		if (to < 0 || to >= AttunedInv.SIZE) {
			source.sendFailure(Component.literal("That Focus slot cannot move any further."));
			return 0;
		}
		if (from == to) {
			return 0;
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);
		ItemStack first = inv.get(from).copy();
		ItemStack second = inv.get(to).copy();
		if (first.isEmpty() && second.isEmpty()) {
			source.sendFailure(Component.literal("Both Focus slots are empty."));
			return 0;
		}
		AttunedAttachments.setSlot(player, from, second);
		AttunedAttachments.setSlot(player, to, first);
		source.sendSuccess(() -> Component.literal(
			"Swapped Focus slots " + (from + 1) + " and " + (to + 1) + "."), false);
		return 1;
	}

	private static int validateContent(CommandSourceStack source) {
		List<String> problems = new ArrayList<>();
		var registry = source.getServer().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		Map<net.minecraft.world.item.Item, FocusDefinition> byItem = new IdentityHashMap<>();
		registry.listElements().forEach(holder -> {
			FocusDefinition def = holder.value();
			byItem.put(def.item().value(), def);
			def.behavior().ifPresent(behaviorId -> {
				if (dev.attuned.AttunedRegistries.getBehavior(behaviorId) == null) {
					problems.add("Missing behavior " + behaviorId + " for "
						+ BuiltInRegistries.ITEM.getKey(def.item().value()));
				}
			});
		});

		for (net.minecraft.world.item.Item focus : AttunedContent.FOCI) {
			if (!byItem.containsKey(focus)) {
				problems.add("Missing FocusDefinition for " + BuiltInRegistries.ITEM.getKey(focus));
			}
		}
		for (net.minecraft.world.item.Item item : byItem.keySet()) {
			if (!AttunedContent.isFocus(item)) {
				problems.add("FocusDefinition item is not in AttunedContent.FOCI: "
					+ BuiltInRegistries.ITEM.getKey(item));
			}
		}

		if (problems.isEmpty()) {
			source.sendSuccess(() -> Component.literal(
				"Attuned validation passed: " + byItem.size() + " Focus definitions checked."), false);
			return byItem.size();
		}
		source.sendFailure(Component.literal("Attuned validation found " + problems.size() + " issue(s):"));
		for (String problem : problems.subList(0, Math.min(8, problems.size()))) {
			source.sendFailure(Component.literal("- " + problem));
		}
		if (problems.size() > 8) {
			source.sendFailure(Component.literal("- ...and " + (problems.size() - 8) + " more."));
		}
		return 0;
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
		Optional<Apex.Capstone> apexCapstone = Apex.capstoneOf(player);
		boolean apexFiring = apexCapstone.isPresent() && Resonance.atApex(player);

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
			if (apexCapstone.isEmpty()) {
				return label("Apex: ")
					.append(Component.literal("Foci do not qualify").withStyle(ChatFormatting.DARK_GRAY));
			}
			Apex.Capstone capstone = apexCapstone.get();
			String name = capstone.displayName();
			if (apexFiring) {
				return label("Apex: ")
					.append(Component.literal("active — " + name)
						.withStyle(capstone.chatColor(), ChatFormatting.BOLD));
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
			case HOLY -> ChatFormatting.YELLOW;
		};
	}

	private static ChatFormatting affinityColor(Optional<Affinity> affinity) {
		return affinity.map(AttunedCommands::affinityChatColor).orElse(ChatFormatting.GRAY);
	}
}
