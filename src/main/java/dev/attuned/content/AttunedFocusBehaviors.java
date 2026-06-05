package dev.attuned.content;

import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.content.behavior.AegisBehavior;
import dev.attuned.content.behavior.AnchorBehavior;
import dev.attuned.content.behavior.BeaconBehavior;
import dev.attuned.content.behavior.BloodfuryBehavior;
import dev.attuned.content.behavior.DelverBehavior;
import dev.attuned.content.behavior.DriftglassBehavior;
import dev.attuned.content.behavior.EmberwardBehavior;
import dev.attuned.content.behavior.ForagerBehavior;
import dev.attuned.content.behavior.GalespurBehavior;
import dev.attuned.content.behavior.HarpoonBehavior;
import dev.attuned.content.behavior.HarvestBehavior;
import dev.attuned.content.behavior.HarborlightBehavior;
import dev.attuned.content.behavior.HearthBehavior;
import dev.attuned.content.behavior.LanternBehavior;
import dev.attuned.content.behavior.LodestoneBehavior;
import dev.attuned.content.behavior.NightgazeBehavior;
import dev.attuned.content.behavior.RadiantFocusBehaviors;
import dev.attuned.content.behavior.RainstepBehavior;
import dev.attuned.content.behavior.RevenantFocusBehaviors;
import dev.attuned.content.behavior.SmokeBehavior;
import dev.attuned.content.behavior.SoftstepBehavior;
import dev.attuned.content.behavior.StormcallBehavior;
import dev.attuned.content.behavior.TideBehavior;
import dev.attuned.content.behavior.TremorBehavior;
import dev.attuned.content.behavior.VeilBehavior;
import dev.attuned.content.behavior.VoidstepBehavior;
import dev.attuned.content.behavior.WaystoneBehavior;
import net.minecraft.resources.Identifier;

/** Registers code-backed Focus behaviours referenced by datapack Focus definitions. */
final class AttunedFocusBehaviors {
	private static boolean initialized;

	private AttunedFocusBehaviors() {}

	static void init() {
		if (initialized) {
			return;
		}
		register("tide", new TideBehavior());
		register("galespur", new GalespurBehavior());
		register("rainstep", new RainstepBehavior());
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
		register("harborlight", new HarborlightBehavior());
		register("driftglass", new DriftglassBehavior());
		register("harpoon", new HarpoonBehavior());
		register("votive", new RadiantFocusBehaviors.Votive());
		register("bellwether", new RadiantFocusBehaviors.Bellwether());
		register("oathguard", new RadiantFocusBehaviors.Oathguard());
		register("censer", new RadiantFocusBehaviors.Censer());
		register("threshold", new RadiantFocusBehaviors.Threshold());
		register("epitaph", new RevenantFocusBehaviors.Epitaph());
		register("hollowstep", new RevenantFocusBehaviors.Hollowstep());
		initialized = true;
	}

	private static void register(String name, FocusBehavior behavior) {
		AttunedRegistries.registerBehavior(
			Identifier.fromNamespaceAndPath(Attuned.MOD_ID, name), behavior);
	}
}
