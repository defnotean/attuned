package dev.attuned.synergy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure, Minecraft-free policy for Focus Confluences: a Confluence is active iff
 * every one of its member Focus ids is in the active set. Modelled on
 * {@code BudgetResolver}/{@code PresetApplicationResolver} (String ids only) so its
 * test classpath never needs to Bootstrap Minecraft.
 */
public final class SynergyResolver {
    private SynergyResolver() {}

    /** A Confluence's identity and its required member Focus ids (the testable shape). */
    public record SynergyDef(String id, List<String> members) {
        public SynergyDef {
            members = List.copyOf(members);
        }
    }

    /**
     * @param activeFocusIds ids of currently-active Foci (e.g. "attuned:lantern_focus")
     * @param defs the loaded Confluence table
     * @return ids of every Confluence whose full member set is active
     */
    public static Set<String> activeConfluences(Set<String> activeFocusIds, List<SynergyDef> defs) {
        Set<String> active = new LinkedHashSet<>();
        for (SynergyDef def : defs) {
            if (def.members().isEmpty()) {
                continue; // a memberless Confluence is inert, not vacuously active
            }
            if (activeFocusIds.containsAll(def.members())) {
                active.add(def.id());
            }
        }
        return active;
    }

    /**
     * The single Confluence that is exactly one active member short — but only if it
     * has already been discovered, so the discovery meta is never spoiled by a hint.
     * Returns empty when nothing qualifies, when the candidate is already fully active,
     * or when more than one Confluence is one-away (ambiguous).
     *
     * @param active     ids of currently-active Foci
     * @param discovered ids of Confluences this player has already discovered
     * @param defs       the loaded Confluence table
     */
    public static Optional<String> previewOf(Set<String> active, Set<String> discovered, List<SynergyDef> defs) {
        String candidate = null;
        for (SynergyDef def : defs) {
            if (def.members().isEmpty() || !discovered.contains(def.id())) {
                continue;
            }
            if (active.containsAll(def.members())) {
                continue; // already fully active — not a "needs one more" hint
            }
            int missing = 0;
            for (String member : def.members()) {
                if (!active.contains(member)) {
                    missing++;
                }
            }
            if (missing == 1) {
                if (candidate != null) {
                    return Optional.empty(); // ambiguous: more than one is one-away
                }
                candidate = def.id();
            }
        }
        return Optional.ofNullable(candidate);
    }
}
