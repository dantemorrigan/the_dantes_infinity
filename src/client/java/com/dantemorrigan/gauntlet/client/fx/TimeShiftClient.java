package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Time stone day/night switch: key, time-tint with a spinning clock ring while the sky races. */
public final class TimeShiftClient {
	public static final KeyMapping SHIFT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.time_shift", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));

	private static int ticks, total;
	private static boolean toNight;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(TimeShiftClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.TimeShiftFx.TYPE, (fx, ctx) -> {
			ticks = total = fx.duration();
			toNight = fx.toNight();
			Cutscene.shake(0.12f, fx.duration());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> ticks = 0));
		HudElementRegistry.addLast(InfinityGauntlet.id("time_shift"), (g, d) -> overlay(g, d.getGameTimeDeltaPartialTick(false)));
	}

	private static void tick(Minecraft mc) {
		while (SHIFT.consumeClick()) {
			if (mc.player != null) ClientPlayNetworking.send(new ModNetwork.TimeShiftRequest());
		}
		if (mc.level == null || !mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) ticks = 0;
		else if (ticks > 0) ticks--;
	}

	private static void overlay(GuiGraphicsExtractor g, float partial) {
		if (ticks <= 0) return;
		int w = g.guiWidth(), h = g.guiHeight();
		float age = total - ticks + partial;
		float x = Mth.clamp(age / Math.max(1, total), 0f, 1f);
		float strength = Mth.sin(x * Mth.PI); // fades in and out
		// Warm gold toward day, cold violet toward night; the middle of the race is the most intense.
		float r = toNight ? 0.45f : 1f, gr = toNight ? 0.4f : 0.85f, b = toNight ? 1f : 0.45f;
		g.fill(0, 0, w, h, ARGB.colorFromFloat(0.16f * strength, r, gr, b));
		int band = Math.max(8, h / 9);
		for (int i = 0; i < 4; i++) {
			int o = band * i / 4;
			int col = ARGB.colorFromFloat(0.10f * strength, r * 0.5f, gr * 0.5f, b * 0.5f);
			g.fill(0, o, w, o + band / 4, col);
			g.fill(0, h - o - band / 4, w, h - o, col);
		}
		// Clock ring: 12 marks spinning faster toward the middle of the race.
		int cx = w / 2, cy = h / 2;
		float radius = Math.min(w, h) * 0.34f;
		float rot = age * 0.12f * (1f + 3f * strength);
		for (int i = 0; i < 12; i++) {
			double a = rot + i * Math.PI / 6;
			int px = cx + (int) (Math.cos(a) * radius), py = cy + (int) (Math.sin(a) * radius);
			int s = i % 3 == 0 ? 3 : 2;
			g.fill(px - s, py - s, px + s, py + s, ARGB.colorFromFloat(0.7f * strength, 0.7f + 0.3f * r, 1f, 0.8f));
		}
	}

	private TimeShiftClient() {}
}
