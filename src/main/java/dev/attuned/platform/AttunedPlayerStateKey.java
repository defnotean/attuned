package dev.attuned.platform;

import dev.attuned.Attuned;
import net.minecraft.resources.Identifier;

public enum AttunedPlayerStateKey {
	CAPACITY("capacity", true, true, true),
	INVENTORY("inventory", true, true, true),
	PRESETS("presets", true, true, true),
	MILESTONES("milestones", true, false, true),
	RESONANCE("resonance", true, true, true),
	ONBOARDING("onboarding", true, false, true),
	PACT_TRIAL_PROGRESS("pact_trial_progress", true, true, true),
	DISCOVERED_CONFLUENCES("discovered_confluences", true, true, true);

	private final String path;
	private final boolean persistent;
	private final boolean syncedToOwner;
	private final boolean copyOnDeath;

	AttunedPlayerStateKey(String path, boolean persistent, boolean syncedToOwner, boolean copyOnDeath) {
		this.path = path;
		this.persistent = persistent;
		this.syncedToOwner = syncedToOwner;
		this.copyOnDeath = copyOnDeath;
	}

	public String path() {
		return path;
	}

	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID, path);
	}

	public boolean persistent() {
		return persistent;
	}

	public boolean syncedToOwner() {
		return syncedToOwner;
	}

	public boolean copyOnDeath() {
		return copyOnDeath;
	}
}
