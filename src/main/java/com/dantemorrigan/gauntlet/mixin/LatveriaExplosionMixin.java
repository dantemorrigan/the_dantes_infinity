package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.doom.Latveria;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Blasts in Latveria hurt whoever stands in them but leave the masonry, and drop nothing from it. */
@Mixin(ServerExplosion.class)
public abstract class LatveriaExplosionMixin {
	@Shadow @Final private ServerLevel level;

	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> infinityGauntlet$spareLatveria(List<BlockPos> blocks) {
		if (this.level.dimension() != Latveria.LEVEL) return blocks;
		List<BlockPos> kept = new java.util.ArrayList<>(blocks.size());
		for (BlockPos p : blocks) if (!Latveria.protects(this.level, p, Blocks.AIR.defaultBlockState())) kept.add(p);
		return kept;
	}
}
