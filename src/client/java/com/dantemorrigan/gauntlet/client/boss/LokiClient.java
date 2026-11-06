package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.boss.Loki;
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
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
	private static ThanosClient.Music music;

	private record Cue(LokiFx fx, long start) {
		float age(float partial) { return ticks - start + partial; }
	}

	public static void init() {
		for (LokiFx.Effect effect : LokiFx.Effect.values()) {
			String texture = switch (effect) {
				case BARRAGE_MARK -> "bind_mark";
				case BARRAGE_IMPACT, TRANSFORM_ROAR -> "roar";
				case TRANSFORM_REVEAL -> "rage_pulse";
				case DEFEAT_CINEMATIC -> "transform";
				case DEFEAT_DUST -> "essence_collect";
				default -> effect.name().toLowerCase(Locale.ROOT);
			};
			TEXTURES.put(effect, InfinityGauntlet.id("textures/loki/" + texture + ".png"));
		}
		ClientPlayNetworking.registerGlobalReceiver(LokiFx.TYPE, (fx, context) -> {
			setLevel(context.client().level);
			if (level != null) CUES.add(new Cue(fx, ticks));
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			setLevel(mc.level);
			ticks++;
			CUES.removeIf(c -> c.age(0) >= c.fx.effect().ticks);
			tickMusic(mc);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(LokiClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("loki_gold_light"), (graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.level != level) return;
			float a = goldTint(mc.gameRenderer.getMainCamera().position(), delta.getGameTimeDeltaPartialTick(false));
			if (a > 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(a * .25f, 1, .84f, .39f));
			float partial = delta.getGameTimeDeltaPartialTick(false);
			float defeat = defeatAge(mc, partial);
			float stage = defeat >= 0 ? defeat : transformationAge(mc, partial);
			if (stage >= 0) {
				int duration = defeat >= 0 ? Loki.DEATH_TICKS : Loki.TRANSFORMATION_TICKS;
				int reveal = defeat >= 0 ? Loki.REVERT_TICK : Loki.REVEAL_TICK;
				float envelope = Mth.clamp(Math.min(stage / 13, (duration - stage) / 13), 0, 1);
				int w = graphics.guiWidth(), h = graphics.guiHeight();
				int edge = Math.round(h * .09f * envelope);
				if (edge > 0) {
					graphics.fill(0, 0, w, edge, 0xf0080f0d);
					graphics.fill(0, h - edge, w, h, 0xf0080f0d);
				}
				float flash = Math.max(0, 1 - Math.abs(stage - reveal) / 8) * .27f;
				int tint = ARGB.colorFromFloat((envelope * .08f + flash) * Mth.clamp(GauntletConfig.get().flashIntensity, 0, 1), .20f, .76f, .48f);
				graphics.fill(0, edge, w, h - edge, tint);
			}
		});
	}

	private static void setLevel(ClientLevel next) {
		if (next == level) return;
		level = next;
		CUES.clear();
		if (music != null) music.halt();
		music = null;
	}

	private static void tickMusic(Minecraft mc) {
		boolean fight = false;
		if (mc.player != null && mc.player.isAlive() && !mc.player.isSpectator() && level != null
				&& !ThanosClient.musicPlaying()) {
			for (var entity : level.entitiesForRendering()) {
				if (entity instanceof Loki loki && loki.isAlive() && !loki.isIllusion()
						&& mc.player.distanceToSqr(loki) < 50 * 50) {
					fight = true;
					break;
				}
			}
		}
		if (music != null && music.isStopped()) music = null;
		if (fight && music == null) {
			music = new ThanosClient.Music(ModSounds.LOKI_MUSIC);
			mc.getSoundManager().play(music);
		}
		if (music != null) {
			if (fight) mc.getMusicManager().stopPlaying();
			else music.fadeOut(40);
		}
	}

	private static float transformationAge(Minecraft mc, float partial) {
		for (Cue cue : CUES) {
			if (cue.fx.effect() == LokiFx.Effect.TRANSFORM) return cue.age(partial);
		}
		// Clients that enter tracking range midway get the same phase from the entity snapshot.
		for (var entity : level.entitiesForRendering()) {
			if (entity instanceof Loki loki && loki.transformationTicks() > 0
					&& mc.gameRenderer.getMainCamera().position().distanceToSqr(loki.position()) <= 80 * 80) {
				return Loki.TRANSFORMATION_TICKS - loki.transformationTicks() + partial;
			}
		}
		return -1;
	}

	private static float defeatAge(Minecraft mc, float partial) {
		for (Cue cue : CUES) {
			if (cue.fx.effect() == LokiFx.Effect.DEFEAT_CINEMATIC) return cue.age(partial);
		}
		for (var entity : level.entitiesForRendering()) {
			if (entity instanceof Loki loki && !loki.isIllusion() && loki.isDeadOrDying()
					&& mc.gameRenderer.getMainCamera().position().distanceToSqr(loki.position()) <= 80 * 80) return loki.deathTime + partial;
		}
		return -1;
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
			if (effect == LokiFx.Effect.DEFEAT_CINEMATIC) fade *= .65f;
			int color = ARGB.color((int) (255 * Mth.clamp(fade, 0, 1)), 255, 255, 255);
			Vec3 origin = fx.position().subtract(camera);
			if (effect == LokiFx.Effect.WARD) {
				if (!(level.getEntity(fx.recipient()) instanceof Loki loki) || !loki.isAlive() || loki.isEnraged()) continue;
				origin = loki.getPosition(partial).subtract(camera);
			}
			if (effect == LokiFx.Effect.MIND_STONE_REVEAL) {
				renderStone(ctx, mc, pose, fx, camera, age);
			}
			pose.pushPose();
			pose.translate(origin.x, origin.y + effect.yOffset, origin.z);
			if (effect == LokiFx.Effect.ROAR_WARN || effect == LokiFx.Effect.ROAR || effect == LokiFx.Effect.PAW_WARN
					|| effect == LokiFx.Effect.BARRAGE_MARK || effect == LokiFx.Effect.BARRAGE_IMPACT || effect == LokiFx.Effect.TRANSFORM_ROAR) {
				pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw() + 180));
				pose.mulPose(Axis.XP.rotationDegrees(90));
			} else if (effect == LokiFx.Effect.PORTAL_OPEN || effect == LokiFx.Effect.PORTAL_LOOP || effect == LokiFx.Effect.DAGGER_SLASH) {
				pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw()));
			} else {
				pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
			}
			if (effect == LokiFx.Effect.TRANSFORM) {
				pose.mulPose(Axis.ZP.rotationDegrees(age * 3.6f));
				float contraction = 1.2f - .55f * Mth.clamp(age / Loki.REVEAL_TICK, 0, 1);
				pose.scale(contraction, contraction, 1);
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

	private static void renderStone(LevelRenderContext ctx, Minecraft mc, PoseStack pose, LokiFx fx, Vec3 camera, float age) {
		if (age < 7) return;
		var recipient = level.getEntity(fx.recipient());
		float travel = Mth.clamp((age - 35) / 17f, 0, 1);
		Vec3 origin = fx.position().add(0, Mth.sin(age * .18f) * .09f, 0);
		if (recipient != null) origin = origin.lerp(recipient.position().add(0, 1.15, 0), travel * travel);
		origin = origin.subtract(camera);
		float size = .95f * Mth.clamp((age - 7) / 17f, 0, 1) * (1 - .8f * travel);
		ItemStackRenderState state = new ItemStackRenderState();
		mc.getItemModelResolver().updateForTopItem(state, new ItemStack(ModItems.MIND_STONE), ItemDisplayContext.FIXED, level, null, 0);
		pose.pushPose();
		pose.translate(origin.x, origin.y, origin.z);
		pose.mulPose(Axis.YP.rotationDegrees(age * 7));
		pose.mulPose(Axis.ZP.rotationDegrees(12));
		pose.scale(size, size, size);
		state.submit(pose, ctx.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		out.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}

	private LokiClient() {}
}
