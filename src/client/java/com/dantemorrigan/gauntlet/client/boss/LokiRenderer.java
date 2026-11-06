package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.boss.Loki;
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

import java.util.HashMap;
import java.util.Map;

public final class LokiRenderer extends EntityRenderer<Loki, LokiRenderer.State> {
	private static final Identifier NORMAL = texture("loki");
	private static final Identifier FENRIR = texture("fenrir");
	private static final Identifier ILLUSION = texture("loki_illusion");
	private static final Identifier EYES = texture("fenrir_eyes");
	private GeoModel model, fenrir;

	public static final class State extends EntityRenderState {
		float yaw, headYaw, pitch, stride, walkSpeed, strike, intro, death;
		boolean rage, illusion, hurt;
		Loki.Attack attack;
	}

	public LokiRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.36f;
	}

	private static Identifier texture(String name) {
		return InfinityGauntlet.id("textures/entity/loki/" + name + ".png");
	}

	@Override
	protected float getShadowRadius(State state) { return state.rage ? 1.3f : shadowRadius; }

	@Override
	public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(Loki loki, State state, float partial) {
		super.extractRenderState(loki, state, partial);
		state.yaw = Mth.rotLerp(partial, loki.yBodyRotO, loki.yBodyRot);
		state.headYaw = Mth.wrapDegrees(loki.getYHeadRot() - state.yaw);
		state.pitch = loki.getXRot(partial);
		state.stride = loki.walkAnimation.position(partial);
		state.walkSpeed = Mth.clamp(loki.walkAnimation.speed(partial), 0, 1);
		state.attack = loki.attack();
		int total = state.attack.windup + state.attack.recovery;
		state.strike = loki.strikeTicks() > 0 ? (total - loki.strikeTicks() + partial) / total : -1;
		state.intro = loki.introTicks() > 0 ? Loki.INTRO_TICKS - loki.introTicks() + partial : -1;
		state.death = loki.isDeadOrDying() ? Mth.clamp((loki.deathTime + partial) / 20f, 0, 1) : 0;
		state.rage = loki.isEnraged();
		state.illusion = loki.isIllusion();
		state.hurt = loki.hurtTime > 0 && !loki.isDeadOrDying();
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (model == null) model = GeoModel.load(InfinityGauntlet.id("loki/loki.geo.json"));
		if (state.rage && fenrir == null) fenrir = GeoModel.load(InfinityGauntlet.id("loki/fenrir.geo.json"));
		GeoModel geometry = state.rage ? fenrir : model;
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		if (state.rage) wolfPose(state, bones);
		else humanoidPose(state, bones);

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - state.yaw));
		if (!state.rage) pose.scale(.78f, 1.015f, .85f);
		if (state.death > 0) pose.mulPose(Axis.ZP.rotationDegrees(state.death * (state.rage ? -25 : -65)));
		Identifier tex = state.illusion ? ILLUSION : state.rage ? FENRIR : NORMAL;
		float fade = state.intro >= 0 ? Mth.clamp(state.intro / 6f, 0.1f, 1f) : 1f;
		fade *= 1f - state.death;
		boolean translucent = state.illusion || fade < 0.995f;
		int color = ARGB.color((int) (255 * fade), 255, 255, 255);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(state.hurt));
		collector.submitCustomGeometry(pose, translucent ? RenderTypes.entityTranslucent(tex) : RenderTypes.entityCutout(tex),
				(p, out) -> geometry.render(ThanosRenderer.stack(p), out, bones, state.lightCoords, overlay, (bone, cube) -> color));
		if (state.rage) {
			collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EYES),
					(p, out) -> geometry.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
							OverlayTexture.NO_OVERLAY, (bone, cube) -> color));
		}
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	private static void humanoidPose(State state, Map<String, GeoModel.BonePose> bones) {
		float walk = Mth.sin(state.stride * .6662f) * state.walkSpeed;
		rotate(bones, "right_leg", walk * 43, 0, 0);
		rotate(bones, "left_leg", -walk * 43, 0, 0);
		rotate(bones, "right_coat", walk * 22, 0, 0);
		rotate(bones, "left_coat", -walk * 22, 0, 0);
		rotate(bones, "cape", 7 + state.walkSpeed * 25 + Mth.sin(state.ageInTicks * .16f) * 4, 0, walk * 6);
		rotate(bones, "head", -state.pitch, state.headYaw, 0);
		if (state.strike >= 0 && state.attack == Loki.Attack.DAGGER) {
			float swing = attackPose(state);
			rotate(bones, "body", 0, 35 * swing, 0);
			rotate(bones, "right_arm", -73 * swing, 4, 12 + 24 * swing);
			rotate(bones, "left_arm", 35 * swing, -12, -20);
		} else if (state.strike >= 0) {
			float cast = Math.abs(attackPose(state));
			rotate(bones, "right_arm", 92 * cast, -10, -18);
			rotate(bones, "left_arm", 76 * cast, 10, 18);
		} else {
			rotate(bones, "right_arm", -walk * 26 + 7, 0, -7);
			rotate(bones, "left_arm", walk * 26 + 7, 0, 7);
		}
	}

	private static void wolfPose(State state, Map<String, GeoModel.BonePose> bones) {
		float gait = Mth.sin(state.stride * .72f) * state.walkSpeed;
		float breath = Mth.sin(state.ageInTicks * .18f);
		rotate(bones, "front_l", gait * 31, 0, 0);
		rotate(bones, "front_r", -gait * 31, 0, 0);
		rotate(bones, "rear_l", -gait * 24, 0, 0);
		rotate(bones, "rear_r", gait * 24, 0, 0);
		rotate(bones, "tail", 4 * breath, gait * 22, 0);
		rotate(bones, "tail_end", -6 * breath, gait * 14, 0);
		float pitch = Mth.clamp(-state.pitch, -20, 20);
		float yaw = Mth.clamp(state.headYaw, -35, 35);
		rotate(bones, "head", pitch + breath * 2, yaw, 0);
		rotate(bones, "jaw", -3 + breath * 2, 0, 0);
		if (state.strike < 0) return;

		float swing = attackPose(state);
		float intensity = Math.abs(swing);
		switch (state.attack) {
			case BITE -> {
				rotate(bones, "body", -8 * swing, 0, 0);
				rotate(bones, "head", pitch - 25 * swing, yaw * .3f, 0);
				rotate(bones, "jaw", -34 * Math.max(0, swing), 0, 0);
			}
			case PAW -> {
				rotate(bones, "body", -8 * swing, 0, -9 * swing);
				rotate(bones, "front_r", 61 * swing, 0, -13 * intensity);
				rotate(bones, "head", pitch + 12 * swing, yaw, 0);
			}
			case ROAR, GROUNDING -> {
				rotate(bones, "body", 8 * intensity, 0, 0);
				rotate(bones, "head", pitch + 24 * intensity, yaw * .25f, 0);
				rotate(bones, "jaw", -39 * intensity, 0, 0);
			}
			default -> { }
		}
	}

	private static float attackPose(State state) {
		float impact = state.attack.windup / (float) (state.attack.windup + state.attack.recovery);
		if (state.strike < impact * .74f) return Mth.clamp(state.strike / (impact * .74f), 0, 1);
		if (state.strike < impact) return Mth.lerp((state.strike - impact * .74f) / (impact * .26f), 1, -.9f);
		return -.9f * Mth.clamp((1 - state.strike) / (1 - impact), 0, 1);
	}

	private static void rotate(Map<String, GeoModel.BonePose> bones, String name, float x, float y, float z) {
		GeoModel.BonePose bone = new GeoModel.BonePose();
		bone.rx = x;
		bone.ry = y;
		bone.rz = z;
		bones.put(name, bone);
	}
}
