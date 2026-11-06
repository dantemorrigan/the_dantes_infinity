package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.boss.DoomTeaserRenderer;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Space stone client: keys (B portal, Shift+B destination, N teleport where you look), portal rendering
 * (opening frames -> looping idle rim over the painted destination window; closing = opening reversed),
 * blue flash and wisps from the pack.
 */
public final class SpaceStoneClient {
	public static final KeyMapping PORTAL = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.space_portal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));
	public static final KeyMapping BLINK = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.space_blink", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));

	private static final int OPEN = 8, CLOSE = 8;

	private static final class ClientPortal {
		final Vec3 center;
		final float yaw;
		final String dest;
		int age, life;
		ClientLevel level;

		ClientPortal(Vec3 center, float yaw, String dest, int age, int left, ClientLevel level) {
			this.center = center;
			this.yaw = yaw;
			this.dest = dest;
			this.age = age;
			this.life = age + left;
			this.level = level;
		}
	}

	private static final Map<Integer, ClientPortal> PORTALS = new HashMap<>();

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SpaceStoneClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PortalOpen.TYPE, (p, ctx) ->
				PORTALS.put(p.id(), new ClientPortal(p.pos(), p.yaw(), p.dest(), p.age(), p.ticksLeft(), Minecraft.getInstance().level)));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PortalClose.TYPE, (p, ctx) -> PORTALS.remove(p.id()));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SpaceFx.TYPE, (p, ctx) -> teleportFx(p.from(), p.to()));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(PORTALS::clear));
		LevelRenderEvents.COLLECT_SUBMITS.register(SpaceStoneClient::render);
	}

	private static void tick(Minecraft mc) {
		while (PORTAL.consumeClick()) {
			if (mc.player != null) ClientPlayNetworking.send(new ModNetwork.SpaceAction(mc.player.isShiftKeyDown() ? 1 : 0));
		}
		while (BLINK.consumeClick()) {
			if (mc.player != null) ClientPlayNetworking.send(new ModNetwork.SpaceAction(2));
		}
		if (mc.level == null) return;
		PORTALS.values().removeIf(p -> p.level != mc.level || ++p.age > p.life + 2);
		RandomSource rnd = mc.level.getRandom();
		for (ClientPortal p : PORTALS.values()) {
			// Loose blue wisps around the rim (fresh particles, never one looping forever).
			if (p.age % 3 == 0) {
				double a = rnd.nextDouble() * Math.PI * 2;
				Vec3 right = right(p.yaw);
				float r = SpaceStone.PORTAL_SIZE * 0.36f;
				Vec3 at = p.center.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
				ClientPower.spawn(ModParticles.SPACE_ENERGY_WISPS, at, 0.8 + rnd.nextDouble() * 0.6, 0, 0);
			}
		}
	}

	private static Vec3 right(float yaw) {
		Vec3 n = Vec3.directionFromRotation(0, yaw);
		return new Vec3(-n.z, 0, n.x);
	}

	public static void teleportFx(Vec3 from, Vec3 to) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		ClientPower.spawn(ModParticles.SPACE_TELEPORT_FLASH, from.add(0, 1, 0), 1.8, 0, 0);
		// At the arrival the camera is inside the flash: keep it small and in front of the feet, not in the face.
		Minecraft m = Minecraft.getInstance();
		boolean self = m.player != null && m.player.position().distanceTo(to) < 1.5;
		Vec3 at = self ? to.add(m.player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)).add(0, 0.6, 0) : to.add(0, 1, 0);
		ClientPower.spawn(ModParticles.SPACE_TELEPORT_FLASH, at, self ? 1.0 : 2.0, 0, 0);
		double dist = from.distanceTo(to);
		int n = (int) Math.min(40, dist / 1.5);
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.lerp(to, i / (double) Math.max(1, n)).add(0, 1, 0);
			ClientPower.spawn(ModParticles.SPACE_ENERGY_WISPS, p, 0.8, 0, 0);
		}
		RandomSource rnd = mc.level.getRandom();
		for (int i = 0; i < 6; i++) {
			ClientPower.spawn(ModParticles.SPACE_ENERGY_WISPS, to.add(rnd.nextGaussian() * 0.6, 0.5 + rnd.nextDouble() * 1.5, rnd.nextGaussian() * 0.6), 1.0, 0, 0);
		}
	}

	// ------------------------------------------------------------------ render

	private static Identifier tex(String path) {
		return InfinityGauntlet.id("textures/space/" + path + ".png");
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (PORTALS.isEmpty() || mc.level == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		for (ClientPortal p : PORTALS.values()) {
			if (p.level != mc.level) continue;
			float age = p.age + partial;
			float closing = p.life - age;
			int frame;
			Identifier rim;
			float windowAlpha;
			if (age < OPEN) {
				frame = Mth.clamp((int) (age / 2), 0, 3);
				rim = tex("portal_opening_" + frame);
				windowAlpha = Mth.clamp((age - 4) / 4f, 0, 1);
			} else if (closing < CLOSE) {
				frame = Mth.clamp((int) (closing / 2), 0, 3); // opening played backwards
				rim = tex("portal_opening_" + frame);
				windowAlpha = Mth.clamp((closing - 4) / 4f, 0, 1);
			} else {
				frame = ((int) ((age - OPEN) / 3)) % 4;
				rim = tex("portal_idle_" + frame);
				windowAlpha = 1;
			}
			float s = SpaceStone.PORTAL_SIZE / 2;
			pose.pushPose();
			pose.translate(p.center.x - cam.x, p.center.y - cam.y, p.center.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(-p.yaw));
			if (SpaceStone.LATVERIA.equals(p.dest)) {
				float reveal = Mth.clamp(Math.min(age, closing) / OPEN, 0, 1);
				pose.pushPose();
				pose.scale(s * reveal, s * reveal, s);
				latveriaWindow(ctx, pose, age, reveal);
				pose.popPose();
				DoomTeaserRenderer.render(ctx, pose, age, closing);
				pose.popPose();
				continue;
			}
			pose.scale(s, s, s);
			// Painted destination window (rim included), slightly behind; then the animated cobalt rim on top.
			if (!p.dest.equals("exit") && windowAlpha > 0) {
				int c = ARGB.colorFromFloat(windowAlpha, 1, 1, 1);
				quad(ctx, pose, RenderTypes.entityTranslucentEmissive(tex("portal_" + p.dest)), 0.86f, -0.01f, c);
			}
			quad(ctx, pose, RenderTypes.entityTranslucentEmissive(rim), 1.0f, 0.0f, 0xFFFFFFFF);
			pose.popPose();
		}
	}

	/** The reference image is clipped by the aperture mesh, with an animated emerald rim. */
	private static void latveriaWindow(LevelRenderContext ctx, PoseStack pose, float age, float reveal) {
		var picture = RenderTypes.entityTranslucentEmissive(tex("portal_latveria"));
		ctx.submitNodeCollector().submitCustomGeometry(pose, picture, (p, out) -> {
			int color = ARGB.colorFromFloat(reveal, 1, 1, 1);
			for (int i = 0; i < 32; i++) {
				float low = (i - 16) / 16f, high = (i + 1 - 16) / 16f;
				float a = (float)Math.sqrt(1 - low * low), b = (float)Math.sqrt(1 - high * high);
				for (int side = 1; side >= -1; side -= 2) {
					apertureVertex(out, p, -a * side, low, color, side);
					apertureVertex(out, p, a * side, low, color, side);
					apertureVertex(out, p, b * side, high, color, side);
					apertureVertex(out, p, -b * side, high, color, side);
				}
			}
		});
		// The existing portal particle frame has an opaque white center texel for tinting.
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(tex("portal_idle_0")), (p, out) -> {
			for (int i = 0; i < 40; i++) {
				float a = (float)(i * Math.TAU / 40), b = (float)((i + 1) * Math.TAU / 40);
				float pulse = 0.5f + 0.5f * Mth.sin(a * 5 - age * 0.23f);
				int edge = ARGB.colorFromFloat(reveal * (0.85f + 0.15f * pulse),
						0.16f + pulse * 0.29f, 0.53f + pulse * 0.28f, 0.25f + pulse * 0.09f);
				ringSegment(out, p, a, b, 0.92f, 1.07f + 0.045f * pulse, edge);
				ringSegment(out, p, a, b, 1.055f, 1.14f + 0.045f * pulse,
						ARGB.colorFromFloat(0.22f * reveal, 0.57f, 0.84f, 0.24f));
			}
		});
	}

	private static void apertureVertex(VertexConsumer out, PoseStack.Pose p, float x, float y, int color, int side) {
		// The wide reference is cropped horizontally, preserving its proportions in the oval.
		v(out, p, x * 0.74f, y * 0.94f, 0, 0.58f + x * side * 0.312f, 0.5f - y * 0.5f, color, side);
	}

	private static void ringSegment(VertexConsumer out, PoseStack.Pose p, float a, float b, float inner, float outer, int color) {
		for (int side = 1; side >= -1; side -= 2) {
			float start = side > 0 ? a : b, end = side > 0 ? b : a;
			ringVertex(out, p, start, inner, color, side);
			ringVertex(out, p, start, outer, color, side);
			ringVertex(out, p, end, outer, color, side);
			ringVertex(out, p, end, inner, color, side);
		}
	}

	private static void ringVertex(VertexConsumer out, PoseStack.Pose p, float angle, float radius, int color, int side) {
		v(out, p, Mth.cos(angle) * radius * 0.74f, Mth.sin(angle) * radius * 0.94f,
				side * 0.01f, 0.5f, 0.5f, color, side);
	}

	/** Upright square, both sides; the back side uses mirrored U so the picture is never seen mirrored. */
	private static void quad(LevelRenderContext ctx, PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type, float size, float z, int color) {
		ctx.submitNodeCollector().submitCustomGeometry(pose, type, (p, buffer) -> {
			float h = size;
			// front (faces +Z)
			v(buffer, p, -h, -h, z, 0, 1, color, 1);
			v(buffer, p, h, -h, z, 1, 1, color, 1);
			v(buffer, p, h, h, z, 1, 0, color, 1);
			v(buffer, p, -h, h, z, 0, 0, color, 1);
			// back (faces -Z), U mirrored
			v(buffer, p, -h, h, z, 1, 0, color, -1);
			v(buffer, p, h, h, z, 0, 0, color, -1);
			v(buffer, p, h, -h, z, 0, 1, color, -1);
			v(buffer, p, -h, -h, z, 1, 1, color, -1);
		});
	}

	private static void v(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int color, float nz) {
		b.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 0, nz);
	}

	private SpaceStoneClient() {}
}
