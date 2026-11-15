package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.boss.GeoAnimation;
import com.dantemorrigan.gauntlet.client.boss.GeoModel;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/** Doctor Doom and the Doombots, drawn from their Bedrock models with the clips their server-side action names. */
public final class DoomRenderers {
	private static final String[] BOT_ATTACK = {"shoot", "shield", "dive", "cast", "repair", "combo"};

	private static final class DoomAssets {
		static final GeoModel MODEL = GeoModel.load(InfinityGauntlet.id("doctor_doom/doctor_doom.geo.json"));
		static final Map<String, GeoAnimation> CLIPS = GeoAnimation.load(InfinityGauntlet.id("doctor_doom/doctor_doom.animation.json"));
		static final Identifier TEXTURE = InfinityGauntlet.id("textures/entity/doctor_doom/doctor_doom.png");
		static final Identifier GOD = InfinityGauntlet.id("textures/entity/doctor_doom/god_emperor_doom.png");
	}

	private static final class BotAssets {
		static final GeoModel[] MODELS = new GeoModel[6];
		static final Identifier[] TEXTURES = new Identifier[6];
		static final Map<String, GeoAnimation> CLIPS = GeoAnimation.load(InfinityGauntlet.id("doctor_doom/doombot.animation.json"));
		static {
			for (int i = 0; i < 6; i++) {
				MODELS[i] = GeoModel.load(InfinityGauntlet.id("doctor_doom/doombot_" + Doombot.NAMES[i] + ".geo.json"));
				TEXTURES[i] = InfinityGauntlet.id("textures/entity/doctor_doom/doombot_" + Doombot.NAMES[i] + ".png");
			}
		}
	}

	private static void play(Map<String, GeoAnimation> clips, Map<String, GeoModel.BonePose> bones, String name, float seconds, float weight) {
		GeoAnimation clip = clips.get(name);
		if (clip != null) clip.apply(bones, seconds, weight);
	}

	/** Never darker than torchlight: the armor and the glowing eyes stay readable in the castle's halls. */
	private static int lit(int coords) {
		return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(coords), 10), LightCoordsUtil.sky(coords));
	}

	private static PoseStack stack(PoseStack.Pose p) {
		PoseStack s = new PoseStack();
		s.last().pose().set(p.pose());
		s.last().normal().set(p.normal());
		return s;
	}

	public static final class DoomState extends EntityRenderState {
		boolean god;
		float bodyYaw, actionTime, walkPos, walkSpeed, deathTime, headPitch, headYaw;
		int action, phase;
		boolean hurt, shield, exposed;
	}

	public static final class Doom extends EntityRenderer<DoctorDoom, DoomState> {
		public Doom(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.8f;
		}

		@Override
		public DoomState createRenderState() { return new DoomState(); }

		@Override
		protected AABB getBoundingBoxForCulling(DoctorDoom entity) {
			return super.getBoundingBoxForCulling(entity).inflate(2.5, 2.0, 2.5);
		}

		@Override
		public void extractRenderState(DoctorDoom e, DoomState s, float partial) {
			super.extractRenderState(e, s, partial);
			s.bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
			s.action = e.action();
			s.phase = e.phase();
			s.actionTime = (e.tickCount - e.actionStartTick + partial) / 20f;
			s.walkPos = e.walkAnimation.position(partial);
			s.walkSpeed = Math.min(1f, e.walkAnimation.speed(partial));
			s.hurt = e.hurtTime > 0 && !e.isDeadOrDying();
			s.deathTime = e.isDeadOrDying() ? e.deathTime + partial : 0;
			s.headPitch = e.getXRot(partial);
			s.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - s.bodyYaw);
			s.shield = e.flag(DoctorDoom.FLAG_SHIELD);
			s.exposed = e.flag(DoctorDoom.FLAG_EXPOSED);
			s.god = e.flag(DoctorDoom.FLAG_GOD);
		}

		@Override
		public void submit(DoomState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
			Map<String, GeoModel.BonePose> bones = new HashMap<>();
			if (s.deathTime > 0) {
				play(DoomAssets.CLIPS, bones, "defeat", Math.min(s.deathTime / 20f, 3.6f), 1);
			} else if (s.action == DoctorDoom.A_IDLE) {
				play(DoomAssets.CLIPS, bones, s.phase >= 3 ? "idle_menace" : "idle", s.ageInTicks / 20f, 1 - s.walkSpeed);
				play(DoomAssets.CLIPS, bones, "walk", s.walkPos * 0.11f, s.walkSpeed);
				GeoModel.BonePose head = bones.computeIfAbsent("head", k -> new GeoModel.BonePose());
				head.ry += Mth.clamp(s.headYaw, -60, 60);
				head.rx += Mth.clamp(s.headPitch, -30, 30);
			} else {
				play(DoomAssets.CLIPS, bones, DoctorDoom.CLIPS[Math.min(s.action, DoctorDoom.CLIPS.length - 1)], s.actionTime, 1);
			}
			float fade = s.deathTime > 88 ? Mth.clamp(1 - (s.deathTime - 88) / 12f, 0, 1) : 1;
			int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(s.hurt));
			int light = s.god ? 0xF000F0 : lit(s.lightCoords);
			int color = s.shield ? ARGB.color((int) (fade * 255), 200, 235, 255) : s.exposed ? ARGB.color((int) (fade * 255), 255, 226, 200)
					: ARGB.color((int) (fade * 255), 255, 255, 255);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyYaw));
			Identifier tex = s.god ? DoomAssets.GOD : DoomAssets.TEXTURE;
			var type = fade < 1 ? RenderTypes.entityTranslucent(tex) : RenderTypes.entityCutout(tex);
			collector.submitCustomGeometry(pose, type, (p, out) -> DoomAssets.MODEL.render(stack(p), out, bones, light, overlay, (b, c) -> color));
			pose.popPose();
			super.submit(s, pose, collector, camera);
		}
	}

	public static final class BotState extends EntityRenderState {
		float bodyYaw, actionTime, walkPos, walkSpeed, deathTime, headYaw, scale;
		int action, variant;
		boolean hurt, ward, allied, flying, decoy;
	}

	public static final class Bot extends EntityRenderer<Doombot, BotState> {
		/** A decoy is Doom to the eye: his model, texture and clips, at his own size. Only a faint chill in the steel betrays it. */
		private void decoy(BotState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
			Map<String, GeoModel.BonePose> bones = new HashMap<>();
			if (s.deathTime > 0) play(DoomAssets.CLIPS, bones, "stagger_heavy", Math.min(s.deathTime / 20f, 1.6f), 1);
			else switch (s.action) {
				case Doombot.A_ATTACK -> play(DoomAssets.CLIPS, bones, "gauntlet_bolt", s.actionTime, 1);
				case Doombot.A_STAGGER -> play(DoomAssets.CLIPS, bones, "stagger_heavy", s.actionTime, 1);
				default -> {
					play(DoomAssets.CLIPS, bones, "idle", s.ageInTicks / 20f, 1 - s.walkSpeed);
					play(DoomAssets.CLIPS, bones, "walk", s.walkPos * 0.11f, s.walkSpeed);
					bones.computeIfAbsent("head", k -> new GeoModel.BonePose()).ry += Mth.clamp(s.headYaw, -60, 60);
				}
			}
			int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(s.hurt));
			int light = lit(s.lightCoords);
			int color = ARGB.color(255, 238, 246, 255);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyYaw));
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(DoomAssets.TEXTURE), (p, out) ->
					DoomAssets.MODEL.render(stack(p), out, bones, light, overlay, (b, c) -> color));
			pose.popPose();
			super.submit(s, pose, collector, camera);
		}

		public Bot(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.9f;
		}

		@Override
		public BotState createRenderState() { return new BotState(); }

		/** A hunter that senses something shows a yellow "?", one that has the intruder a red "!", as in a stealth game. */
		@Override
		protected boolean shouldShowName(Doombot e, double distanceSq) {
			return e.awareness() > 0 && distanceSq < 64 * 64 || super.shouldShowName(e, distanceSq);
		}

		@Override
		protected net.minecraft.network.chat.Component getNameTag(Doombot e) {
			return switch (e.awareness()) {
				case 2 -> net.minecraft.network.chat.Component.literal("!").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
				case 1 -> net.minecraft.network.chat.Component.literal("?").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD);
				default -> super.getNameTag(e);
			};
		}

		@Override
		protected AABB getBoundingBoxForCulling(Doombot entity) {
			return super.getBoundingBoxForCulling(entity).inflate(1.5, 1.0, 1.5);
		}

		@Override
		public void extractRenderState(Doombot e, BotState s, float partial) {
			super.extractRenderState(e, s, partial);
			s.bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
			s.action = e.action();
			s.variant = e.variant();
			s.actionTime = (e.tickCount - e.actionStartTick + partial) / 20f;
			s.walkPos = e.walkAnimation.position(partial);
			s.walkSpeed = Math.min(1f, e.walkAnimation.speed(partial));
			s.hurt = e.hurtTime > 0 && !e.isDeadOrDying();
			s.deathTime = e.isDeadOrDying() ? e.deathTime + partial : 0;
			s.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - s.bodyYaw);
			s.ward = e.flag(Doombot.FLAG_WARD);
			s.allied = e.flag(Doombot.FLAG_ALLIED);
			s.flying = e.flag(Doombot.FLAG_FLYING);
			s.decoy = e.decoy();
			s.scale = e.getScale();
		}

		@Override
		public void submit(BotState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
			if (s.decoy) { decoy(s, pose, collector, camera); return; }
			Map<String, GeoModel.BonePose> bones = new HashMap<>();
			if (s.deathTime > 0) {
				play(BotAssets.CLIPS, bones, "shutdown", Math.min(s.deathTime / 20f, 2f), 1);
			} else switch (s.action) {
				case Doombot.A_ALERT -> play(BotAssets.CLIPS, bones, "alert", s.actionTime, 1);
				case Doombot.A_ATTACK -> play(BotAssets.CLIPS, bones, BOT_ATTACK[s.variant], s.actionTime, 1);
				case Doombot.A_STAGGER -> play(BotAssets.CLIPS, bones, "stagger", s.actionTime, 1);
				case Doombot.A_SALUTE -> play(BotAssets.CLIPS, bones, "salute", s.actionTime, 1);
				case Doombot.A_ACTIVATE -> play(BotAssets.CLIPS, bones, "activate", s.actionTime, 1);
				default -> {
					if (s.flying) play(BotAssets.CLIPS, bones, "hover", s.ageInTicks / 20f, 1);
					else {
						play(BotAssets.CLIPS, bones, "idle", s.ageInTicks / 20f, 1 - s.walkSpeed);
						play(BotAssets.CLIPS, bones, "walk", s.walkPos * 0.13f, s.walkSpeed);
					}
					bones.computeIfAbsent("head", k -> new GeoModel.BonePose()).ry += Mth.clamp(s.headYaw, -50, 50);
				}
			}
			int v = Mth.clamp(s.variant, 0, 5);
			int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(s.hurt));
			int light = lit(s.lightCoords);
			int color = s.ward ? ARGB.color(255, 170, 255, 190) : s.allied ? ARGB.color(255, 235, 255, 240) : -1;
			float scale = s.scale;
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyYaw));
			pose.scale(scale, scale, scale);
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BotAssets.TEXTURES[v]), (p, out) ->
					BotAssets.MODELS[v].render(stack(p), out, bones, light, overlay, (b, c) -> color));
			pose.popPose();
			super.submit(s, pose, collector, camera);
		}
	}

	private DoomRenderers() {}
}
