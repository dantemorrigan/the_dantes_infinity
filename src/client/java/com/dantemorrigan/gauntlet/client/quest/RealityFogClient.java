package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * The red mist (pack 02_Reality_Quest): red fog colour plus layers of the pack's translucent density map
 * ("quest_reality_fog" frames) as quads drifting around the shared world-space center.
 */
public final class RealityFogClient {
	/** Distance, quads per ring, quad half-size, opacity of each layer. */
	private static final float[][] LAYERS = {{3.5f, 5, 2.6f, 0.42f}, {7f, 7, 4.2f, 0.38f}, {12f, 9, 6.5f, 0.34f}, {19f, 11, 9.5f, 0.30f}};
	private static final Identifier[] FRAMES = new Identifier[8];
	static {
		for (int i = 0; i < 8; i++) FRAMES[i] = InfinityGauntlet.id("textures/particle/quest_reality_fog_0" + i + ".png");
	}

	private static boolean on;
	private static Vec3 center = Vec3.ZERO, target = Vec3.ZERO;
	private static float factor;
	private static int ticks;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.RealityFogFx.TYPE, (packet, ctx) -> {
			if (packet.on()) {
				target = packet.center();
				if (!on) center = target;
			}
			on = packet.on();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { on = false; factor = 0; });
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ticks++;
			if (on) center = center.lerp(target, .24);
			// Fades in slowly, out a bit faster (the alpha falloff after fog_dispel).
			factor += ((on ? 1f : 0f) - factor) * (on ? 0.04f : 0.07f);
			if (factor < 0.002f) factor = 0;
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(RealityFogClient::render);
	}

	/** 0..1, how thick the mist is right now. */
	public static float factor() { return factor; }

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (factor <= 0 || mc.level == null || mc.player == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = ticks + partial;
		Vec3 anchor = center.subtract(mc.gameRenderer.getMainCamera().position());
		PoseStack pose = ctx.poseStack();
		for (int layer = 0; layer < LAYERS.length; layer++) {
			float dist = LAYERS[layer][0], half = LAYERS[layer][2];
			int count = (int) LAYERS[layer][1];
			// Each layer turns slowly, alternating direction, and breathes up and down.
			float spin = t * (layer % 2 == 0 ? 0.25f : -0.18f) / (1 + layer);
			int alpha = (int) (255 * LAYERS[layer][3] * factor);
			int color = ARGB.color(alpha, 255, 255, 255);
			for (int i = 0; i < count; i++) {
				float yaw = spin + 360f * i / count + layer * 17;
				double rad = Math.toRadians(yaw);
				double y = Math.sin(t * 0.02 + i * 1.7 + layer) * 1.2 - 0.4;
				Identifier frame = FRAMES[(int) ((t / 6f) + i * 3 + layer * 2) % 8];
				pose.pushPose();
				pose.translate(anchor.x + Math.cos(rad) * dist, anchor.y + y, anchor.z + Math.sin(rad) * dist);
				pose.mulPose(Axis.YP.rotationDegrees(-yaw - 90));
				ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(frame), (p, b) -> {
					v(b, p, -half, -half, 0, 1, color, 1);
					v(b, p, half, -half, 1, 1, color, 1);
					v(b, p, half, half, 1, 0, color, 1);
					v(b, p, -half, half, 0, 0, color, 1);
					v(b, p, -half, half, 1, 0, color, -1);
					v(b, p, half, half, 0, 0, color, -1);
					v(b, p, half, -half, 0, 1, color, -1);
					v(b, p, -half, -half, 1, 1, color, -1);
				});
				pose.popPose();
			}
		}
	}

	private static void v(VertexConsumer b, PoseStack.Pose p, float x, float y, float u, float v, int color, float nz) {
		b.addVertex(p, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 0, nz);
	}

	private RealityFogClient() {}
}
