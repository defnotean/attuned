package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.Attunement;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side handler for the Altar of Reweaving's result roll. */
public final class ReweavingNetworking {
	private ReweavingNetworking() {}

	public static void init() {
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
			BlockState state = serverLevel.getBlockState(pos);
			if (!state.is(AttunedContent.ALTAR_OF_REWEAVING)) {
				return;
			}
			if (!player.isWithinBlockInteractionRange(pos, 4.0)) {
				return;
			}
			Container container = menu.container();
			if (!hasThreeFociAndFragment(container) || !container.getItem(ReweavingMenu.OUTPUT_SLOT).isEmpty()) {
				return;
			}
			ItemStack result = rollResult(player, serverLevel, container);
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

	private static boolean hasThreeFociAndFragment(Container container) {
		for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
			if (!isFocus(container.getItem(i))) {
				return false;
			}
		}
		return container.getItem(ReweavingMenu.CATALYST_SLOT).is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
	}

	private static ItemStack rollResult(ServerPlayer player, ServerLevel level, Container container) {
		Registry<FocusDefinition> registry =
			level.registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		List<ReweavingResultPicker.Candidate> candidates = focusCandidates(registry);
		Set<String> sacrificedIds = sacrificedIds(container);
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

	private static Set<String> sacrificedIds(Container container) {
		Set<String> ids = new TreeSet<>();
		for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
			ItemStack stack = container.getItem(i);
			if (isFocus(stack)) {
				ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			}
		}
		return ids;
	}

	private static boolean isFocus(ItemStack stack) {
		return !stack.isEmpty() && AttunedContent.FOCI.contains(stack.getItem());
	}

	private static Identifier identifier(String id) {
		String[] parts = id.split(":", 2);
		if (parts.length == 2) {
			return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
		}
		return Identifier.fromNamespaceAndPath("minecraft", id);
	}
}
