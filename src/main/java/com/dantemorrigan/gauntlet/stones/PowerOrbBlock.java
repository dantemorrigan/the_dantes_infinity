package com.dantemorrigan.gauntlet.stones;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Orb sealed inside the purple meteorite. Breaking it drops the Orb item (see PowerStoneFall). */
public final class PowerOrbBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(3, 3, 3, 13, 13, 13);

	public PowerOrbBlock(Properties properties) { super(properties); }

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
