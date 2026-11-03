package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dead Space has no atmosphere: no distance/environment fog, so the far planet and the sky stay visible. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void infinityGauntlet$noFogInSpace(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		if (level != null && level.dimension().equals(SpaceManager.DEAD_SPACE)) {
			FogData fog = cir.getReturnValue();
			fog.environmentalStart = fog.renderDistanceStart = 1.0e5f;
			fog.environmentalEnd = fog.renderDistanceEnd = 1.0e5f + 1;
			fog.skyEnd = fog.cloudEnd = 1.0e5f + 1;
		}
	}
}
