package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.space.SpaceScene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Time stone rewind visuals: green time-tint with clock ticks, reversed portal particles spiralling INTO the
 * player, ghost trail, and for the world rewind the planet reassembling followed by a green flash.
 */
public final class TimeRewindClient {
	public static final KeyMapping REWIND = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.time_rewind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, ClientPower.CATEGORY));

	private static final int GREEN = 0x28C850;
	/** entity id -> ticks left of rewind particles. */
	private static final Map<Integer, Integer> REWINDING = new HashMap<>();
	private static int localTicks, localDuration;
	private static boolean localWorld;
	private static Level localLevel;
	/** Green flash after the world rewind lands the player back home (fades out). */
	private static float afterFlash;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(TimeRewindClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.TimeRewindFx.TYPE, (fx, ctx) -> start(fx.entityId(), fx.world(), fx.duration()));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(TimeRewindClient::reset));
		HudElementRegistry.addLast(InfinityGauntlet.id("time_rewind"), (g, d) -> overlay(g, d.getGameTimeDeltaPartialTick(false)));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
			if (localWorld && localTicks > 0 || afterFlash > 0) overlay(g, delta);
		}));
	}

	public static boolean inputLocked() {
		return localTicks > 0;
	}

	private static void reset() {
		REWINDING.clear();
		localTicks = localDuration = 0;
		localWorld = false;
		afterFlash = 0;
		SpaceScene.stopRewind();
	}

	private static void start(int entityId, boolean world, int duration) {
		Minecraft mc = Minecraft.getInstance();
		REWINDING.put(entityId, duration);
		if (mc.player != null && mc.player.getId() == entityId) {
			localTicks = localDuration = duration;
			localWorld = world;
			localLevel = mc.level;
			Cutscene.shake(0.25f, duration);
			if (world) SpaceScene.startRewind(duration);
		}
	}

	private static void tick(Minecraft mc) {
		while (REWIND.consumeClick()) {
			if (mc.player != null) ClientPlayNetworking.send(new ModNetwork.TimeRewindRequest());
		}
		if (afterFlash > 0) afterFlash = Math.max(0, afterFlash - 0.04f);
		if (mc.level == null) return;
		if (localTicks > 0) {
			localTicks--;
			if (localWorld && mc.level != localLevel) {
				// Landed back in the restored world.
				localTicks = 0;
				afterFlash = 1f;
				SpaceScene.stopRewind();
			}
		}
		RandomSource rnd = mc.level.getRandom();
		REWINDING.entrySet().removeIf(e -> {
			Entity entity = mc.level.getEntity(e.getKey());
			if (entity != null) particles(mc, entity, rnd);
			e.setValue(e.getValue() - 1);
			return e.getValue() <= 0;
		});
	}

	private static void particles(Minecraft mc, Entity entity, RandomSource rnd) {
		Vec3 c = entity.position().add(0, 1, 0);
		float t = mc.level.getGameTime();
		// Reverse-portal motes collapsing inward + green time dust on a counter-rotating clock ring.
		for (int i = 0; i < 6; i++) {
			double a = -t * 0.35 + i * Math.PI / 3;
			Vec3 p = c.add(Math.cos(a) * 1.3, Math.sin(t * 0.2 + i) * 0.8, Math.sin(a) * 1.3);
			mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, (c.x - p.x) * 0.08, 0.02, (c.z - p.z) * 0.08);
			mc.level.addParticle(new DustParticleOptions(GREEN, 1.2f), p.x, p.y, p.z, 0, 0, 0);
		}
		if (rnd.nextInt(3) == 0) ClientPower.spawn(ModParticles.VOID_DISSOLVE, c.add(rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6), 0.5, 0, 0);
	}

	/** Green time tint, clock tick marks around the edge, white-green flash at the end of a world rewind. */
	private static void overlay(GuiGraphicsExtractor g, float partial) {
		int w = g.guiWidth(), h = g.guiHeight();
		if (afterFlash > 0) {
			g.fill(0, 0, w, h, ARGB.colorFromFloat(afterFlash * GauntletConfig.get().flashIntensity, 0.85f, 1f, 0.9f));
			return;
		}
		if (localTicks <= 0) return;
		float progress = 1f - (localTicks - partial) / Math.max(1, localDuration);
		float pulse = 0.5f + 0.5f * Mth.sin((localDuration - localTicks + partial) * 0.9f);
		g.fill(0, 0, w, h, ARGB.colorFromFloat(0.18f + 0.08f * pulse, 0.1f, 0.9f, 0.35f));
		// Edge vignette
		int band = Math.max(8, h / 10);
		for (int i = 0; i < 4; i++) {
			int o = band * i / 4;
			int col = ARGB.colorFromFloat(0.12f, 0f, 0.25f, 0.08f);
			g.fill(0, o, w, o + band / 4, col);
			g.fill(0, h - o - band / 4, w, h - o, col);
		}
		// Clock: 12 tick marks rotating backwards around the screen center.
		int cx = w / 2, cy = h / 2;
		float r = Math.min(w, h) * 0.32f;
		float rot = -(localDuration - localTicks + partial) * 0.25f;
		for (int i = 0; i < 12; i++) {
			double a = rot + i * Math.PI / 6;
			int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
			int s = i % 3 == 0 ? 3 : 2;
			g.fill(x - s, y - s, x + s, y + s, ARGB.colorFromFloat(0.7f, 0.6f, 1f, 0.7f));
		}
		if (localWorld && progress > 0.75f) {
			float a = (progress - 0.75f) / 0.25f;
			g.fill(0, 0, w, h, ARGB.colorFromFloat(Math.min(1f, a) * GauntletConfig.get().flashIntensity, 0.85f, 1f, 0.9f));
		}
	}

	private TimeRewindClient() {}
}
