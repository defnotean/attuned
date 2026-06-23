package dev.attuned.content;

import dev.attuned.Attuned;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registers and orders Attuned creative-inventory tabs. */
final class AttunedCreativeTabs {
	private static boolean initialized;

	private AttunedCreativeTabs() {}

	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		registerFocusCreativeTab(
			"attuned",
			Component.translatable("itemGroup.attuned.fury_bastion_foci"),
			AttunedContent.BLOODFURY_FOCUS,
			false,
			AttunedContent.CINDER_FOCUS,
			AttunedContent.EDGE_FOCUS,
			AttunedContent.FRENZY_FOCUS,
			AttunedContent.BLOODFURY_FOCUS,
			AttunedContent.BLOODRUSH_FOCUS,
			AttunedContent.THORNWARD_FOCUS,
			AttunedContent.LEECH_FOCUS,
			AttunedContent.RAVAGER_FOCUS,
			AttunedContent.ANCHOR_FOCUS,
			AttunedContent.EMBERWARD_FOCUS,
			AttunedContent.IRON_FOCUS,
			AttunedContent.BULWARK_FOCUS,
			AttunedContent.GRANITEHIDE_FOCUS,
			AttunedContent.VITAL_FOCUS,
			AttunedContent.HAMMERWARD_FOCUS,
			AttunedContent.AEGIS_FOCUS);
		registerFocusCreativeTab(
			"attuned_zephyr_holy",
			Component.translatable("itemGroup.attuned.zephyr_holy_foci"),
			AttunedContent.GALESPUR_FOCUS,
			false,
			AttunedContent.DRIFT_FOCUS,
			AttunedContent.LEAP_FOCUS,
			AttunedContent.RAINSTEP_FOCUS,
			AttunedContent.SWIFT_FOCUS,
			AttunedContent.TIDE_FOCUS,
			AttunedContent.GALESPUR_FOCUS,
			AttunedContent.STORMCALL_FOCUS,
			AttunedContent.UPDRAFT_FOCUS,
			AttunedContent.ROOTSTEP_FOCUS,
			AttunedContent.VOTIVE_FOCUS,
			AttunedContent.BELLWETHER_FOCUS,
			AttunedContent.OATHGUARD_FOCUS,
			AttunedContent.SUNLANCE_FOCUS,
			AttunedContent.NAMESAKE_FOCUS,
			AttunedContent.CENSER_FOCUS,
			AttunedContent.THRESHOLD_FOCUS,
			AttunedContent.RESCUEFLAME_FOCUS);
		registerFocusCreativeTab(
			"attuned_tide_forge",
			Component.translatable("itemGroup.attuned.tide_forge_foci"),
			AttunedContent.TIDEWARDEN_FOCUS,
			false,
			AttunedContent.CURRENT_RUNNER_FOCUS,
			AttunedContent.EBBSTRIDE_FOCUS,
			AttunedContent.TIDEWARDEN_FOCUS,
			AttunedContent.WELLSPRING_FOCUS,
			AttunedContent.SALTBRAND_FOCUS,
			AttunedContent.RIPTIDE_HEART_FOCUS,
			AttunedContent.PEARLGUARD_FOCUS,
			AttunedContent.UNDERTOW_FOCUS,
			AttunedContent.BELLOWSFURY_FOCUS,
			AttunedContent.CINDERPLATE_FOCUS,
			AttunedContent.RIVET_FOCUS,
			AttunedContent.KILNWARD_FOCUS,
			AttunedContent.TEMPER_FOCUS,
			AttunedContent.SPARKWELD_FOCUS,
			AttunedContent.SLAGBRAND_FOCUS,
			AttunedContent.ANVILHEART_FOCUS);
		registerFocusCreativeTab(
			"attuned_verdant_umbral",
			Component.translatable("itemGroup.attuned.verdant_umbral_foci"),
			AttunedContent.OVERGROWTH_FOCUS,
			false,
			AttunedContent.FERNSTRIDE_FOCUS,
			AttunedContent.BRIARCOAT_FOCUS,
			AttunedContent.DEEPROOT_FOCUS,
			AttunedContent.SAPFLOW_FOCUS,
			AttunedContent.OVERGROWTH_FOCUS,
			AttunedContent.SEEDCALL_FOCUS,
			AttunedContent.BRAMBLEGATE_FOCUS,
			AttunedContent.THORNWAKE_FOCUS,
			AttunedContent.SOFTSTEP_FOCUS,
			AttunedContent.MASK_FOCUS,
			AttunedContent.SMOKE_FOCUS,
			AttunedContent.NEEDLE_FOCUS,
			AttunedContent.WHISPER_FOCUS,
			AttunedContent.BLACKOUT_FOCUS,
			AttunedContent.VEIL_FOCUS,
			AttunedContent.BONECHILL_FOCUS,
			AttunedContent.HOLLOWSTEP_FOCUS,
			AttunedContent.LAST_RITES_FOCUS,
			AttunedContent.ASHEN_DEBT_FOCUS,
			AttunedContent.GRAVEBIND_FOCUS,
			AttunedContent.CINDERTHIEF_FOCUS,
			AttunedContent.DUSKWARD_FOCUS,
			AttunedContent.GLOOMSTRIDE_FOCUS,
			AttunedContent.NULLVEIL_FOCUS,
			AttunedContent.SHADOWMELD_FOCUS,
			AttunedContent.SNAREMOON_FOCUS,
			AttunedContent.DREADFANG_FOCUS,
			AttunedContent.ECLIPSE_FOCUS);
		registerFocusCreativeTab(
			"attuned_utility",
			Component.translatable("itemGroup.attuned.utility_foci"),
			AttunedContent.LINECAST_FOCUS,
			true,
			AttunedContent.BEACON_FOCUS,
			AttunedContent.FORAGER_FOCUS,
			AttunedContent.HARVEST_FOCUS,
			AttunedContent.HEARTH_FOCUS,
			AttunedContent.LANTERN_FOCUS,
			AttunedContent.NIGHTGAZE_FOCUS,
			AttunedContent.WAYSTONE_FOCUS,
			AttunedContent.DELVER_FOCUS,
			AttunedContent.LODESTONE_FOCUS,
			AttunedContent.TREMOR_FOCUS,
			AttunedContent.VOIDSTEP_FOCUS,
			AttunedContent.HARPOON_FOCUS,
			AttunedContent.EPITAPH_FOCUS,
			AttunedContent.HARBORLIGHT_FOCUS,
			AttunedContent.LINECAST_FOCUS,
			AttunedContent.NETMENDER_FOCUS,
			AttunedContent.DRIFTGLASS_FOCUS,
			AttunedContent.BLOOM_FOCUS,
			AttunedContent.MOSSHEART_FOCUS,
			AttunedContent.CAVEWICK_FOCUS,
			AttunedContent.GLOWLINE_FOCUS,
			AttunedContent.DEPTHGLASS_FOCUS);
	}

	private static void registerFocusCreativeTab(String id, Component title, Item icon,
			boolean includeCoreItems, Item... focusItems) {
		FabricItemGroupBuilder.create(new ResourceLocation(Attuned.MOD_ID, id))
			.icon(() -> new ItemStack(icon))
			.appendItems(stacks -> {
				if (includeCoreItems) {
					stacks.add(new ItemStack(AttunedContent.ATTUNEMENT_JOURNAL));
					stacks.add(new ItemStack(AttunedContent.ATTUNEMENT_SHARD));
					stacks.add(new ItemStack(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT));
					stacks.add(new ItemStack(AttunedContent.ATTUNEMENT_ALTAR));
					stacks.add(new ItemStack(AttunedContent.ALTAR_OF_REWEAVING));
					stacks.add(new ItemStack(AttunedContent.SATCHEL_OF_FOCI));
					stacks.add(new ItemStack(AttunedContent.GRAND_SATCHEL_OF_FOCI));
				}
				for (Item focus : focusItems) {
					stacks.add(new ItemStack(focus));
				}
				if (includeCoreItems) {
					for (Item customFocus : AttunedContent.CUSTOM_FOCI) {
						stacks.add(new ItemStack(customFocus));
					}
				}
			})
			.build();
	}
}
