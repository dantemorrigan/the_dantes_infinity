package com.dantemorrigan.gauntlet.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A falling block anywhere, without {@code FallingBlockEntity.fall} replacing the block at that position. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityAccessor {
	@Invoker("<init>")
	static FallingBlockEntity infinityGauntlet$create(Level level, double x, double y, double z, BlockState state) {
		throw new AssertionError();
	}
}
