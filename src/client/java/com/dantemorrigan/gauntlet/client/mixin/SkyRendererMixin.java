package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.celestial.CelestialClient;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.client.celestial.CelestialClientAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Celestial Apocalypse in the sky pass: red sky, broken sun/moon, hidden destroyed bodies. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void infinityGauntlet$tint(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float storm = com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.skyFactor(camera.position(), partialTicks);
        if (storm > 0) {
            state.skyColor = ARGB.srgbLerp(storm * .97f, state.skyColor, 0xFF121827);
            state.sunriseAndSunsetColor = ARGB.srgbLerp(storm, state.sunriseAndSunsetColor, 0);
            state.starBrightness *= 1 - storm;
        }
        float amber = com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.amber();
        if (amber > 0) {
            state.skyColor = ARGB.srgbLerp(amber, state.skyColor, 0xFFE07A22);
            state.sunriseAndSunsetColor = ARGB.srgbLerp(amber, state.sunriseAndSunsetColor, 0xFFFF9A3C);
        }
        float altitude = com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.atmosphere();
        if (altitude > 0) {
            state.skyColor = ARGB.srgbLerp(altitude, state.skyColor, 0xFF000008);
            state.sunriseAndSunsetColor = ARGB.srgbLerp(altitude,state.sunriseAndSunsetColor,0);
            state.starBrightness = Math.max(state.starBrightness,altitude);
        }
		float thanos = com.dantemorrigan.gauntlet.client.boss.ThanosClient.skyTint(partialTicks);
		if (thanos > 0) {
			// Sanctuary II overhead: a dangerous red cast over the sky, not a full red sky.
			state.skyColor = ARGB.srgbLerp(0.32f * thanos, state.skyColor, 0xFF972F38);
			state.sunriseAndSunsetColor = ARGB.srgbLerp(0.5f * thanos, state.sunriseAndSunsetColor, ARGB.color(ARGB.alpha(state.sunriseAndSunsetColor), 200, 40, 30));
		}
		float f = CelestialClient.redFactor(camera.position(), partialTicks);
		if (f <= 0) return;
		float day = CelestialClientAccess.daylight(partialTicks);
		float[] d = CelestialClientAccess.sky(true), n = CelestialClientAccess.sky(false);
		int red = ARGB.colorFromFloat(1f, lerp(day, n[0], d[0]), lerp(day, n[1], d[1]), lerp(day, n[2], d[2]));
		state.skyColor = ARGB.srgbLerp(f, state.skyColor, red);
		state.sunriseAndSunsetColor = ARGB.srgbLerp(f, state.sunriseAndSunsetColor, ARGB.color(ARGB.alpha(state.sunriseAndSunsetColor), 230, 60, 20));
		state.starBrightness *= 1 - f * (1 - lerp(day, CelestialClientAccess.stars(), 0f));
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void infinityGauntlet$redSky(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness, CallbackInfo ci) {
        com.dantemorrigan.gauntlet.client.cosmos.CosmosRenderer.sky(com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.atmosphere());
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CelestialSky.drawRedSky(poseStack, partial);
        com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.drawClouds(poseStack, partial);
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void infinityGauntlet$brokenBody(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness, CallbackInfo ci) {
		CelestialSky.drawBody(poseStack, sunAngle, moonAngle, moonPhase, rainBrightness,
				Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (!CelestialSky.hideVanilla("sun")) com.dantemorrigan.gauntlet.client.boss.ThanosClient.drawRedSun(poseStack, sunAngle, rainBrightness,
				Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (CelestialSky.hideVanilla("sun") || com.dantemorrigan.gauntlet.client.boss.ThanosClient.hideSun()
                || com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.activeSky(0) > .32f) ci.cancel();
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$hideMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (CelestialSky.hideVanilla("moon")
                || com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.activeSky(0) > .32f) ci.cancel();
	}

	/** Dead Space uses skybox "end" only to get a sky pass; we replace the End sky with the space cube-map. */
	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$deadSpaceSky(CallbackInfo ci) {
		if (com.dantemorrigan.gauntlet.client.doom.LatveriaSky.active()) {
			com.dantemorrigan.gauntlet.client.doom.LatveriaSky.draw();
			ci.cancel();
			return;
		}
		if (com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.inSoulWorld()) {
			com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.drawSky();
			ci.cancel();
			return;
		}
		if (com.dantemorrigan.gauntlet.client.quest.SpaceQuestClient.onAsteroid()) {
			com.dantemorrigan.gauntlet.client.quest.SpaceQuestClient.drawSky();
			ci.cancel();
			return;
		}
        if (com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.inCosmos()) {
            com.dantemorrigan.gauntlet.client.cosmos.CosmosRenderer.sky(1);
            ci.cancel(); return;
        }
		if (com.dantemorrigan.gauntlet.client.space.SpaceScene.active()) {
			com.dantemorrigan.gauntlet.client.space.SpaceScene.drawSkyPass();
			ci.cancel();
		}
	}

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void infinityGauntlet$stormEndSky(CallbackInfo ci) {
        com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.drawClouds(
                new PoseStack(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

	@Inject(method = "renderEndFlash", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$noEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle, CallbackInfo ci) {
		if (com.dantemorrigan.gauntlet.client.space.SpaceScene.active() || com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.inCosmos()
				|| com.dantemorrigan.gauntlet.client.quest.SpaceQuestClient.onAsteroid() || com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.inSoulWorld()) ci.cancel();
	}
}

