package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.GauntletGesture;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.ability.AbilityInput;
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
 * "Admire the gauntlet": the hand is lifted a little where it already is and the gauntlet is turned slightly
 * one way and the other, then lowered. Purely visual: every client animates the players it was told about
 * (the local one starts instantly).
 */
public final class GauntletGestureClient {
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.gesture", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, ClientPower.CATEGORY));

	/** Animation state at one moment: lift 0..1 (small raise), roll = forearm twist in degrees, tilt = extra raise in degrees. */
	public record Pose(float lift, float roll, float tilt) {}

	// t (0..1), lift, roll, tilt
	private static final float[][] ADMIRE = {
			{0.00f, 0f, 0f, 0f},
			{0.18f, 1f, 0f, 4f},     // lift the fist a little
			{0.38f, 1f, 24f, 6f},    // turn it slightly one way
			{0.62f, 1f, -18f, 6f},   // and the other
			{0.80f, 1f, 6f, 4f},     // settle
			{1.00f, 0f, 0f, 0f},
	};
	/** The Infinity Snap: a small wind-up turn, then a short flick on the click (server SNAP_TICK = 9 of 30). */
	private static final float[][] SNAP = {
			{0.00f, 0f, 0f, 0f},
			{0.22f, 1f, -10f, 3f},
			{0.30f, 1f, 9f, 6f},
			{0.65f, 1f, 6f, 5f},
			{1.00f, 0f, 0f, 0f},
	};
	private static final float[][][] KINDS = {ADMIRE, SNAP};
	private static final int[] DURATION_TICKS = {GauntletGesture.DURATION, 30};

	private record Run(long start, int kind) {}

	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static boolean checkedBindings;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.GestureFx.TYPE, (fx, ctx) -> {
			int kind = Math.floorMod(fx.kind(), KINDS.length);
			Run current = RUNS.get(fx.entityId());
			// The local admire gesture already started on key press; never restart it from the echo.
			if (kind == 0 && current != null && current.kind() == 0) return;
			RUNS.put(fx.entityId(), new Run(Util.getMillis(), kind));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> RUNS.clear());
		ClientTickEvents.END_CLIENT_TICK.register(GauntletGestureClient::tick);
	}

	private static void tick(Minecraft mc) {
		long now = Util.getMillis();
		RUNS.values().removeIf(run -> now - run.start() > durationMs(run.kind()));
		var favoriteKey = KeyMappingHelper.getBoundKeyOf(AbilityInput.NEXT_FAVORITE);
		var gestureKey = KeyMappingHelper.getBoundKeyOf(KEY);
		if (!checkedBindings) {
			checkedBindings = true;
			var oldDefault = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
			if (oldDefault.equals(gestureKey) && oldDefault.equals(favoriteKey)) {
				KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_I));
				KeyMapping.resetMapping();
				mc.options.save();
			}
		}
		boolean pressed = false;
		while (KEY.consumeClick()) pressed = true;
		if (pressed && !gestureKey.equals(favoriteKey) && mc.player != null && mc.screen == null) play();
	}

	/** Starts the admire gesture for the local player (if a working gauntlet is worn) and tells the server so others see it. */
	public static void play() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || GauntletState.activeGauntlet(mc.player).isEmpty()) return;
		Run current = RUNS.get(mc.player.getId());
		if (current != null && (current.kind() != 0 || Util.getMillis() - current.start() < durationMs(0) - 500)) return;
		RUNS.put(mc.player.getId(), new Run(Util.getMillis(), 0));
		if (ClientPlayNetworking.canSend(ModNetwork.GestureStart.TYPE)) ClientPlayNetworking.send(new ModNetwork.GestureStart());
	}

	private static long durationMs(int kind) { return DURATION_TICKS[kind] * 50L; }

	/** Current pose of an entity's gesture, or null when it is not gesturing. */
	public static Pose pose(int entityId) {
		Run run = RUNS.get(entityId);
		if (run == null) return null;
		float t = (Util.getMillis() - run.start()) / (float) durationMs(run.kind());
		if (t < 0 || t >= 1) return null;
		return sample(KINDS[run.kind()], t);
	}

	/** Test hook: freeze an entity's gesture of the given kind at a fraction (0..1). */
	public static void startAt(int entityId, int kind, float fraction) {
		RUNS.put(entityId, new Run(Util.getMillis() - (long) (fraction * durationMs(kind)), kind));
	}

	public static void startAt(int entityId, float fraction) {
		startAt(entityId, 0, fraction);
	}

	private static Pose sample(float[][] keys, float t) {
		for (int i = 1; i < keys.length; i++) {
			float[] b = keys[i];
			if (t > b[0]) continue;
			float[] a = keys[i - 1];
			float k = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
			k = k * k * (3 - 2 * k);
			return new Pose(Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]), Mth.lerp(k, a[3], b[3]));
		}
		return new Pose(0, 0, 0);
	}

	private GauntletGestureClient() {}
}
