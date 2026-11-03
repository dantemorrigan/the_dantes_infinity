package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.GauntletGesture;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * "Admire the gauntlet": the hand is raised as a fist, turns palm up while the fingers spread, turns over so the
 * stones face the viewer, closes back into a fist and lowers. Keyframes follow the reference video of a real hand.
 * Purely visual: every client animates the players it was told about (the local one starts instantly).
 */
public final class GauntletGestureClient {
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.gesture", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, ClientPower.CATEGORY));

	/** Animation state at one moment. lift 0..1 raises the hand into view, roll = forearm twist in degrees
	 *  (0 = back of the hand outward, +180 = palm toward the viewer), open 0 = fist .. 1 = spread fingers,
	 *  tilt = extra raise of the forearm in degrees. */
	public record Pose(float lift, float roll, float open, float tilt) {}

	// t (0..1), lift, roll, open, tilt
	private static final float[][] KEYS = {
			{0.00f, 0f, 0f, 0f, 0f},
			{0.12f, 1f, 0f, 0f, 0f},   // fist raised, knuckles outward
			{0.20f, 1f, 0f, 0f, 0f},   // short hold
			{0.30f, 1f, 110f, 0.55f, 8f}, // wrist turns, fingers start opening
			{0.38f, 1f, 165f, 1f, 10f},  // open palm toward the viewer
			{0.50f, 1f, 170f, 1f, 10f},  // hold, look at the palm
			{0.62f, 1f, -10f, 1f, 4f},   // turn over: back of the hand, stones, fingers spread
			{0.70f, 1f, -15f, 1f, 4f},   // hold
			{0.80f, 1f, 70f, 0f, 12f},   // squeeze into a fist while turning it upright
			{0.90f, 1f, 75f, 0f, 12f},   // hold the fist
			{1.00f, 0f, 0f, 0f, 0f},
	};

	private static final Map<Integer, Long> STARTS = new HashMap<>();

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.GestureFx.TYPE, (fx, ctx) -> STARTS.put(fx.entityId(), Util.getMillis()));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> STARTS.clear());
		ClientTickEvents.END_CLIENT_TICK.register(GauntletGestureClient::tick);
	}

	private static void tick(Minecraft mc) {
		long now = Util.getMillis();
		STARTS.values().removeIf(start -> now - start > durationMs());
		boolean pressed = false;
		while (KEY.consumeClick()) pressed = true;
		if (pressed && mc.player != null && mc.screen == null) play();
	}

	/** Starts the gesture for the local player (if a gauntlet is worn) and tells the server so others see it. */
	public static void play() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || GauntletState.activeGauntlet(mc.player).isEmpty()) return;
		Long current = STARTS.get(mc.player.getId());
		if (current != null && Util.getMillis() - current < durationMs() - 500) return;
		STARTS.put(mc.player.getId(), Util.getMillis());
		if (ClientPlayNetworking.canSend(ModNetwork.GestureStart.TYPE)) ClientPlayNetworking.send(new ModNetwork.GestureStart());
	}

	private static long durationMs() { return GauntletGesture.DURATION * 50L; }

	/** Current pose of an entity's gesture, or null when it is not gesturing. */
	public static Pose pose(int entityId) {
		Long start = STARTS.get(entityId);
		if (start == null) return null;
		float t = (Util.getMillis() - start) / (float) durationMs();
		if (t < 0 || t >= 1) return null;
		return sample(t);
	}

	/** Test hook: freeze an entity's gesture at a given fraction (0..1). */
	public static void startAt(int entityId, float fraction) {
		STARTS.put(entityId, Util.getMillis() - (long) (fraction * durationMs()));
	}

	public static Pose sample(float t) {
		for (int i = 1; i < KEYS.length; i++) {
			float[] b = KEYS[i];
			if (t > b[0]) continue;
			float[] a = KEYS[i - 1];
			float k = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
			k = k * k * (3 - 2 * k);
			return new Pose(Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]), Mth.lerp(k, a[3], b[3]), Mth.lerp(k, a[4], b[4]));
		}
		return new Pose(0, 0, 0, 0);
	}

	private GauntletGestureClient() {}
}
