package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusLookup;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.ReweavingResultPicker;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side handler for the Altar of Reweaving's result roll. */
public final class ReweavingNetworking {
	private static boolean initialized;

	private ReweavingNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.serverboundPlay().register(ReweavePayload.TYPE, ReweavePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ReweavePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> tryReweave(player));
		});
	}

	private static void tryReweave(ServerPlayer player) {
		if (!(player.containerMenu instanceof ReweavingMenu menu)) {
			return;
		}
		menu.access().execute((level, pos) -> {
			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}
			if (player.level() != serverLevel) {
				return;
			}
			BlockState state = serverLevel.getBlockState(pos);
			if (!state.is(AttunedContent.ALTAR_OF_REWEAVING)) {
				return;
			}
			if (!player.isWithinBlockInteractionRange(pos, 4.0)) {
				return;
			}
			Container container = menu.container();
			Registry<FocusDefinition> registry =
				serverLevel.registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
			if (!container.getItem(ReweavingMenu.OUTPUT_SLOT).isEmpty()) {
				return;
			}
			// Tempering: two copies of the same untempered Focus fuse into one
			// stronger Tempered copy. Checked before the three-Focus reweave so the
			// same-id pair routes here instead of falling through to the roll.
			if (menu.canTemper()) {
				ItemStack tempered = temperResult(container);
				if (tempered.isEmpty()) {
					return;
				}
				container.getItem(0).shrink(1);
				container.getItem(1).shrink(1);
				container.getItem(ReweavingMenu.CATALYST_SLOT).shrink(1);
				container.setItem(ReweavingMenu.OUTPUT_SLOT, tempered);
				menu.broadcastChanges();
				return;
			}
			if (!hasThreeFociAndFragment(container, registry)) {
				return;
			}
			ItemStack result = rollResult(player, serverLevel, container, registry);
			if (result.isEmpty()) {
				return;
			}
			for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
				container.getItem(i).shrink(1);
			}
			container.getItem(ReweavingMenu.CATALYST_SLOT).shrink(1);
			container.setItem(ReweavingMenu.OUTPUT_SLOT, result);
			menu.broadcastChanges();
		});
	}

	/**
	 * A fresh, single Tempered copy of the Focus in the first input slot. Carries
	 * nothing from the inputs beyond the item itself plus the Tempered marker, so
	 * the result is deterministic regardless of the sacrificed stacks' components.
	 */
	private static ItemStack temperResult(Container container) {
		ItemStack source = container.getItem(0);
		if (source.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack tempered = new ItemStack(source.getItem());
		tempered.set(AttunedComponents.TEMPERED, Unit.INSTANCE);
		return tempered;
	}

	private static boolean hasThreeFociAndFragment(Container container, Registry<FocusDefinition> registry) {
		for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
			if (focusDefinitionFor(registry, container.getItem(i)).isEmpty()) {
				return false;
			}
		}
		return container.getItem(ReweavingMenu.CATALYST_SLOT).is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
	}

	private static ItemStack rollResult(ServerPlayer player, ServerLevel level, Container container,
			Registry<FocusDefinition> registry) {
		List<ReweavingResultPicker.Candidate> candidates = focusCandidates(registry);
		Set<String> sacrificedIds = sacrificedIds(container, registry);
		Optional<String> committedAffinity =
			Attunement.committedAffinity(player).map(affinity -> affinity.getSerializedName());
		Optional<String> picked = ReweavingResultPicker.pick(
			candidates, sacrificedIds, committedAffinity, new java.util.Random(level.getRandom().nextLong()));
		if (picked.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(identifier(picked.get()));
		if (item == Items.AIR) {
			Attuned.LOGGER.warn("Reweaving picked unknown Focus item id {}", picked.get());
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	private static List<ReweavingResultPicker.Candidate> focusCandidates(Registry<FocusDefinition> registry) {
		return registry.stream()
			.map(def -> new ReweavingResultPicker.Candidate(
				BuiltInRegistries.ITEM.getKey(def.item().value()).toString(),
				def.affinity().map(affinity -> affinity.getSerializedName())))
			.toList();
	}

	private static Set<String> sacrificedIds(Container container, Registry<FocusDefinition> registry) {
		Set<String> ids = new TreeSet<>();
		for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
			ItemStack stack = container.getItem(i);
			if (focusDefinitionFor(registry, stack).isPresent()) {
				ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			}
		}
		return ids;
	}

	private static Optional<FocusDefinition> focusDefinitionFor(Registry<FocusDefinition> registry, ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return FocusLookup.forItem(registry, stack.getItem());
	}

	private static Identifier identifier(String id) {
		String[] parts = id.split(":", 2);
		if (parts.length == 2) {
			return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
		}
		return Identifier.fromNamespaceAndPath("minecraft", id);
	}
}
