package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ancient clockwork mechanism in the Temple of Time.
 * Inserting the three dimensional gears (Overworld, Nether, End) advances {@link #GEARS} from 0 to 3
 * and releases the Time Stone.
 */
public final class TimeClockBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final IntegerProperty GEARS = IntegerProperty.create("gears", 0, 3);

	public TimeClockBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(GEARS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, GEARS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (TimeQuest.isGear(stack)) {
			if (player instanceof ServerPlayer sp) TimeQuest.insertGear(sp, pos, state, stack);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) TimeQuest.inspectOrInsertFromInventory(sp, pos, state);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int gears = state.getValue(GEARS);
		if (gears == 3 && random.nextInt(4) == 0) {
			Direction dir = state.getValue(FACING);
			double fx = pos.getX() + 0.5 + dir.getStepX() * 0.65 + (random.nextDouble() - 0.5) * 0.4;
			double fy = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
			double fz = pos.getZ() + 0.5 + dir.getStepZ() * 0.65 + (random.nextDouble() - 0.5) * 0.4;
			level.addParticle(ModParticles.QUEST_TIME_REWARD_SPIRAL, fx, fy, fz, 0.7, 0, 0);
			if (random.nextInt(5) == 0) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.15F, random.nextBoolean() ? 1.8F : 1.5F, false);
			}
		}
	}
}
