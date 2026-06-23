package dev.attuned.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.attuned.client.AttunedClientConfig;
import dev.attuned.client.AttunedClientConfig.HudLayout;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Behavioral geometry checks for the compact party HUD layout. */
class PartyHudGeometryTest {
	private static final int GUI_W = 854;
	private static final int GUI_H = 480;
	private static final int FULL_PARTY_HEIGHT = 175;
	private static final int BOSS_BAR_CLEARANCE = 36;

	@Test
	void defaultPartyHudClearsBossBarsAndFociCluster() throws Exception {
		Object party = bounds(GUI_W, GUI_H, FULL_PARTY_HEIGHT, HudLayout.PARTY_DEFAULT);

		assertEquals(HudAnchor.SCREEN_MARGIN, intAccessor(party, "x"),
			"Default party HUD should live on the left edge, away from the default Foci sidecar.");
		assertTrue(intAccessor(party, "y") >= HudAnchor.SCREEN_MARGIN + BOSS_BAR_CLEARANCE,
			"Default party HUD should leave vertical room for vanilla boss bars.");

		int fociX = HudAnchor.x(GUI_W, FociHud.HUD_W, HudLayout.DEFAULT);
		int fociY = HudAnchor.y(GUI_H, FociHud.HUD_H, HudLayout.DEFAULT);
		assertFalse(overlaps(party, fociX, fociY, FociHud.HUD_W, FociHud.HUD_H),
			"Default party HUD should not overlap the default Foci HUD cluster.");

		int combatW = staticIntField(CombatHud.class, "HUD_BACKPLATE_W");
		int combatH = staticIntField(CombatHud.class, "HUD_BACKPLATE_H");
		int primaryCombatX = staticIntMethod(CombatHud.class, "sidecarX",
			new Class<?>[] {int.class, int.class}, GUI_W, combatW);
		int primaryCombatY = staticIntMethod(CombatHud.class, "sidecarY",
			new Class<?>[] {int.class, int.class, int.class}, GUI_W, GUI_H, combatW);
		assertFalse(overlaps(party, primaryCombatX, primaryCombatY, combatW, combatH),
			"Default party HUD should not overlap the default combat HUD sidecar.");

		int secondaryCombatX = staticIntMethod(CombatHud.class, "secondarySidecarX",
			new Class<?>[] {int.class, int.class}, GUI_W, combatW);
		int secondaryCombatY = staticIntMethod(CombatHud.class, "secondarySidecarY",
			new Class<?>[] {int.class, int.class, int.class}, GUI_W, GUI_H, combatW);
		assertFalse(overlaps(party, secondaryCombatX, secondaryCombatY, combatW, combatH),
			"Default party HUD should not overlap the combat HUD when the Foci HUD is visible.");
	}

	@Test
	void allAnchorsAndSupportedScalesStayInsideTheScaledViewport() throws Exception {
		for (AttunedClientConfig.HudAnchor anchor : AttunedClientConfig.HudAnchor.values()) {
			for (float scale : new float[] {0.5F, 1.0F, 1.5F, 2.0F}) {
				HudLayout layout = new HudLayout(anchor, 0, 0, scale);
				Object party = bounds(GUI_W, GUI_H, FULL_PARTY_HEIGHT, layout);
				int x = intAccessor(party, "x");
				int y = intAccessor(party, "y");
				int width = intAccessor(party, "width");
				int height = intAccessor(party, "height");
				int scaledW = intAccessor(party, "scaledScreenW");
				int scaledH = intAccessor(party, "scaledScreenH");

				assertEquals(PartyHud.PARTY_HUD_W, width, "Party HUD width should stay stable.");
				assertEquals(FULL_PARTY_HEIGHT, height, "The tested full party stack should keep its measured height.");
				assertEquals(layout.scale(), floatAccessor(party, "scale"),
					"Party HUD bounds should remember the snapped client scale.");
				assertTrue(x >= HudAnchor.SCREEN_MARGIN, "Party HUD should clamp inside the left edge: " + anchor);
				assertTrue(y >= HudAnchor.SCREEN_MARGIN, "Party HUD should clamp inside the top edge: " + anchor);
				assertTrue(x + width <= scaledW - HudAnchor.SCREEN_MARGIN,
					"Party HUD should clamp inside the right edge: " + anchor + " @" + scale);
				assertTrue(y + height <= scaledH - HudAnchor.SCREEN_MARGIN,
					"Party HUD should clamp inside the bottom edge: " + anchor + " @" + scale);
			}
		}
	}

	private static Object bounds(int guiWidth, int guiHeight, int height, HudLayout layout) throws Exception {
		try {
			Method method = PartyHud.class.getDeclaredMethod("bounds",
				int.class, int.class, int.class, HudLayout.class);
			method.setAccessible(true);
			return method.invoke(null, guiWidth, guiHeight, height, layout);
		} catch (NoSuchMethodException e) {
			fail("PartyHud should expose a package-visible bounds helper used by draw().");
			throw e;
		}
	}

	private static int intAccessor(Object bounds, String name) throws Exception {
		Method method = bounds.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		return (int) method.invoke(bounds);
	}

	private static float floatAccessor(Object bounds, String name) throws Exception {
		Method method = bounds.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		return (float) method.invoke(bounds);
	}

	private static int staticIntField(Class<?> owner, String name) throws Exception {
		var field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return (int) field.get(null);
	}

	private static int staticIntMethod(Class<?> owner, String name, Class<?>[] parameterTypes,
			Object... args) throws Exception {
		Method method = owner.getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		return (int) method.invoke(null, args);
	}

	private static boolean overlaps(Object left, int rightX, int rightY, int rightW, int rightH)
			throws Exception {
		int leftX = intAccessor(left, "x");
		int leftY = intAccessor(left, "y");
		int leftW = intAccessor(left, "width");
		int leftH = intAccessor(left, "height");
		return leftX < rightX + rightW && leftX + leftW > rightX
			&& leftY < rightY + rightH && leftY + leftH > rightY;
	}
}
