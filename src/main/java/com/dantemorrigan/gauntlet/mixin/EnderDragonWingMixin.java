package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnderDragon.class)
abstract class EnderDragonWingMixin {
	/** The Stormbreaker wielder holds their ground against the wing beats: the dragon can be approached and fought. */
	@Inject(method = "knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At("HEAD"))
	private void infinityGauntlet$wielderHoldsGround(ServerLevel level, List<Entity> entities, CallbackInfo ci) {
		try {
			entities.removeIf(entity -> entity instanceof ServerPlayer player && Stormbreaker.guarded(player));
		} catch (UnsupportedOperationException ignored) {
			// An immutable list cannot be filtered; the vanilla behavior then simply applies.
		}
	}
}
