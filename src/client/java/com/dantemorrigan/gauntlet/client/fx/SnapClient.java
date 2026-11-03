package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Infinity Snap visuals: the world fades to white after the click, the White Void fades in from pure white,
 * a white-green flash when the Time stone brings everyone back, and smoke rising from a burnt-out gauntlet.
 */
public final class SnapClient {
	private static float fadeIn, fadeInStep;     // to white (before the universe is erased)
	private static float fadeOut, fadeOutStep;   // from white (arrival in the void / back home)
	private static int green;                    // rewind tint after the Time stone is used
	private static int held;                     // safety: never stay white if the arrival packet never comes

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SnapFx.TYPE, (fx, ctx) -> start(fx.phase(), fx.duration()));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(SnapClient::tick);
		HudElementRegistry.addLast(InfinityGauntlet.id("infinity_snap"), (g, d) -> overlay(g));
	}

	private static void reset() {
		fadeIn = fadeInStep = fadeOut = fadeOutStep = 0;
		held = 0;
		green = 0;
	}

	private static void start(int phase, int duration) {
		int ticks = Math.max(1, duration);
		switch (phase) {
			case 0 -> {fadeIn = 0.001f; fadeInStep = 1f / ticks; com.dantemorrigan.gauntlet.client.cutscene.Cutscene.shake(0.35f, ticks);}
			case 1 -> {fadeIn = 0; fadeOut = 1; fadeOutStep = 1f / ticks;}
			case 2 -> green = ticks;
			case 3 -> {green = 0; fadeOut = 1; fadeOutStep = 1f / ticks;}
			default -> {}
		}
	}

	private static void tick(Minecraft mc) {
		if (fadeIn > 0 && fadeIn < 1) fadeIn = Math.min(1, fadeIn + fadeInStep);
		if (fadeIn >= 1 && ++held > 100) {fadeIn = 0; fadeOut = 1; fadeOutStep = 0.05f;}
		if (fadeIn < 1) held = 0;
		if (fadeOut > 0) fadeOut = Math.max(0, fadeOut - fadeOutStep);
		if (green > 0) green--;
		if (mc.level == null || mc.isPaused()) return;
		// A burnt-out gauntlet keeps smoking.
		RandomSource rnd = mc.level.getRandom();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.distanceToSqr(mc.gameRenderer.getMainCamera().position()) > 64 * 64) continue;
			if (!InfinitySnap.snapped(GauntletState.wornGauntlet(p))) continue;
			boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if (rnd.nextInt(firstPerson ? 5 : 3) != 0) continue;
			Vec3 fist;
			if (firstPerson) {
				// Far enough from the camera that the puffs stay small, rising past the lower-left hand.
				float yaw = p.getViewYRot(1f) * Mth.DEG_TO_RAD;
				Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1);
				Vec3 flat = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
				fist = p.getEyePosition(1f).add(flat.scale(1.9)).add(left.scale(1.0)).add(0, -0.85, 0);
			} else fist = FistPosition.of(p, 1f);
			double x = fist.x + rnd.nextGaussian() * 0.06, y = fist.y + rnd.nextDouble() * 0.1, z = fist.z + rnd.nextGaussian() * 0.06;
			mc.level.addParticle(!firstPerson && rnd.nextInt(5) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z,
					rnd.nextGaussian() * 0.004, 0.025 + rnd.nextDouble() * 0.02, rnd.nextGaussian() * 0.004);
			if (rnd.nextInt(12) == 0) mc.level.addParticle(ParticleTypes.ASH, x, y, z, 0, 0.01, 0);
		}
	}

	private static void overlay(GuiGraphicsExtractor g) {
		int w = g.guiWidth(), h = g.guiHeight();
		if (green > 0) {
			float pulse = 0.5f + 0.5f * Mth.sin(green * 0.4f);
			g.fill(0, 0, w, h, ARGB.colorFromFloat(0.22f + 0.1f * pulse, 0.1f, 0.9f, 0.35f));
		}
		float white = Math.max(fadeIn > 0 ? Math.min(1, fadeIn * fadeIn) : 0, fadeOut);
		if (white > 0.002f) g.fill(0, 0, w, h, ARGB.colorFromFloat(white, 1f, 1f, 1f));
	}

	/** True while the screen is (fading) white because of the snap. */
	public static boolean whiteOut() {
		return fadeIn > 0 || fadeOut > 0;
	}

	private SnapClient() {}
}
