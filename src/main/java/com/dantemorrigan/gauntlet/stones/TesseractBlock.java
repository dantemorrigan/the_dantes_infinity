package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Tesseract resting on the pedestal at the top of the ice tower. Right-click takes it. */
public final class TesseractBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(4, 2, 4, 12, 10, 12);

	public TesseractBlock(Properties properties) { super(properties); }

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) SpaceStoneQuest.takeTesseract(sp, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ModParticles.SPACE_ENERGY_WISPS, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 0.4 + random.nextDouble() * 0.6,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0.6, 0, 0);
		}
	}
}
