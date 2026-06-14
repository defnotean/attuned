package dev.attuned.synergy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SynergyResolverTest {

    private static final SynergyResolver.SynergyDef HUNTERS_PATIENCE =
        new SynergyResolver.SynergyDef("attuned:hunters_patience",
            List.of("attuned:lantern", "attuned:veil"));
    private static final SynergyResolver.SynergyDef CARTOGRAPHERS_TRUST =
        new SynergyResolver.SynergyDef("attuned:cartographers_trust",
            List.of("attuned:beacon", "attuned:waystone", "attuned:driftglass"));

    @Test
    void exactTwoMemberMatchActivatesTheConfluence() {
        Set<String> active = SynergyResolver.activeConfluences(
            Set.of("attuned:lantern", "attuned:veil"), List.of(HUNTERS_PATIENCE));
        assertEquals(Set.of("attuned:hunters_patience"), active,
            "All members active wakes exactly that Confluence.");
    }

    @Test
    void missingOneMemberLeavesTheConfluenceDormant() {
        Set<String> active = SynergyResolver.activeConfluences(
            Set.of("attuned:lantern"), List.of(HUNTERS_PATIENCE));
        assertTrue(active.isEmpty(),
            "A Confluence with a missing member is never active.");
    }

    @Test
    void supersetOfMembersStillActivates() {
        Set<String> active = SynergyResolver.activeConfluences(
            Set.of("attuned:lantern", "attuned:veil", "attuned:anchor"),
            List.of(HUNTERS_PATIENCE));
        assertEquals(Set.of("attuned:hunters_patience"), active,
            "Extra active Foci beyond the members do not block activation.");
    }

    @Test
    void threeMemberConfluenceNeedsAllThree() {
        assertTrue(SynergyResolver.activeConfluences(
                Set.of("attuned:beacon", "attuned:waystone"),
                List.of(CARTOGRAPHERS_TRUST)).isEmpty(),
            "Two of three members is not enough for a triad Confluence.");
        assertEquals(Set.of("attuned:cartographers_trust"),
            SynergyResolver.activeConfluences(
                Set.of("attuned:beacon", "attuned:waystone", "attuned:driftglass"),
                List.of(CARTOGRAPHERS_TRUST)),
            "All three members active wakes the triad.");
    }

    @Test
    void emptyActiveSetWakesNothing() {
        assertTrue(SynergyResolver.activeConfluences(Set.of(), List.of(HUNTERS_PATIENCE)).isEmpty(),
            "No active Foci means no Confluences.");
    }

    @Test
    void emptyDefinitionTableWakesNothing() {
        assertTrue(SynergyResolver.activeConfluences(
                Set.of("attuned:lantern", "attuned:veil"), List.of()).isEmpty(),
            "No defined Confluences means an empty result regardless of active Foci.");
    }

    @Test
    void overlappingConfluencesSharingAMemberBothWakeTogether() {
        SynergyResolver.SynergyDef sharedA =
            new SynergyResolver.SynergyDef("attuned:a", List.of("attuned:lantern", "attuned:veil"));
        SynergyResolver.SynergyDef sharedB =
            new SynergyResolver.SynergyDef("attuned:b", List.of("attuned:lantern", "attuned:smoke"));
        Set<String> active = SynergyResolver.activeConfluences(
            Set.of("attuned:lantern", "attuned:veil", "attuned:smoke"),
            List.of(sharedA, sharedB));
        assertEquals(Set.of("attuned:a", "attuned:b"), active,
            "Two Confluences sharing the lantern member both wake when their full member sets are active.");
    }

    @Test
    void emptyMembersListNeverActivatesEvenWhenFociAreActive() {
        SynergyResolver.SynergyDef degenerate =
            new SynergyResolver.SynergyDef("attuned:degenerate", List.of());
        assertFalse(
            SynergyResolver.activeConfluences(Set.of("attuned:lantern"), List.of(degenerate))
                .contains("attuned:degenerate"),
            "A memberless Confluence is treated as inert, not vacuously active.");
    }
}
