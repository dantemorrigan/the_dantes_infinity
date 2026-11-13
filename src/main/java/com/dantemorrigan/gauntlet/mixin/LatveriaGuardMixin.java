package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.doom.Latveria;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Latveria's city, castle and ground refuse every change that is not Latveria's own: beams, stones, flights, pistons. */
@Mixin(Level.class)
public abstract class LatveriaGuardMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$guardSet(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		if (level.isClientSide() || level.dimension() != Latveria.LEVEL) return;
		if (Latveria.protects(level, pos, state)) cir.setReturnValue(false);
		else Latveria.changed(level, pos, level.getBlockState(pos), state);
	}

	/** Before the drops: a refused break must not hand out the block anyway. */
	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$guardDestroy(BlockPos pos, boolean drop, Entity by, int recursion, CallbackInfoReturnable<Boolean> cir) {
		if (Latveria.protects((Level) (Object) this, pos, Blocks.AIR.defaultBlockState())) cir.setReturnValue(false);
	}
}
