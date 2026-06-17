package dev.attuned.effect;

import dev.attuned.compat.DustParticles;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedInv;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Small, throttled custom visual motifs for individual active Foci.
 *
 * <p>These are intentionally server-driven vanilla particles rather than new
 * gameplay state: the selected concept pass guides the look, and this class
 * translates that look into subtle Minecraft-readable bursts on the existing
 * Focus aura cadence.</p>
 */
public final class FocusVisualEffects {
	private FocusVisualEffects() {}

	private static final ResourceLocation SOFTSTEP_FOCUS = focusId("softstep_focus");
	private static final ResourceLocation AEGIS_FOCUS = focusId("aegis_focus");
	private static final ResourceLocation TIDE_FOCUS = focusId("tide_focus");
	private static final ResourceLocation CINDER_FOCUS = focusId("cinder_focus");

	private static final int SOFTSTEP_PURPLE = 0x5B2C86;
	private static final int AEGIS_GOLD = 0xF6B73C;
	private static final int TIDE_CYAN = 0x3DD6FF;
	private static final int CINDER_ORANGE = 0xFF7A1A;
	private static final double TAU = Math.PI * 2.0D;

	public static void spawn(ServerPlayer player, AttunedInv inventory, Iterable<Integer> activeSlots, int tick) {
		ServerLevel level = (ServerLevel) player.level();
		for (int slot : activeSlots) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ResourceLocation focusId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (SOFTSTEP_FOCUS.equals(focusId)) {
				spawnSoftstep(level, player, tick, slot);
			} else if (AEGIS_FOCUS.equals(focusId)) {
				spawnAegis(level, player, tick, slot);
			} else if (TIDE_FOCUS.equals(focusId)) {
				spawnTide(level, player, tick, slot);
			} else if (CINDER_FOCUS.equals(focusId)) {
				spawnCinder(level, player, tick, slot);
			}
		}
	}

	private static ResourceLocation focusId(String path) {
		return ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, path);
	}

	/** Softstep/Umbral: smoky violet footprints and low ground wisps. */
	private static void spawnSoftstep(ServerLevel level, ServerPlayer player, int tick, int slot) {
		Vec3 look = player.getLookAngle();
		double backX = -look.x;
		double backZ = -look.z;
		double sideX = -backZ;
		double sideZ = backX;
		double stepPulse = ((tick / 8 + slot) & 1) == 0 ? -1.0D : 1.0D;
		double baseX = player.getX() + backX * 0.35D;
		double baseZ = player.getZ() + backZ * 0.35D;
		double footX = baseX + sideX * 0.22D * stepPulse;
		double footZ = baseZ + sideZ * 0.22D * stepPulse;
		double y = player.getY() + 0.08D;

		level.sendParticles(ParticleTypes.SCULK_SOUL,
			footX, y + 0.05D, footZ, 1, 0.04D, 0.02D, 0.04D, 0.0D);
		level.sendParticles(DustParticles.color(SOFTSTEP_PURPLE, 0.75F),
			footX, y, footZ, 2, 0.08D, 0.01D, 0.08D, 0.0D);
	}

	/** Aegis/Bastion: a brief warm shield ring and protective sparks. */
	private static void spawnAegis(ServerLevel level, ServerPlayer player, int tick, int slot) {
		double phase = (tick + slot * 5) * 0.12D;
		double radius = 0.72D;
		double y = player.getY() + 1.0D;
		DustParticleOptions gold = DustParticles.color(AEGIS_GOLD, 1.0F);
		for (int i = 0; i < 6; i++) {
			double angle = phase + i * TAU / 6.0D;
			level.sendParticles(gold,
				player.getX() + Math.cos(angle) * radius, y, player.getZ() + Math.sin(angle) * radius,
				1, 0.0D, 0.02D, 0.0D, 0.0D);
		}
	}

	/** Tide: a cyan bubble spiral with a small splash base. */
	private static void spawnTide(ServerLevel level, ServerPlayer player, int tick, int slot) {
		double phase = (tick + slot * 7) * 0.18D;
		for (int i = 0; i < 4; i++) {
			double angle = phase + i * TAU / 4.0D;
			double y = player.getY() + 0.35D + i * 0.22D;
			level.sendParticles(ParticleTypes.BUBBLE,
				player.getX() + Math.cos(angle) * 0.42D, y, player.getZ() + Math.sin(angle) * 0.42D,
				1, 0.03D, 0.04D, 0.03D, 0.0D);
		}
		level.sendParticles(ParticleTypes.SPLASH,
			player.getX(), player.getY() + 0.12D, player.getZ(), 2, 0.24D, 0.02D, 0.24D, 0.0D);
		level.sendParticles(DustParticles.color(TIDE_CYAN, 0.75F),
			player.getX(), player.getY() + 0.72D, player.getZ(), 1, 0.28D, 0.18D, 0.28D, 0.0D);
	}

	/** Cinder/Fury: a compact ember arc and coal-bright sparks. */
	private static void spawnCinder(ServerLevel level, ServerPlayer player, int tick, int slot) {
		double phase = (tick + slot * 3) * 0.2D;
		for (int i = 0; i < 3; i++) {
			double angle = phase + i * TAU / 3.0D;
			double x = player.getX() + Math.cos(angle) * 0.55D;
			double z = player.getZ() + Math.sin(angle) * 0.55D;
			double y = player.getY() + 0.78D + i * 0.08D;
			level.sendParticles(ParticleTypes.FLAME, x, y, z,
				1, 0.03D, 0.03D, 0.03D, 0.0D);
		}
		level.sendParticles(ParticleTypes.LAVA,
			player.getX(), player.getY() + 0.9D, player.getZ(), 1, 0.18D, 0.16D, 0.18D, 0.0D);
		level.sendParticles(DustParticles.color(CINDER_ORANGE, 0.85F),
			player.getX(), player.getY() + 0.85D, player.getZ(), 1, 0.24D, 0.16D, 0.24D, 0.0D);
	}
}
