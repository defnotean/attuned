package dev.attuned.content;

import com.mojang.serialization.MapCodec;
import dev.attuned.AttunedConfig;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

	// A wide base under a narrower column — an altar/pedestal silhouette.
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
		Block.box(3.0, 4.0, 3.0, 13.0, 16.0, 13.0));

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
		if (!stack.is(AttunedContent.ATTUNEMENT_SHARD)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int cap = AttunedConfig.get().capacityCap();
		int capacity = AttunedAttachments.getCapacity(player);
		if (capacity >= cap) {
			player.sendSystemMessage(Component.literal("Your attunement is already at its fullest.")
				.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS_SERVER;
		}
		int raised = Math.min(cap, capacity + AttunedConfig.get().capacityPerShard());
		AttunedAttachments.setCapacity(player, raised);
		stack.shrink(1);
		level.setBlock(pos, state.setValue(AFFINITY, altarAffinityOf(Attunement.committedAffinity(player))),
			Block.UPDATE_CLIENTS);

		ServerLevel server = (ServerLevel) level;
		server.sendParticles(ParticleTypes.ENCHANT,
			pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
		server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.literal("The Altar binds the shard — capacity ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(raised + " / " + cap).withStyle(ChatFormatting.AQUA)));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Right-click empty-handed: report the player's attunement state. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.sendSystemMessage(Component.literal("Attunement: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(Attunement.used(player) + " / " + Attunement.capacity(player))
				.withStyle(ChatFormatting.AQUA))
			.append(Component.literal("    Affinity: ").withStyle(ChatFormatting.GRAY))
			.append(affinityLabel(Attunement.committedAffinity(player))));
		Apex.affinityOf(player).ifPresent(apex ->
			player.sendSystemMessage(Component.literal("Apex: " + Apex.capstoneName(apex))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		return InteractionResult.SUCCESS;
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
	}

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

	private static Component affinityLabel(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return Component.literal("None").withStyle(ChatFormatting.GRAY);
		}
		return switch (affinity.get()) {
			case FURY -> Component.literal("Fury").withStyle(ChatFormatting.RED);
			case BASTION -> Component.literal("Bastion").withStyle(ChatFormatting.GOLD);
			case ZEPHYR -> Component.literal("Zephyr").withStyle(ChatFormatting.AQUA);
		};
	}
}
