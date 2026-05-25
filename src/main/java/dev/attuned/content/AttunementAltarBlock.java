package dev.attuned.content;

import com.mojang.serialization.MapCodec;
import dev.attuned.AttunedConfig;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import dev.attuned.menu.AltarMenuType;
import dev.attuned.onboarding.Onboarding;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Attunement Altar — the home block of the attunement economy, where crafted
 * Attunement Shards are bound into permanent capacity. The {@link #AFFINITY}
 * blockstate records the affinity the Altar was last attuned to, which drives its
 * glow. Player interactions are added as overrides on this class.
 */
public class AttunementAltarBlock extends Block {

	/** The affinity an Altar currently glows with — {@code NONE} until first used. */
	public enum AltarAffinity implements StringRepresentable {
		NONE("none"),
		FURY("fury"),
		BASTION("bastion"),
		ZEPHYR("zephyr");

		private final String serializedName;

		AltarAffinity(String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return serializedName;
		}
	}

	public static final EnumProperty<AltarAffinity> AFFINITY =
		EnumProperty.create("affinity", AltarAffinity.class);

	public static final MapCodec<AttunementAltarBlock> CODEC = simpleCodec(AttunementAltarBlock::new);
	private static final double PROXIMITY_PULSE_RADIUS = 5.0;
	private static final long PROXIMITY_PULSE_COOLDOWN_TICKS = 100L;
	private static final long PROXIMITY_PULSE_RETENTION_TICKS = PROXIMITY_PULSE_COOLDOWN_TICKS * 4;
	private static final Map<ProximityPulseKey, Long> PROXIMITY_PULSE_TICKS = new HashMap<>();

	// Mirrors the solid-block monument 3D model element-for-element so the
	// raytrace hits every visible surface — full 16x1 base slab and top plate,
	// 14x14 stepped base and capital, the 12x10x12 solid core, and the four
	// full-height 2x10x2 corner gem columns that protrude beyond the core.
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),    // base slab (full footprint)
		Block.box(1.0, 2.0, 1.0, 15.0, 4.0, 15.0),    // stepped base (14x2x14)
		Block.box(2.0, 4.0, 2.0, 14.0, 14.0, 14.0),   // solid core (12x10x12)
		Block.box(1.0, 4.0, 1.0, 3.0, 14.0, 3.0),     // gem NW (2x10x2 column)
		Block.box(13.0, 4.0, 1.0, 15.0, 14.0, 3.0),   // gem NE
		Block.box(1.0, 4.0, 13.0, 3.0, 14.0, 15.0),   // gem SW
		Block.box(13.0, 4.0, 13.0, 15.0, 14.0, 15.0), // gem SE
		Block.box(1.0, 14.0, 1.0, 15.0, 15.0, 15.0),  // stepped capital (14x1x14)
		Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0)); // top plate (full 16x1x16)

	public AttunementAltarBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AFFINITY, AltarAffinity.NONE));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AFFINITY);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Right-click with Attunement Shards: bind one into capacity and take on the binder's affinity. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		// When the held stack isn't a shard (including the empty hand), defer to
		// the empty-hand path so {@link #useWithoutItem} gets a chance to fire. In
		// 26.1.2 the dispatcher in ServerPlayerGameMode only calls useWithoutItem
		// when useItemOn returns TRY_WITH_EMPTY_HAND — returning PASS here would
		// swallow the click and never open the altar GUI.
		if (stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
			if (!level.isClientSide()) {
				AttunementShardFragmentItem.sendProgressHint(player);
			}
			return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
		}
		if (!stack.is(AttunedContent.ATTUNEMENT_SHARD)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (bindShard(level, pos, state, player, stack)) {
			if (player instanceof ServerPlayer serverPlayer) {
				Onboarding.tryAltarHint(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Right-click empty-handed: open the Altar GUI so the player can bind shards visually. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.openMenu(AltarMenuType.provider(level, pos));
		if (player instanceof ServerPlayer serverPlayer) {
			Onboarding.tryAltarHint(serverPlayer);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Consumes one shard from {@code stack} and raises the binder's attunement
	 * capacity, mirroring the side effects of an in-hand right-click bind: the
	 * Altar's affinity blockstate is updated, particles and a chime are emitted
	 * server-side, and the player receives the same chat confirmation.
	 *
	 * <p>Returns {@code true} when a shard was actually bound, {@code false} if
	 * capacity was already at the cap (in which case the shard is left alone and
	 * the player is told their attunement is full).
	 *
	 * <p>Used by the in-hand bind flow and by the Altar GUI's "Bind" button.
	 */
	public static boolean bindShard(Level level, BlockPos pos, BlockState state,
			Player player, ItemStack stack) {
		int cap = AttunedConfig.get().capacityCap();
		int capacity = AttunedAttachments.getCapacity(player);
		if (capacity >= cap) {
			player.sendSystemMessage(Component.literal("Your attunement is already at its fullest.")
				.withStyle(ChatFormatting.GRAY));
			return false;
		}
		int raised = Math.min(cap, capacity + AttunedConfig.get().capacityPerShard());
		AttunedAttachments.setCapacity(player, raised);
		// Creative players keep their shard, the same way vanilla skips item
		// consumption in creative across furnaces, brewing stands, and so on.
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		Optional<Affinity> affinity = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		level.setBlock(pos, state.setValue(AFFINITY, altarAffinityOf(affinity)), Block.UPDATE_CLIENTS);

		ServerLevel server = (ServerLevel) level;
		AltarAnimations.RitualFlair flair = scanRitualFlair(server, pos);
		server.sendParticles(ParticleTypes.ENCHANT,
			pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
		server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
			0.8F, bindChimePitch(affinity, discord));
		if (player instanceof ServerPlayer serverPlayer) {
			AltarAnimations.begin(server, pos, serverPlayer, stanceRgb(affinity), flair);
			applyBindingPerk(serverPlayer);
		}
		player.sendSystemMessage(Component.literal("The Altar binds the shard — capacity ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(raised + " / " + cap).withStyle(ChatFormatting.AQUA)));
		return true;
	}

	private static void applyBindingPerk(ServerPlayer player) {
		Attunement.committedAffinity(player).ifPresent(affinity -> {
			MobEffectInstance effect = switch (affinity) {
				case FURY -> new MobEffectInstance(MobEffects.STRENGTH, 400, 0, true, true, true);
				case BASTION -> new MobEffectInstance(MobEffects.RESISTANCE, 400, 0, true, true, true);
				case ZEPHYR -> new MobEffectInstance(MobEffects.SPEED, 400, 0, true, true, true);
			};
			player.addEffect(effect);
		});
	}

	/**
	 * The player's stance colour as a 24-bit RGB suitable for
	 * {@code DustParticleOptions}: their committed affinity's colour with the
	 * alpha byte stripped, or a neutral grey when no affinity is committed.
	 */
	private static int stanceRgb(Optional<Affinity> affinity) {
		return affinity
			.map(a -> a.argb() & 0x00FFFFFF)
			.orElse(0x8B8B8B);
	}

	private static float bindChimePitch(Optional<Affinity> affinity, boolean discord) {
		if (discord || affinity.isEmpty()) {
			return 1.0F;
		}
		return switch (affinity.get()) {
			case FURY -> 0.88F;
			case BASTION -> 0.76F;
			case ZEPHYR -> 1.18F;
		};
	}

	private static AltarAnimations.RitualFlair scanRitualFlair(ServerLevel level, BlockPos pos) {
		int litCandles = 0;
		int amethyst = 0;
		for (BlockPos scanPos : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
			BlockState scanState = level.getBlockState(scanPos);
			if (isLitCandle(scanState)) {
				litCandles += scanState.hasProperty(CandleBlock.CANDLES)
					? scanState.getValue(CandleBlock.CANDLES)
					: 1;
			}
			if (isAmethystAccent(scanState)) {
				amethyst++;
			}
		}
		return new AltarAnimations.RitualFlair(litCandles, amethyst);
	}

	private static boolean isLitCandle(BlockState state) {
		return state.is(BlockTags.CANDLES) && AbstractCandleBlock.isLit(state);
	}

	private static boolean isAmethystAccent(BlockState state) {
		return state.is(Blocks.BUDDING_AMETHYST)
			|| state.is(Blocks.AMETHYST_CLUSTER)
			|| state.is(Blocks.LARGE_AMETHYST_BUD)
			|| state.is(Blocks.MEDIUM_AMETHYST_BUD)
			|| state.is(Blocks.SMALL_AMETHYST_BUD);
	}

	/** The stance label for {@code player} — Discord, a committed affinity, or None. */
	public static Component stanceLabel(Player player) {
		if (Attunement.isDiscord(player)) {
			return Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		Optional<Affinity> affinity = Attunement.committedAffinity(player);
		if (affinity.isEmpty()) {
			return Component.literal("None").withStyle(ChatFormatting.GRAY);
		}
		return switch (affinity.get()) {
			case FURY -> Component.literal("Fury").withStyle(ChatFormatting.RED);
			case BASTION -> Component.literal("Bastion").withStyle(ChatFormatting.GOLD);
			case ZEPHYR -> Component.literal("Zephyr").withStyle(ChatFormatting.AQUA);
		};
	}

	/** A particle drifting up from the Altar — affinity-coloured once it has been attuned. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		AltarAffinity affinity = state.getValue(AFFINITY);
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		double y = pos.getY() + 1.05;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		ParticleOptions particle = affinity == AltarAffinity.NONE
			? ParticleTypes.ENCHANT
			: new DustParticleOptions(affinityColor(affinity), 1.0F);
		level.addParticle(particle, x, y, z, 0.0, 0.03, 0.0);
		tryProximityPulse(level, pos, random);
	}

	private static void tryProximityPulse(Level level, BlockPos pos, RandomSource random) {
		long now = level.getGameTime();
		ProximityPulseKey key = new ProximityPulseKey(level.dimension(), pos.asLong());
		pruneProximityPulseCooldowns(now);
		if (proximityPulseOnCooldown(key, now)) {
			return;
		}
		Player player = nearbyActiveFocusPlayer(level, pos);
		if (player == null) {
			return;
		}
		PROXIMITY_PULSE_TICKS.put(key, now);
		spawnProximityPulse(level, pos, random, proximityPulseParticle(player));
	}

	private static boolean proximityPulseOnCooldown(ProximityPulseKey key, long now) {
		Long lastPulse = PROXIMITY_PULSE_TICKS.get(key);
		return lastPulse != null
			&& now >= lastPulse
			&& now - lastPulse < PROXIMITY_PULSE_COOLDOWN_TICKS;
	}

	private static void pruneProximityPulseCooldowns(long now) {
		PROXIMITY_PULSE_TICKS.entrySet().removeIf(entry ->
			now < entry.getValue() || now - entry.getValue() > PROXIMITY_PULSE_RETENTION_TICKS);
	}

	private static Player nearbyActiveFocusPlayer(Level level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		double radiusSqr = PROXIMITY_PULSE_RADIUS * PROXIMITY_PULSE_RADIUS;
		for (Player player : level.players()) {
			if (player.isLocalPlayer()
					&& player.distanceToSqr(x, y, z) <= radiusSqr
					&& !Attunement.activeSlots(player).isEmpty()) {
				return player;
			}
		}
		return null;
	}

	private static void spawnProximityPulse(Level level, BlockPos pos, RandomSource random,
			ParticleOptions particle) {
		double centerX = pos.getX() + 0.5;
		double y = pos.getY() + 1.1;
		double centerZ = pos.getZ() + 0.5;
		double start = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < 10; i++) {
			double angle = start + (Math.PI * 2.0 * i / 10.0);
			double radius = 0.58 + random.nextDouble() * 0.08;
			level.addParticle(particle,
				centerX + Math.cos(angle) * radius,
				y + (random.nextDouble() - 0.5) * 0.08,
				centerZ + Math.sin(angle) * radius,
				0.0, 0.012, 0.0);
		}
		level.addParticle(ParticleTypes.ENCHANT, centerX, y + 0.12, centerZ, 0.0, 0.02, 0.0);
	}

	private static ParticleOptions proximityPulseParticle(Player player) {
		int rgb = AffinityColors.argbOf(
			Attunement.committedAffinity(player),
			Attunement.isDiscord(player)) & 0x00FFFFFF;
		return new DustParticleOptions(rgb, 1.0F);
	}

	private record ProximityPulseKey(ResourceKey<Level> dimension, long pos) {}

	private static AltarAffinity altarAffinityOf(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return AltarAffinity.NONE;
		}
		return switch (affinity.get()) {
			case FURY -> AltarAffinity.FURY;
			case BASTION -> AltarAffinity.BASTION;
			case ZEPHYR -> AltarAffinity.ZEPHYR;
		};
	}

	private static int affinityColor(AltarAffinity affinity) {
		return switch (affinity) {
			case FURY -> 0xFF5555;
			case BASTION -> 0xFFAA00;
			case ZEPHYR -> 0x55FFFF;
			case NONE -> 0xFFFFFF;
		};
	}
}
