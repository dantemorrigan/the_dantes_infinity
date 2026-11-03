package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.client.celestial.CelestialClientAccess;
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
		float red = level != null ? com.dantemorrigan.gauntlet.client.celestial.CelestialClient.redFactor(camera.position(), deltaTracker.getGameTimeDeltaPartialTick(false)) : 0;
		if (red > 0) {
			float day = CelestialClientAccess.daylight(deltaTracker.getGameTimeDeltaPartialTick(false));
			float[] d = CelestialClientAccess.fog(true), n = CelestialClientAccess.fog(false);
			FogData fog = cir.getReturnValue();
			fog.color.set(fog.color.x + (n[0] + (d[0] - n[0]) * day - fog.color.x) * red,
					fog.color.y + (n[1] + (d[1] - n[1]) * day - fog.color.y) * red,
					fog.color.z + (n[2] + (d[2] - n[2]) * day - fog.color.z) * red, fog.color.w);
		}
		if (level != null && level.dimension().equals(SpaceManager.DEAD_SPACE)) {
			FogData fog = cir.getReturnValue();
			fog.environmentalStart = fog.renderDistanceStart = 1.0e5f;
			fog.environmentalEnd = fog.renderDistanceEnd = 1.0e5f + 1;
			fog.skyEnd = fog.cloudEnd = 1.0e5f + 1;
		}
	}
}
