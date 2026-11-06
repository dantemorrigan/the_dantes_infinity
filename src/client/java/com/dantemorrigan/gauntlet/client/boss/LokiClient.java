package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.boss.LokiFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LokiClient {
	private static final int FRAMES = 12;
	private static final float ATLAS_WIDTH = FRAMES * 128;
	private static final List<Cue> CUES = new ArrayList<>();
	private static final Map<LokiFx.Effect, Identifier> TEXTURES = new EnumMap<>(LokiFx.Effect.class);
	private static ClientLevel level;
	private static long ticks;

	private record Cue(LokiFx fx, long start) {
		float age(float partial) { return ticks - start + partial; }
	}

	public static void init() {
		for (LokiFx.Effect effect : LokiFx.Effect.values()) {
			TEXTURES.put(effect, InfinityGauntlet.id("textures/loki/" + effect.name().toLowerCase(Locale.ROOT) + ".png"));
		}
		ClientPlayNetworking.registerGlobalReceiver(LokiFx.TYPE, (fx, context) -> {
			setLevel(context.client().level);
			if (level != null) CUES.add(new Cue(fx, ticks));
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			setLevel(mc.level);
			ticks++;
			CUES.removeIf(c -> c.age(0) >= c.fx.effect().ticks);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(LokiClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("loki_gold_light"), (graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.level != level) return;
			float a = goldTint(mc.gameRenderer.getMainCamera().position(), delta.getGameTimeDeltaPartialTick(false));
			if (a > 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(a * .25f, 1, .84f, .39f));
		});
	}

	private static void setLevel(ClientLevel next) {
		if (next == level) return;
		level = next;
		CUES.clear();
	}

	public static float goldTint(Vec3 camera, float partial) {
		if (level == null || level != Minecraft.getInstance().level) return 0;
		float strength = 0;
		for (Cue cue : CUES) {
			if (cue.fx.effect() != LokiFx.Effect.GOLD_WORLD_WASH) continue;
			float t = Mth.clamp(cue.age(partial) / cue.fx.effect().ticks, 0, 1);
			float local = Mth.clamp(1 - ((float) camera.distanceTo(cue.fx.position()) - 12) / 54, 0, 1);
			strength = Math.max(strength, Mth.sin(t * Mth.PI) * local);
		}
		return strength * Mth.clamp(GauntletConfig.get().flashIntensity, 0, 1);
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level != level) return;
		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		PoseStack pose = ctx.poseStack();
		for (Cue cue : CUES) {
			LokiFx fx = cue.fx;
			LokiFx.Effect effect = fx.effect();
			float age = cue.age(partial);
			if (age < 0 || age >= effect.ticks || fx.position().distanceToSqr(camera) > 96 * 96) continue;
			int frame = effect.loop ? (int) (age / 1.6f) % FRAMES : Math.min(FRAMES - 1, (int) (age * FRAMES / effect.ticks));
			float u0 = (frame * 128 + .5f) / ATLAS_WIDTH;
			float u1 = ((frame + 1) * 128 - .5f) / ATLAS_WIDTH;
			float fade = effect.loop ? Mth.clamp((effect.ticks - age) / 11, 0, 1) : 1;
			if (effect == LokiFx.Effect.GOLD_WORLD_WASH) fade *= GauntletConfig.get().flashIntensity;
			int color = ARGB.color((int) (255 * Mth.clamp(fade, 0, 1)), 255, 255, 255);
			Vec3 origin = fx.position().subtract(camera);
			pose.pushPose();
			pose.translate(origin.x, origin.y + effect.yOffset, origin.z);
			if (effect == LokiFx.Effect.PORTAL_OPEN || effect == LokiFx.Effect.PORTAL_LOOP || effect == LokiFx.Effect.DAGGER_SLASH) {
				pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw()));
			} else {
				pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
			}
			float r = effect.size * 0.5f;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(TEXTURES.get(effect)), (p, out) -> {
				vertex(out, p, -r, -r, u0, 127.5f / 128, color);
				vertex(out, p, r, -r, u1, 127.5f / 128, color);
				vertex(out, p, r, r, u1, .5f / 128, color);
				vertex(out, p, -r, r, u0, .5f / 128, color);
			});
			pose.popPose();
		}
	}

	private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		out.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}

	private LokiClient() {}
}
