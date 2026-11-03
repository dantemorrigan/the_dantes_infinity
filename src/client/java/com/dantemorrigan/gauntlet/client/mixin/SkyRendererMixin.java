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
        float altitude = com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.atmosphere();
        if (altitude > 0) {
            state.skyColor = ARGB.srgbLerp(altitude, state.skyColor, 0xFF000008);
            state.sunriseAndSunsetColor = ARGB.srgbLerp(altitude,state.sunriseAndSunsetColor,0);
            state.starBrightness = Math.max(state.starBrightness,altitude);
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
		CelestialSky.drawRedSky(poseStack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void infinityGauntlet$brokenBody(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness, CallbackInfo ci) {
		CelestialSky.drawBody(poseStack, sunAngle, moonAngle, moonPhase, rainBrightness,
				Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (CelestialSky.hideVanilla("sun")) ci.cancel();
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$hideMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (CelestialSky.hideVanilla("moon")) ci.cancel();
	}

	/** Dead Space uses skybox "end" only to get a sky pass; we replace the End sky with the space cube-map. */
	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$deadSpaceSky(CallbackInfo ci) {
        if (com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.inCosmos()) {
            com.dantemorrigan.gauntlet.client.cosmos.CosmosRenderer.sky(1);
            ci.cancel(); return;
        }
		if (com.dantemorrigan.gauntlet.client.space.SpaceScene.active()) {
			com.dantemorrigan.gauntlet.client.space.SpaceScene.drawSkyPass();
			ci.cancel();
		}
	}

	@Inject(method = "renderEndFlash", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$noEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle, CallbackInfo ci) {
		if (com.dantemorrigan.gauntlet.client.space.SpaceScene.active() || com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.inCosmos()) ci.cancel();
	}
}

