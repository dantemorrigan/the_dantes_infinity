package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The icy rift: in the End (after the dragon) it leads to the frozen asteroid, on the asteroid it leads back to the End.
 * A thin pane in the X/Y plane, walk-through, unbreakable.
 */
public final class FrostPortalBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(0, 0, 6, 16, 16, 10);

	public FrostPortalBlock(Properties properties) { super(properties); }

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity instanceof ServerPlayer player) SpaceStoneQuest.enterPortal(player);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(18) == 0) {
			level.addParticle(ModParticles.QUEST_SPACE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.4 + random.nextFloat(), 0, 0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ModParticles.QUEST_SPACE_FROST_SHARD, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5, 0, 0, 0);
		}
	}
}
