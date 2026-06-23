package dev.attuned.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.content.AttunementJournalItem;
import dev.attuned.network.CircleSnapshotSync;
import dev.attuned.network.CircleInvitePromptPayload;
import dev.attuned.party.CircleContributions;
import dev.attuned.party.CirclePolicy;
import dev.attuned.party.CircleRuntime;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
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
				.then(circleCommands())
				.then(Commands.literal("capacity")
					.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
					.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						printStatus(ctx.getSource(), player);
						return 1;
					}))
				.then(Commands.literal("validate")
					.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> validateContent(ctx.getSource())))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> circleCommands() {
		return Commands.literal("circle")
			.then(Commands.literal("create")
				.then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						ServerPlayer player = source.getPlayerOrException();
						CirclePolicy.Result result = CircleRuntime.manager().create(player.getUUID(), StringArgumentType.getString(ctx, "name"), player.level().getGameTime());
						CircleContributions.forgetIfMembershipChanged(result, player);
						CircleSnapshotSync.syncAfter(player.level().getServer(), result, player);
						sendCircleResult(source, result.status());
						return circleResultCode(result.status());
					})))
			.then(Commands.literal("invite")
				.then(Commands.argument("target", EntityArgument.player())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						ServerPlayer player = source.getPlayerOrException();
						ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
						CirclePolicy.Result result = CircleRuntime.manager().invite(player.getUUID(), target.getUUID(), player.level().getGameTime());
						CircleSnapshotSync.syncAfter(player.level().getServer(), result, player, target);
						sendCircleResult(source, result.status());
						if (result.status() == CirclePolicy.Status.OK) {
							sendCircleInviteNotice(target, player, result.circle().orElseThrow());
						}
						return circleResultCode(result.status());
					})))
			.then(Commands.literal("accept")
				.then(Commands.argument("inviter", EntityArgument.player())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						ServerPlayer player = source.getPlayerOrException();
						ServerPlayer inviter = EntityArgument.getPlayer(ctx, "inviter");
						CirclePolicy.Result result = CircleRuntime.manager().accept(player.getUUID(), inviter.getUUID(), player.level().getGameTime());
						CircleContributions.forgetIfMembershipChanged(result, player);
						CircleSnapshotSync.syncAfter(player.level().getServer(), result, player, inviter);
						sendCircleResult(source, result.status());
						return circleResultCode(result.status());
					})))
			.then(Commands.literal("leave")
				.executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					ServerPlayer player = source.getPlayerOrException();
					CirclePolicy.Result result = CircleRuntime.manager().leave(player.getUUID(), player.level().getGameTime());
					CircleContributions.forgetIfMembershipChanged(result, player);
					CircleSnapshotSync.syncAfter(player.level().getServer(), result, player);
					sendCircleResult(source, result.status());
					return circleResultCode(result.status());
				}))
			.then(Commands.literal("disband")
				.executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					ServerPlayer player = source.getPlayerOrException();
					Optional<CirclePolicy.Circle> previousCircle =
						CircleRuntime.manager().circleOf(player.getUUID());
					CirclePolicy.Result result =
						CircleRuntime.manager().disband(player.getUUID(), player.level().getGameTime());
					ServerPlayer[] affected = onlineCircleMembers(player, previousCircle);
					CircleContributions.forgetIfMembershipChanged(result, affected);
					CircleSnapshotSync.syncAfter(player.level().getServer(), result, affected);
					sendCircleResult(source, result.status());
					return circleResultCode(result.status());
				}))
			.then(Commands.literal("kick")
				.then(Commands.argument("target", EntityArgument.player())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						ServerPlayer player = source.getPlayerOrException();
						ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
						CirclePolicy.Result result = CircleRuntime.manager().kick(player.getUUID(), target.getUUID());
						CircleContributions.forgetIfMembershipChanged(result, target);
						CircleSnapshotSync.syncAfter(player.level().getServer(), result, player, target);
						sendCircleResult(source, result.status());
						return circleResultCode(result.status());
					})));
	}

	private static void sendCircleResult(CommandSourceStack source, CirclePolicy.Status status) {
		MutableComponent message = Component.translatable("circle.attuned.status."
			+ status.name().toLowerCase(Locale.ROOT));
		if (circleResultCode(status) > 0) {
			source.sendSuccess(() -> message.withStyle(ChatFormatting.AQUA), false);
		} else {
			source.sendFailure(message.withStyle(ChatFormatting.RED));
		}
	}

	private static int circleResultCode(CirclePolicy.Status status) {
		return status == CirclePolicy.Status.OK || status == CirclePolicy.Status.DISBANDED ? 1 : 0;
	}

	private static ServerPlayer[] onlineCircleMembers(ServerPlayer player,
			Optional<CirclePolicy.Circle> previousCircle) {
		if (previousCircle.isEmpty()) {
			return new ServerPlayer[] { player };
		}
		return previousCircle.orElseThrow().members().stream()
			.map(member -> player.level().getServer().getPlayerList().getPlayer(member))
			.filter(member -> member != null)
			.toArray(ServerPlayer[]::new);
	}

	private static void sendCircleInviteNotice(ServerPlayer target, ServerPlayer inviter, CirclePolicy.Circle circle) {
		ServerPlayNetworking.send(target, new CircleInvitePromptPayload(
			inviter.getUUID(),
			inviter.getName().getString(),
			circle.name(),
			circle.members().size(),
			CircleRuntime.maxMembers(),
			circle.invites().get(target.getUUID()).expiresAtTick()));
		target.sendSystemMessage(Component.translatable("circle.attuned.invited",
			inviter.getDisplayName(),
			circle.name(),
			circle.members().size(),
			CircleRuntime.maxMembers(),
			CircleRuntime.inviteTtlSeconds()).withStyle(ChatFormatting.AQUA));
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
		if (!canMoveFocus(player, first) || !canMoveFocus(player, second)) {
			source.sendFailure(Component.literal("A Focus slot contains an item that no longer has a Focus definition."));
			return 0;
		}
		AttunedAttachments.setSlot(player, from, second);
		AttunedAttachments.setSlot(player, to, first);
		source.sendSuccess(() -> Component.literal(
			"Swapped Focus slots " + (from + 1) + " and " + (to + 1) + "."), false);
		return 1;
	}

	private static boolean canMoveFocus(ServerPlayer player, ItemStack stack) {
		return stack.isEmpty() || Attunement.definitionFor(player, stack).isPresent();
	}

	private static int validateContent(CommandSourceStack source) {
		List<ValidationIssue> problems = new ArrayList<>();
		List<ValidationIssue> warnings = new ArrayList<>();
		var registries = source.getServer().registryAccess();
		var registry = registries.registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		Map<Item, FocusDefinition> byItem = new IdentityHashMap<>();
		Set<ResourceLocation> focusDefinitionIds = new HashSet<>();
		// Walk every focus/<name>.json file by file: each problem and each warning
		// is qualified with the Focus's item key so an author can find the source file.
		registry.holders().forEach(holder -> {
			FocusDefinition def = holder.value();
			ResourceLocation focusId = holder.key().location();
			focusDefinitionIds.add(focusId);
			String focusPath = focusDefinitionPath(focusId);
			ResourceLocation itemId = registryId(def.item(), BuiltInRegistries.ITEM::getKey);
			// An item that failed to resolve to a real registered item.
			if (!def.item().isBound()) {
				problems.add(ValidationIssue.error(focusPath, "item", itemId.toString(),
					"Focus item failed to resolve."));
			} else {
				FocusDefinition previous = byItem.putIfAbsent(def.item().value(), def);
				if (previous != null) {
					problems.add(ValidationIssue.error(focusPath, "item", itemId.toString(),
						"Duplicate FocusDefinition item."));
				}
			}
			// Behavior ids resolve code-first-then-data through the single funnel.
			def.behavior().ifPresent(behaviorId -> {
				if (AttunedRegistries.getBehavior(behaviorId, registries) == null) {
					problems.add(ValidationIssue.error(focusPath, "behavior", behaviorId.toString(),
						"Behavior id is not registered in code or the focus_behavior palette."));
				}
			});
			// Attribute ids on every modifier must resolve to a real attribute.
			for (int index = 0; index < def.modifiers().size(); index++) {
				ModifierEntry modifier = def.modifiers().get(index);
				if (!modifier.attribute().isBound()) {
					ResourceLocation attributeId = registryId(modifier.attribute(), BuiltInRegistries.ATTRIBUTE::getKey);
					String fieldPath = "modifiers[" + index + "].attribute";
					problems.add(ValidationIssue.error(focusPath, fieldPath, attributeId.toString(),
						"Modifier attribute failed to resolve."));
				}
			}
			// A missing display-name lang key is a warning, not a hard failure: the
			// Focus still works, it just shows a raw translation key in game.
			String langKey = "item." + itemId.getNamespace() + "." + itemId.getPath();
			if (!net.minecraft.locale.Language.getInstance().has(langKey)) {
				warnings.add(ValidationIssue.warning(focusPath, "lang." + langKey, langKey,
					"Missing display-name lang key."));
			}
		});

		// Walk the focus_behavior palette registry too, so a palette file that an
		// author shipped is reported alongside their focus files. A palette entry that
		// failed to decode never reaches the registry, so reaching here means it built.
		var behaviorRegistry = registries.registryOrThrow(AttunedRegistries.FOCUS_BEHAVIORS);
		var behaviorDefinitions = behaviorRegistry.holders().toList();
		int paletteCount = behaviorDefinitions.size();
		behaviorDefinitions.forEach(holder -> {
			ResourceLocation behaviorId = holder.key().location();
			String behaviorPath = focusBehaviorDefinitionPath(behaviorId);
			validateBehaviorDefinition(problems, behaviorPath, holder.value());
		});
		var synergyRegistry = registries.registryOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
		var synergies = synergyRegistry.holders().toList();
		int synergyCount = synergies.size();
		synergies.forEach(holder -> {
			var def = holder.value();
			ResourceLocation synergyId = holder.key().location();
			String synergyPath = synergyDefinitionPath(synergyId);
			for (int index = 0; index < def.members().size(); index++) {
				ResourceLocation memberId = def.members().get(index);
				if (!focusDefinitionIds.contains(memberId)) {
					String fieldPath = "members[" + index + "]";
					problems.add(ValidationIssue.error(synergyPath, fieldPath, memberId.toString(),
						"Confluence member FocusDefinition failed to resolve."));
				}
			}
			def.behavior().ifPresent(behaviorId -> {
				if (AttunedRegistries.getBehavior(behaviorId, registries) == null) {
					problems.add(ValidationIssue.error(synergyPath, "behavior", behaviorId.toString(),
						"Confluence behavior id is not registered in code or the focus_behavior palette."));
				}
			});
			for (int index = 0; index < def.modifiers().size(); index++) {
				ModifierEntry modifier = def.modifiers().get(index);
				if (!modifier.attribute().isBound()) {
					ResourceLocation attributeId = registryId(modifier.attribute(), BuiltInRegistries.ATTRIBUTE::getKey);
					String fieldPath = "modifiers[" + index + "].attribute";
					problems.add(ValidationIssue.error(synergyPath, fieldPath, attributeId.toString(),
						"Confluence modifier attribute failed to resolve."));
				}
			}
		});

		if (problems.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Attuned validation passed: "
				+ byItem.size() + " Focus definitions, " + paletteCount + " palette behavior(s), and "
				+ synergyCount + " Confluence definition(s) checked."), false);
			if (!warnings.isEmpty()) {
				source.sendSuccess(() -> Component.literal(
					"Attuned validation: " + warnings.size() + " warning(s) (missing lang keys)."), false);
				for (ValidationIssue warning : warnings.subList(0, Math.min(8, warnings.size()))) {
					source.sendSuccess(() -> Component.literal("- " + warning.format()), false);
				}
				if (warnings.size() > 8) {
					source.sendSuccess(() -> Component.literal(
						"- ...and " + (warnings.size() - 8) + " more."), false);
				}
			}
			return byItem.size();
		}
		source.sendFailure(Component.literal("Attuned validation found " + problems.size() + " issue(s):"));
		for (ValidationIssue problem : problems.subList(0, Math.min(8, problems.size()))) {
			source.sendFailure(Component.literal("- " + problem.format()));
		}
		if (problems.size() > 8) {
			source.sendFailure(Component.literal("- ...and " + (problems.size() - 8) + " more."));
		}
		if (!warnings.isEmpty()) {
			source.sendFailure(Component.literal(
				"Plus " + warnings.size() + " warning(s) (missing lang keys):"));
			for (ValidationIssue warning : warnings.subList(0, Math.min(8, warnings.size()))) {
				source.sendFailure(Component.literal("- " + warning.format()));
			}
		}
		return 0;
	}

	private static String focusDefinitionPath(ResourceLocation focusId) {
		return "data/" + focusId.getNamespace() + "/" + Attuned.MOD_ID + "/focus/" + focusId.getPath() + ".json";
	}

	private static String focusBehaviorDefinitionPath(ResourceLocation behaviorId) {
		return "data/" + behaviorId.getNamespace() + "/" + Attuned.MOD_ID
			+ "/focus_behavior/" + behaviorId.getPath() + ".json";
	}

	private static String synergyDefinitionPath(ResourceLocation synergyId) {
		return "data/" + synergyId.getNamespace() + "/" + Attuned.MOD_ID + "/synergy/" + synergyId.getPath() + ".json";
	}

	private static void validateBehaviorDefinition(List<ValidationIssue> problems, String behaviorPath,
			FocusBehaviorDef definition) {
		if (definition instanceof FocusBehaviorDef.ConditionalMobEffect effect) {
			validatePaletteMobEffect(problems, behaviorPath, effect.effect());
		} else if (definition instanceof FocusBehaviorDef.OnHitEffect effect) {
			validatePaletteMobEffect(problems, behaviorPath, effect.effect());
		} else if (definition instanceof FocusBehaviorDef.PeriodicEffect effect) {
			validatePaletteMobEffect(problems, behaviorPath, effect.effect());
		} else if (definition instanceof FocusBehaviorDef.BlockContextEffect effect) {
			validatePaletteMobEffect(problems, behaviorPath, effect.effect());
		} else if (definition instanceof FocusBehaviorDef.UseItemWindow window) {
			window.effect().ifPresent(effect -> validatePaletteMobEffect(problems, behaviorPath, effect));
			window.modifier().ifPresent(modifier -> validatePaletteModifier(problems, behaviorPath, modifier));
		} else if (definition instanceof FocusBehaviorDef.PartyAssist assist) {
			validatePaletteMobEffect(problems, behaviorPath, assist.effect().effect());
		} else if (definition instanceof FocusBehaviorDef.MarkedTarget marked) {
			validatePaletteMobEffect(problems, behaviorPath, marked.effectOnConsume().effect());
		} else if (definition instanceof FocusBehaviorDef.NavigationHint) {
			// No nested registry ids: target_kind is codec-validated data.
		} else if (definition instanceof FocusBehaviorDef.AttributeWhile attributeWhile) {
			validatePaletteModifier(problems, behaviorPath, attributeWhile.modifier());
		}
	}

	private static void validatePaletteMobEffect(List<ValidationIssue> problems, String behaviorPath,
			Holder<MobEffect> effect) {
		if (!effect.isBound()) {
			ResourceLocation effectId = registryId(effect, BuiltInRegistries.MOB_EFFECT::getKey);
			problems.add(ValidationIssue.error(behaviorPath, "effect", effectId.toString(),
				"Palette mob effect failed to resolve."));
		}
	}

	private static void validatePaletteModifier(List<ValidationIssue> problems, String behaviorPath,
			ModifierEntry modifier) {
		if (!modifier.attribute().isBound()) {
			ResourceLocation attributeId = registryId(modifier.attribute(), BuiltInRegistries.ATTRIBUTE::getKey);
			problems.add(ValidationIssue.error(behaviorPath, "modifier.attribute", attributeId.toString(),
				"Palette modifier attribute failed to resolve."));
		}
	}

	private static <T> ResourceLocation registryId(Holder<T> holder, Function<T, ResourceLocation> boundId) {
		return holder.unwrapKey()
			.map(key -> key.location())
			.orElseGet(() -> holder.isBound()
				? boundId.apply(holder.value())
				: new ResourceLocation(Attuned.MOD_ID, "unknown_unbound_id"));
	}

	private record ValidationIssue(String filePath, String fieldPath, String badId, String message,
			boolean warning) {
		static ValidationIssue error(String filePath, String fieldPath, String badId, String message) {
			return new ValidationIssue(filePath, fieldPath, badId, message, false);
		}

		static ValidationIssue warning(String filePath, String fieldPath, String badId, String message) {
			return new ValidationIssue(filePath, fieldPath, badId, message, true);
		}

		String format() {
			return filePath + ": " + fieldPath + " [" + badId + "] " + message;
		}
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
			case TIDE -> ChatFormatting.BLUE;
			case FORGE -> ChatFormatting.DARK_RED;
			case VERDANT -> ChatFormatting.GREEN;
			case UMBRAL -> ChatFormatting.DARK_PURPLE;
		};
	}

	private static ChatFormatting affinityColor(Optional<Affinity> affinity) {
		return affinity.map(AttunedCommands::affinityChatColor).orElse(ChatFormatting.GRAY);
	}
}
