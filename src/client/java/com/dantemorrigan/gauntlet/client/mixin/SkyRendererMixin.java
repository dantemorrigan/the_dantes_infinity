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
}
