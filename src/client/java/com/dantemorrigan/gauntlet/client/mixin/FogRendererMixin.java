package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
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
		if (level != null && level.dimension().equals(InfinitySnap.WHITE_VOID)) {
			// With no skybox, the fog color is also the world background.
			cir.getReturnValue().color.set(1f, 1f, 1f, 1f);
			return;
		}
		float red = level != null ? com.dantemorrigan.gauntlet.client.celestial.CelestialClient.redFactor(camera.position(), deltaTracker.getGameTimeDeltaPartialTick(false)) : 0;
		if (red > 0) {
			float day = CelestialClientAccess.daylight(deltaTracker.getGameTimeDeltaPartialTick(false));
			float[] d = CelestialClientAccess.fog(true), n = CelestialClientAccess.fog(false);
			FogData fog = cir.getReturnValue();
			fog.color.set(fog.color.x + (n[0] + (d[0] - n[0]) * day - fog.color.x) * red,
					fog.color.y + (n[1] + (d[1] - n[1]) * day - fog.color.y) * red,
					fog.color.z + (n[2] + (d[2] - n[2]) * day - fog.color.z) * red, fog.color.w);
		}
		float partialFog = deltaTracker.getGameTimeDeltaPartialTick(false);
		float thanosRed = com.dantemorrigan.gauntlet.client.boss.ThanosClient.skyTint(partialFog) * 0.27f;
		float thanosEnd = com.dantemorrigan.gauntlet.client.boss.ThanosClient.endLight(partialFog) * 0.2f;
		if (thanosRed > 0 || thanosEnd > 0) {
			FogData fog = cir.getReturnValue();
			float k = thanosRed > 0 ? thanosRed : thanosEnd;
			float r = thanosRed > 0 ? 0.59f : 0.67f, g = thanosRed > 0 ? 0.18f : 0.73f, b = thanosRed > 0 ? 0.22f : 0.93f;
			fog.color.set(fog.color.x + (r - fog.color.x) * k, fog.color.y + (g - fog.color.y) * k, fog.color.z + (b - fog.color.z) * k, fog.color.w);
		}
		float amber = com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.amber();
		if (amber > 0) {
			FogData fog = cir.getReturnValue();
			fog.color.set(fog.color.x + (0.86f - fog.color.x) * amber, fog.color.y + (0.47f - fog.color.y) * amber,
					fog.color.z + (0.16f - fog.color.z) * amber, fog.color.w);
		}
		float mist = com.dantemorrigan.gauntlet.client.quest.RealityFogClient.factor();
		if (mist > 0) {
			FogData fog = cir.getReturnValue();
			fog.color.set(fog.color.x + (0.55f - fog.color.x) * mist, fog.color.y + (0.03f - fog.color.y) * mist,
					fog.color.z + (0.06f - fog.color.z) * mist, fog.color.w);
			fog.environmentalStart = fog.environmentalStart + (0f - fog.environmentalStart) * mist;
			fog.environmentalEnd = fog.environmentalEnd + (20f - fog.environmentalEnd) * mist;
			fog.renderDistanceStart = Math.min(fog.renderDistanceStart, fog.renderDistanceStart + (0f - fog.renderDistanceStart) * mist);
			fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, fog.renderDistanceEnd + (24f - fog.renderDistanceEnd) * mist);
		}
        float storm = com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.skyFactor(camera.position(), partialFog);
        if (storm > 0) {
            FogData fog = cir.getReturnValue();
            fog.color.set(fog.color.x + (.12f - fog.color.x) * storm * .42f,
                    fog.color.y + (.21f - fog.color.y) * storm * .42f,
                    fog.color.z + (.37f - fog.color.z) * storm * .42f, fog.color.w);
        }
        float altitude = com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.atmosphere();
        if (altitude > 0) {
            FogData fog=cir.getReturnValue();
            fog.color.set(fog.color.x*(1-altitude),fog.color.y*(1-altitude),fog.color.z*(1-altitude),fog.color.w);
            fog.environmentalStart += altitude * 4000;
            fog.environmentalEnd += altitude * 4000;
            fog.renderDistanceStart += altitude * 4000;
            fog.renderDistanceEnd += altitude * 4000;
        }
		if (level != null && (level.dimension().equals(SpaceManager.DEAD_SPACE) || level.dimension().equals(com.dantemorrigan.gauntlet.cosmos.CosmosTravel.DIMENSION))) {
			FogData fog = cir.getReturnValue();
			fog.environmentalStart = fog.renderDistanceStart = 1.0e5f;
			fog.environmentalEnd = fog.renderDistanceEnd = 1.0e5f + 1;
			fog.skyEnd = fog.cloudEnd = 1.0e5f + 1;
		}
	}
}

