package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.content.behavior.AegisBehavior;
import dev.attuned.content.behavior.AnchorBehavior;
import dev.attuned.content.behavior.BeaconBehavior;
import dev.attuned.content.behavior.BloomBehavior;
import dev.attuned.content.behavior.BlackoutBehavior;
import dev.attuned.content.behavior.BloodfuryBehavior;
import dev.attuned.content.behavior.DelverBehavior;
import dev.attuned.content.behavior.DreadfangBehavior;
import dev.attuned.content.behavior.DriftglassBehavior;
import dev.attuned.content.behavior.DuskwardBehavior;
import dev.attuned.content.behavior.EbbstrideBehavior;
import dev.attuned.content.behavior.EclipseBehavior;
import dev.attuned.content.behavior.GloomstrideBehavior;
import dev.attuned.content.behavior.EmberwardBehavior;
import dev.attuned.content.behavior.ForagerBehavior;
import dev.attuned.content.behavior.ForgewardedBehavior;
import dev.attuned.content.behavior.GalespurBehavior;
import dev.attuned.content.behavior.HarpoonBehavior;
import dev.attuned.content.behavior.HarvestBehavior;
import dev.attuned.content.behavior.HarborlightBehavior;
import dev.attuned.content.behavior.HearthBehavior;
import dev.attuned.content.behavior.KilnwardBehavior;
import dev.attuned.content.behavior.LanternBehavior;
import dev.attuned.content.behavior.LodestoneBehavior;
import dev.attuned.content.behavior.MaskBehavior;
import dev.attuned.content.behavior.MossheartBehavior;
import dev.attuned.content.behavior.NightgazeBehavior;
import dev.attuned.content.behavior.PearlguardBehavior;
import dev.attuned.content.behavior.RadiantFocusBehaviors;
import dev.attuned.content.behavior.RainstepBehavior;
import dev.attuned.content.behavior.RevenantFocusBehaviors;
import dev.attuned.content.behavior.RivetBehavior;
import dev.attuned.content.behavior.RootstepBehavior;
import dev.attuned.content.behavior.ShadowmeldBehavior;
import dev.attuned.content.behavior.SmokeBehavior;
import dev.attuned.content.behavior.SoftstepBehavior;
import dev.attuned.content.behavior.SparkweldBehavior;
import dev.attuned.content.behavior.StormcallBehavior;
import dev.attuned.content.behavior.SunwardenBehavior;
import dev.attuned.content.behavior.TemperBehavior;
import dev.attuned.content.behavior.TideBehavior;
import dev.attuned.content.behavior.TremorBehavior;
import dev.attuned.content.behavior.UpdraftBehavior;
import dev.attuned.content.behavior.VeilBehavior;
import dev.attuned.content.behavior.VoidstepBehavior;
import dev.attuned.content.behavior.WaystoneBehavior;
import dev.attuned.content.behavior.WhisperBehavior;
import dev.attuned.content.behavior.WildwardBehavior;
import net.minecraft.resources.ResourceLocation;

/** Registers code-backed Focus behaviours referenced by datapack Focus definitions. */
final class AttunedFocusBehaviors {
	private static boolean initialized;

	private AttunedFocusBehaviors() {}

	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		register("tide", new TideBehavior());
		register("ebbstride", new EbbstrideBehavior());
		register("pearlguard", new PearlguardBehavior());
		register("galespur", new GalespurBehavior());
		register("rainstep", new RainstepBehavior());
		register("updraft", new UpdraftBehavior());
		register("emberward", new EmberwardBehavior());
		register("anchor", new AnchorBehavior());
		register("aegis", new AegisBehavior());
		register("nightgaze", new NightgazeBehavior());
		register("hearth", new HearthBehavior());
		register("lantern", new LanternBehavior());
		register("delver", new DelverBehavior());
		register("lodestone", new LodestoneBehavior());
		register("stormcall", new StormcallBehavior());
		register("bloodfury", new BloodfuryBehavior());
		register("harvest", new HarvestBehavior());
		register("forager", new ForagerBehavior());
		register("tremor", new TremorBehavior());
		register("beacon", new BeaconBehavior());
		register("waystone", new WaystoneBehavior());
		register("voidstep", new VoidstepBehavior());
		register("softstep", new SoftstepBehavior());
		register("veil", new VeilBehavior());
		register("smoke", new SmokeBehavior());
		register("blackout", new BlackoutBehavior());
		register("mask", new MaskBehavior());
		register("whisper", new WhisperBehavior());
		register("harborlight", new HarborlightBehavior());
		register("driftglass", new DriftglassBehavior());
		register("harpoon", new HarpoonBehavior());
		register("votive", new RadiantFocusBehaviors.Votive());
		register("bellwether", new RadiantFocusBehaviors.Bellwether());
		register("oathguard", new RadiantFocusBehaviors.Oathguard());
		register("censer", new RadiantFocusBehaviors.Censer());
		register("namesake", new RadiantFocusBehaviors.Namesake());
		register("threshold", new RadiantFocusBehaviors.Threshold());
		register("rootstep", new RootstepBehavior());
		register("bloom", new BloomBehavior());
		register("mossheart", new MossheartBehavior());
		register("rivet", new RivetBehavior());
		register("kilnward", new KilnwardBehavior());
		register("sparkweld", new SparkweldBehavior());
		register("temper", new TemperBehavior());
		register("epitaph", new RevenantFocusBehaviors.Epitaph());
		register("hollowstep", new RevenantFocusBehaviors.Hollowstep());

		// Umbral Eclipse - darkness- and stealth-gated Foci.
		register("gloomstride", new GloomstrideBehavior());
		register("duskward", new DuskwardBehavior());
		register("shadowmeld", new ShadowmeldBehavior());
		register("dreadfang", new DreadfangBehavior());
		register("eclipse", new EclipseBehavior());

		// Confluence behaviors (no backing Focus item; tick with ItemStack.EMPTY via Synergies).
		register("wildward", new WildwardBehavior());
		register("sunwarden", new SunwardenBehavior());
		register("forgewarded", new ForgewardedBehavior());
	}

	private static void register(String name, FocusBehavior behavior) {
		AttunedRegistries.registerBehavior(
			new ResourceLocation(Attuned.MOD_ID, name), behavior);
	}
}
