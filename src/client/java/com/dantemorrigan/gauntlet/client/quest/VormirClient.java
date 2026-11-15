package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Vormir: while the Soul Stone still waits in its temple, the land round the mountain loses its colour. The nearer you
 * come the greyer, dimmer and more orange-ashen the world gets, with a short fog and drifting ash, like the film's
 * Vormir. It lets go the moment the stone has been taken.
 */
public final class VormirClient {
	private static final double FULL = 90, FAR = 260;
	private static BlockPos site;
	private static boolean active;
	private static float factor;

	private VormirClient() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SoulVormir.TYPE, (p, ctx) -> { site = p.site(); active = p.active(); });
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { site = null; active = false; factor = 0; });
		ClientTickEvents.END_CLIENT_TICK.register(VormirClient::tick);
		HudElementRegistry.addFirst(InfinityGauntlet.id("vormir"), (g, delta) -> overlay(g));
	}

	/** 0..1: how much of Vormir lies over the player right now. */
	public static float factor() { return factor; }

	private static void tick(Minecraft mc) {
		float target = 0;
		if (mc.level != null && mc.player != null && active && site != null && mc.level.dimension().equals(Level.OVERWORLD)) {
			double d = Math.hypot(mc.player.getX() - (site.getX() + 0.5), mc.player.getZ() - (site.getZ() + 0.5));
			float t = Mth.clamp((float) ((FAR - d) / (FAR - FULL)), 0, 1);
			target = t * t * (3 - 2 * t);
		}
		factor += (target - factor) * (target > factor ? 0.04f : 0.06f);
		if (Math.abs(target - factor) < 0.002f) factor = target;
		// Ash drifting down through the grey air.
		if (factor > 0.25f && mc.level != null && mc.player != null && !mc.isPaused()) {
			var rnd = mc.level.getRandom();
			int n = factor > 0.7f ? 3 : 1;
			for (int i = 0; i < n; i++) {
				mc.level.addParticle(ParticleTypes.ASH, mc.player.getX() + rnd.nextGaussian() * 14, mc.player.getY() + 3 + rnd.nextDouble() * 9,
						mc.player.getZ() + rnd.nextGaussian() * 14, 0, -0.03, 0);
			}
		}
	}

	/** A warm grey wash and darkened edges over the picture, under the HUD. */
	private static void overlay(GuiGraphicsExtractor g) {
		if (factor <= 0.01f) return;
		int w = g.guiWidth(), h = g.guiHeight();
		g.fill(0, 0, w, h, ARGB.color((int) (factor * 86), 92, 74, 58));
		int band = Math.max(24, Math.min(w, h) / 3);
		for (int i = 0; i < 8; i++) {
			int a = (int) (factor * 70 * (1 - i / 8f));
			int c = ARGB.color(a, 18, 12, 8);
			int t = band * i / 8, u = band / 8 + 1;
			g.fill(t, 0, w - t, u + 0, c);
			g.fill(t, h - u, w - t, h, c);
		}
		g.fillGradient(0, 0, band, h, ARGB.color((int) (factor * 80), 18, 12, 8), 0);
		g.fillGradient(w - band, 0, w, h, 0, ARGB.color((int) (factor * 80), 18, 12, 8));
	}
}
