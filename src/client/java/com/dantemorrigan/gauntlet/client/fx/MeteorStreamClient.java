package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.meteor.MeteorStream;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Meteor Stream visuals. The caster sees a terrain- and water-adaptive artillery reticle follow their aim on the
 * ground or water; while a stream runs, everyone within sight sees its circle (a danger zone) and the sky turns red
 * over it.
 */
public final class MeteorStreamClient {
	private static final int GRID = 20;
	private static final Identifier[] RETICLE_TEXTURES = {
			InfinityGauntlet.id("textures/particle/meteor_reticle_0.png"),
			InfinityGauntlet.id("textures/particle/meteor_reticle_1.png"),
			InfinityGauntlet.id("textures/particle/meteor_reticle_2.png"),
			InfinityGauntlet.id("textures/particle/meteor_reticle_3.png")
	};

	private static final class Marker {
		Vec3 center;
		float radius;
		boolean solar;
		long startTick, endTick;
	}

	/** A Power meteor shower: the sky (and the sun) take on a red cast around it. */
	private record Shower(Vec3 center, long start, long end) {}

	private static final List<Shower> SHOWERS = new ArrayList<>();
	private static final List<Marker> MARKERS = new ArrayList<>();
	private static long clientTicks;
	/** Aim marker of the local player (valid = the surface point can be used), refreshed by the server and tracked locally every tick. */
	private static Vec3 aimCenter;
	private static boolean aimValid;
	private static float aimRadius = 24;
	private static long aimUntil;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.MeteorStreamFx.TYPE, (fx, ctx) -> onFx(fx));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PowerShowerFx.TYPE, (fx, ctx) ->
				SHOWERS.add(new Shower(net.minecraft.world.phys.Vec3.atCenterOf(fx.center()), clientTicks, clientTicks + fx.ticks())));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {MARKERS.clear(); SHOWERS.clear(); aimCenter = null; aimUntil = 0;});
		ClientTickEvents.END_CLIENT_TICK.register(MeteorStreamClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(MeteorStreamClient::render);
	}

	private static void onFx(ModNetwork.MeteorStreamFx fx) {
		switch (fx.kind()) {
			case 0 -> {
				if (fx.valid()) aimCenter = fx.center();
				aimValid = fx.valid();
				aimRadius = fx.radius();
				aimUntil = clientTicks + 12;
			}
			case 3 -> {
				aimUntil = 0;
				aimCenter = null;
			}
			case 1 -> {
				Marker known = find(fx.center());
				if (known == null) {
					known = new Marker();
					known.center = fx.center();
					known.startTick = clientTicks;
					MARKERS.add(known);
				}
				known.radius = fx.radius();
				known.solar = fx.solar();
				known.endTick = clientTicks + fx.ticks();
			}
			case 2 -> {
				Marker known = find(fx.center());
				if (known != null) known.endTick = Math.min(known.endTick, clientTicks + 1);
			}
			default -> {}
		}
	}

	private static Marker find(Vec3 center) {
		for (Marker m : MARKERS) if (m.center.distanceToSqr(center) < 1.0) return m;
		return null;
	}

	/** 0..1: how strongly a Power meteor shower reddens the sky for a viewer at {@code pos} (ramps in, holds, fades out). */
	public static float showerTint(Vec3 pos) {
		float best = 0;
		for (Shower s : SHOWERS) {
			double d = Math.sqrt(Math.pow(pos.x - s.center.x, 2) + Math.pow(pos.z - s.center.z, 2));
			float radial = (float) Mth.clamp(1 - (d - 260) / 240.0, 0, 1);
			float in = Mth.clamp((clientTicks - s.start) / 70f, 0, 1);
			float out = Mth.clamp((s.end - clientTicks) / 160f, 0, 1);
			best = Math.max(best, radial * Math.min(in, out));
		}
		return best;
	}

	/** 0..1 red-sky strength this stream adds at a position: grows when it starts, fades as it ends, only near the circle. */
	public static float redFactor(Vec3 pos) {
		float best = 0;
		for (Marker m : MARKERS) {
			double d = Math.sqrt(Math.pow(pos.x - m.center.x, 2) + Math.pow(pos.z - m.center.z, 2));
			float radial = (float) Mth.clamp(1 - (d - 180) / 120.0, 0, 1);
			float in = Mth.clamp((clientTicks - m.startTick) / 80f, 0, 1);
			float out = Mth.clamp((m.endTick - clientTicks) / 100f, 0, 1);
			best = Math.max(best, 0.55f * radial * Math.min(in, out));
		}
		// A Power shower reddens the sky, softly: less than the Apocalypse, more than a hint.
		return Math.max(best, 0.28f * showerTint(pos));
	}

	private static void tick(Minecraft mc) {
		clientTicks++;
		if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
			MARKERS.clear();
			SHOWERS.clear();
			return;
		}
		SHOWERS.removeIf(s -> clientTicks >= s.end);
		for (Iterator<Marker> it = MARKERS.iterator(); it.hasNext(); ) {
			Marker m = it.next();
			if (clientTicks >= m.endTick) {
				it.remove();
				continue;
			}
			if (clientTicks % 3 == 0) rim(mc, m.center, m.radius, false);
		}
		if (clientTicks < aimUntil) {
			Vec3 localTarget = MeteorStream.resolveTarget(mc.player, GauntletConfig.get(), aimCenter);
			if (localTarget != null) {
				aimCenter = localTarget;
				aimValid = true;
			}
			if (aimCenter != null && aimValid && clientTicks % 2 == 0) {
				rim(mc, aimCenter, aimRadius, true);
			}
		}
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)) return;
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		int frame = (int) ((clientTicks / 2) & 3);
		Identifier tex = RETICLE_TEXTURES[frame];
		for (Marker m : MARKERS) {
			if (m.center.distanceToSqr(cam) < 320 * 320) {
				drawTerrainReticle(ctx, pose, mc.level, cam, m.center, m.radius, tex, ARGB.colorFromFloat(0.88f, 1.0f, 0.85f, 0.75f), 0.18);
			}
		}
		if (aimCenter != null && clientTicks < aimUntil && aimValid) {
			drawTerrainReticle(ctx, pose, mc.level, cam, aimCenter, aimRadius, tex, ARGB.colorFromFloat(0.95f, 1.0f, 0.95f, 0.88f), 0.22);
		}
	}

	/** Draws the reticle texture draped over the terrain and water surface so it adapts to hills, slopes and oceans. */
	private static void drawTerrainReticle(LevelRenderContext ctx, PoseStack pose, ClientLevel level, Vec3 cam, Vec3 center, float radius, Identifier tex, int color, double yOffset) {
		double[][] vy = new double[GRID + 1][GRID + 1];
		for (int ix = 0; ix <= GRID; ix++) {
			double wx = center.x + ((ix / (double) GRID) - 0.5) * 2.0 * radius;
			for (int iz = 0; iz <= GRID; iz++) {
				double wz = center.z + ((iz / (double) GRID) - 0.5) * 2.0 * radius;
				vy[ix][iz] = smoothedSurfaceY(level, wx, wz, center.y) + yOffset;
			}
		}
		pose.pushPose();
		pose.translate(center.x - cam.x, -cam.y, center.z - cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(tex), (pp, buf) -> {
			for (int ix = 0; ix < GRID; ix++) {
				float u0 = ix / (float) GRID;
				float u1 = (ix + 1) / (float) GRID;
				float x0 = (u0 - 0.5f) * 2.0f * radius;
				float x1 = (u1 - 0.5f) * 2.0f * radius;
				for (int iz = 0; iz < GRID; iz++) {
					float v0 = iz / (float) GRID;
					float v1 = (iz + 1) / (float) GRID;
					float z0 = (v0 - 0.5f) * 2.0f * radius;
					float z1 = (v1 - 0.5f) * 2.0f * radius;
					float y00 = (float) vy[ix][iz];
					float y10 = (float) vy[ix + 1][iz];
					float y11 = (float) vy[ix + 1][iz + 1];
					float y01 = (float) vy[ix][iz + 1];
					// Top face
					vertex(buf, pp, x0, y00, z0, u0, v0, color);
					vertex(buf, pp, x0, y01, z1, u0, v1, color);
					vertex(buf, pp, x1, y11, z1, u1, v1, color);
					vertex(buf, pp, x1, y10, z0, u1, v0, color);
					// Bottom face (visible from below on steep slopes)
					vertex(buf, pp, x0, y00, z0, u0, v0, color);
					vertex(buf, pp, x1, y10, z0, u1, v0, color);
					vertex(buf, pp, x1, y11, z1, u1, v1, color);
					vertex(buf, pp, x0, y01, z1, u0, v1, color);
				}
			}
		});
		pose.popPose();
	}

	private static void vertex(VertexConsumer buf, PoseStack.Pose pp, float x, float y, float z, float u, float v, int color) {
		buf.addVertex(pp, x, y, z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pp, 0, 1, 0);
	}

	private static double smoothedSurfaceY(ClientLevel level, double x, double z, double refY) {
		double y0 = MeteorStream.surfaceY(level, x, z, refY);
		double y1 = MeteorStream.surfaceY(level, x + 0.6, z, y0);
		double y2 = MeteorStream.surfaceY(level, x - 0.6, z, y0);
		double y3 = MeteorStream.surfaceY(level, x, z + 0.6, y0);
		double y4 = MeteorStream.surfaceY(level, x, z - 0.6, y0);
		return (y0 * 2.0 + y1 + y2 + y3 + y4) / 6.0;
	}

	/** Flames along the circle that follow the terrain and water surface, so the area stays readable on hills and oceans. */
	private static void rim(Minecraft mc, Vec3 center, float radius, boolean aim) {
		RandomSource rnd = mc.level.getRandom();
		int count = aim ? 12 : 8;
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
			double y = MeteorStream.surfaceY(mc.level, x, z, center.y);
			mc.level.addParticle(aim && rnd.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, true, true, x, y + 0.2, z, 0, 0.05 + rnd.nextDouble() * 0.05, 0);
		}
	}

	private MeteorStreamClient() {}
}
