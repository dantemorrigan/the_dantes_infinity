package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.stones.SoulQuest;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Soul quest on the client (pack 05_Soul_Quest): the amber ritual sky, the ritual clouds, the beam from the chasm to
 * the clouds with cloud pulses, the fade to black; then in the soul world the red-sun horizon and the fade in.
 */
public final class SoulQuestClient {
	private static final Identifier CLOUDS = InfinityGauntlet.id("textures/environment/quest_soul_ritual_clouds.png");
	private static final Identifier[] BEAM = new Identifier[8];
	static { for (int i = 0; i < 8; i++) BEAM[i] = InfinityGauntlet.id("textures/particle/quest_soul_ritual_beam_0" + i + ".png"); }

	private static BlockPos beam;
	private static int top, ritualTicks, age = -1;
	private static float amber, dark, fadeIn;
	private static int ticks;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SoulRitualFx.TYPE, (p, ctx) -> {
			if (p.kind() == 0) { beam = p.pos(); top = p.top(); ritualTicks = p.ticks(); age = 0; }
			else { age = -1; beam = null; dark = 1; fadeIn = 1; }
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { beam = null; age = -1; amber = dark = fadeIn = 0; });
		ClientTickEvents.END_CLIENT_TICK.register(SoulQuestClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SoulQuestClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("soul_quest"), (g, d) -> hud(g));
	}

	/** 0..1: the amber ritual tint of sky and fog. */
	public static float amber() { return amber; }

	public static boolean inSoulWorld() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().equals(SoulQuest.SOUL_WORLD);
	}

	private static void tick(Minecraft mc) {
		ticks++;
		if (age >= 0) {
			age++;
			amber = Math.min(1, amber + 0.03f);
			dark = Mth.clamp((age - (ritualTicks - 45)) / 40f, 0, 1);
			if (mc.level != null && beam != null && age % 8 == 0) {
				mc.level.addParticle(ModParticles.QUEST_SOUL_CLOUD_PULSE, beam.getX() + 0.5, top + 75, beam.getZ() + 0.5, 9.0, 0, 0);
			}
			if (age > ritualTicks + 60) { age = -1; beam = null; }
		} else {
			amber = Math.max(0, amber - 0.02f);
			if (fadeIn > 0) { fadeIn = Math.max(0, fadeIn - 0.02f); dark = fadeIn; }
			else dark = 0;
		}
	}

	private static void hud(GuiGraphicsExtractor g) {
		if (dark <= 0.01f) return;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color((int) (Mth.clamp(dark, 0, 1) * 255), 0, 0, 0));
	}

	// ------------------------------------------------------------------ ritual: clouds and beam

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (beam == null || amber <= 0 || mc.level == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = ticks + partial;
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		double cx = beam.getX() + 0.5, cz = beam.getZ() + 0.5, cloudY = top + 75;
		int a = (int) (255 * amber);
		// Ritual clouds: three slowly turning layers over the summit.
		for (int layer = 0; layer < 3; layer++) {
			pose.pushPose();
			pose.translate(cx - cam.x, cloudY - layer * 6 - cam.y, cz - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(t * (0.12f + layer * 0.05f) + layer * 60));
			float w = 150 + layer * 40, h = w / 4;
			int c = ARGB.color((int) (a * (0.85f - layer * 0.2f)), 255, 220, 190);
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(CLOUDS), (p, b) -> {
				for (int side = 0; side < 2; side++) {
					float ny = side == 0 ? 1 : -1;
					float[][] q = side == 0 ? new float[][]{{-w, -h}, {w, -h}, {w, h}, {-w, h}} : new float[][]{{-w, h}, {w, h}, {w, -h}, {-w, -h}};
					float[][] uv = side == 0 ? new float[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}} : new float[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}};
					for (int i = 0; i < 4; i++) b.addVertex(p, q[i][0], 0, q[i][1]).setColor(c).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, ny, 0);
				}
			});
			pose.popPose();
		}
		// The beam: stacked upright quads (the strip repeats along the height), always turned to the camera.
		float grow = Mth.clamp(age / 30f, 0, 1);
		double bottom = beam.getY(), height = (cloudY - bottom) * grow;
		float yaw = (float) Math.toDegrees(Math.atan2(cam.x - cx, cam.z - cz));
		Identifier frame = BEAM[(ticks / 2) % 8];
		int c = ARGB.color(a, 255, 255, 255);
		float half = 3.2f, seg = 6.4f;
		for (double y = 0; y < height; y += seg) {
			float hh = (float) Math.min(seg, height - y);
			pose.pushPose();
			pose.translate(cx - cam.x, bottom + y - cam.y, cz - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(yaw));
			float v1 = hh / seg;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(frame), (p, b) -> {
				v(b, p, -half, 0, 0, v1, c);
				v(b, p, half, 0, 1, v1, c);
				v(b, p, half, hh, 1, 0, c);
				v(b, p, -half, hh, 0, 0, c);
			});
			pose.popPose();
		}
	}

	private static void v(VertexConsumer b, PoseStack.Pose p, float x, float y, float u, float v, int c) {
		b.addVertex(p, x, y, 0).setColor(c).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 0, 1);
	}

	// ------------------------------------------------------------------ soul world sky

	private static final Identifier BAND = InfinityGauntlet.id("textures/environment/soul_sky_band.png");
	private static final Identifier DOME = InfinityGauntlet.id("textures/environment/soul_sky_dome.png");
	private static final Identifier SUN = InfinityGauntlet.id("textures/environment/quest_soul_red_sun.png");
	private static final Identifier GLOW = InfinityGauntlet.id("textures/environment/soul_sun_glow.png");

	/**
	 * The soul world sky (sky pass, skybox "end"): a dark gradient dome, a seamless 360-degree band of red clouds that
	 * slowly turns (made from the pack's horizon art), and the red sun with its glow low in the south.
	 */
	public static void drawSky() {
		Minecraft mc = Minecraft.getInstance();
		float t = (mc.level == null ? 0 : mc.level.getGameTime()) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float r = 100f, yTop = 69f, yBottom = -36f, R = (float) Math.sqrt(r * r + yTop * yTop);
		double topElev = Math.atan2(yTop, r);
		PoseStack pose = new PoseStack();
		int seg = 48;
		// Dome: from the top of the band up to the zenith (v: 1 at the band, 0 at the zenith).
		CelestialSky.draw(pose, DOME, buf -> {
			int rings = 8;
			for (int k = 0; k < rings; k++) {
				double e0 = topElev + (Math.PI / 2 - topElev) * k / rings, e1 = topElev + (Math.PI / 2 - topElev) * (k + 1) / rings;
				float v0 = 1 - (float) k / rings, v1 = 1 - (float) (k + 1) / rings;
				for (int i = 0; i < seg; i++) {
					double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
					float[] p00 = sph(R, a0, e0), p10 = sph(R, a1, e0), p11 = sph(R, a1, e1), p01 = sph(R, a0, e1);
					quad(buf, p00, p10, p11, p01, 0, v0, 1, v1);
				}
			}
			// Below the band: dark, down to the nadir.
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
				float[] p00 = {(float) Math.sin(a0) * r, yBottom, (float) Math.cos(a0) * r}, p10 = {(float) Math.sin(a1) * r, yBottom, (float) Math.cos(a1) * r};
				float[] down = {0, -R, 0};
				quad(buf, p00, p10, down, down, 0, 0.6f, 1, 0.6f);
			}
		});
		// The cloud band, turning very slowly.
		double spin = t * 0.00004;
		CelestialSky.draw(pose, BAND, buf -> {
			for (int i = 0; i < seg * 2; i++) {
				double a0 = Math.PI * 2 * i / (seg * 2) + spin, a1 = Math.PI * 2 * (i + 1) / (seg * 2) + spin;
				float u0 = (float) i / (seg * 2), u1 = (float) (i + 1) / (seg * 2);
				float[] b0 = {(float) Math.sin(a0) * r, yBottom, (float) Math.cos(a0) * r}, b1 = {(float) Math.sin(a1) * r, yBottom, (float) Math.cos(a1) * r};
				float[] t1 = {b1[0], yTop, b1[2]}, t0 = {b0[0], yTop, b0[2]};
				quad(buf, b0, b1, t1, t0, u0, 1, u1, 0);
			}
		});
		// The red sun, low in the south, with a soft glow.
		float elev = (float) Math.toRadians(11), d = 95;
		float cy = (float) Math.sin(elev) * d, cz = (float) Math.cos(elev) * d;
		CelestialSky.draw(pose, GLOW, buf -> sunQuad(buf, cy, cz, 34));
		CelestialSky.draw(pose, SUN, buf -> sunQuad(buf, cy, cz, 9));
	}

	private static float[] sph(float R, double a, double e) {
		return new float[]{(float) (Math.sin(a) * Math.cos(e) * R), (float) (Math.sin(e) * R), (float) (Math.cos(a) * Math.cos(e) * R)};
	}

	private static void quad(com.mojang.blaze3d.vertex.BufferBuilder buf, float[] p0, float[] p1, float[] p2, float[] p3, float u0, float v0, float u1, float v1) {
		float[][] p = {p0, p1, p2, p3};
		float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		for (int i = 0; i < 4; i++) buf.addVertex(p[i][0], p[i][1], p[i][2]).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
		for (int i = 3; i >= 0; i--) buf.addVertex(p[i][0], p[i][1], p[i][2]).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
	}

	private static void sunQuad(com.mojang.blaze3d.vertex.BufferBuilder buf, float cy, float cz, float h) {
		quad(buf, new float[]{-h, cy - h, cz}, new float[]{h, cy - h, cz}, new float[]{h, cy + h, cz}, new float[]{-h, cy + h, cz}, 0, 1, 1, 0);
	}

	private SoulQuestClient() {}
}
