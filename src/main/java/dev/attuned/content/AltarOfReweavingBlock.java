package dev.attuned.content;

import com.mojang.serialization.MapCodec;
import dev.attuned.menu.ReweavingMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Altar of Reweaving is a separate crafting altar for turning three old
 * Focus patterns and one shard fragment into a new Focus.
 */
public class AltarOfReweavingBlock extends Block {
	public static final MapCodec<AltarOfReweavingBlock> CODEC = simpleCodec(AltarOfReweavingBlock::new);

	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
		Block.box(1.0, 2.0, 1.0, 15.0, 4.0, 15.0),
		Block.box(2.0, 4.0, 2.0, 14.0, 14.0, 14.0),
		Block.box(1.0, 14.0, 1.0, 15.0, 16.0, 15.0));

	public AltarOfReweavingBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.openMenu(ReweavingMenuType.provider(level, pos));
		return InteractionResult.SUCCESS;
	}
}
