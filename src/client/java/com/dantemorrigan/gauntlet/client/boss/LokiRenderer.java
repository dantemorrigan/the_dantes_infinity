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
	private static final Identifier RAGE = texture("loki_rage");
	private static final Identifier ILLUSION = texture("loki_illusion");
	private static final Identifier EMISSIVE = texture("loki_rage_emissive");
	private GeoModel model;

	public static final class State extends EntityRenderState {
		float yaw, headYaw, pitch, stride, walkSpeed, strike, intro, death;
		boolean rage, illusion, hurt;
	}

	public LokiRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.36f;
	}

	private static Identifier texture(String name) {
		return InfinityGauntlet.id("textures/entity/loki/" + name + ".png");
	}

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
		state.strike = loki.strikeTicks() > 0 ? (Loki.WINDUP - loki.strikeTicks() + partial) / Loki.WINDUP : -1;
		state.intro = loki.introTicks() > 0 ? Loki.INTRO_TICKS - loki.introTicks() + partial : -1;
		state.death = loki.isDeadOrDying() ? Mth.clamp((loki.deathTime + partial) / 20f, 0, 1) : 0;
		state.rage = loki.isEnraged();
		state.illusion = loki.isIllusion();
		state.hurt = loki.hurtTime > 0 && !loki.isDeadOrDying();
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (model == null) model = GeoModel.load(InfinityGauntlet.id("loki/loki.geo.json"));
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		float walk = Mth.sin(state.stride * 0.6662f) * state.walkSpeed;
		rotate(bones, "right_leg", walk * 43, 0, 0);
		rotate(bones, "left_leg", -walk * 43, 0, 0);
		rotate(bones, "right_coat", walk * 22, 0, 0);
		rotate(bones, "left_coat", -walk * 22, 0, 0);
		rotate(bones, "cape", 7 + state.walkSpeed * 25 + Mth.sin(state.ageInTicks * 0.16f) * 4, 0, walk * 6);
		rotate(bones, "head", -state.pitch, state.headYaw, 0);
		if (state.strike >= 0) {
			float sweep = Mth.clamp((state.strike - 0.36f) / 0.64f, 0, 1);
			rotate(bones, "body", 0, 30 - sweep * 68, 0);
			rotate(bones, "right_arm", -48 + sweep * 118, 4, 18 - sweep * 50);
			rotate(bones, "left_arm", 14 + sweep * 52, -12, -20);
		} else {
			rotate(bones, "right_arm", -walk * 26 + 7, 0, -7);
			rotate(bones, "left_arm", walk * 26 + 7, 0, 7);
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - state.yaw));
		if (state.death > 0) pose.mulPose(Axis.ZP.rotationDegrees(state.death * -65));
		Identifier tex = state.illusion ? ILLUSION : state.rage ? RAGE : NORMAL;
		float fade = state.intro >= 0 ? Mth.clamp(state.intro / 6f, 0.1f, 1f) : 1f;
		fade *= 1f - state.death;
		boolean translucent = state.illusion || fade < 0.995f;
		int color = ARGB.color((int) (255 * fade), 255, 255, 255);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(state.hurt));
		collector.submitCustomGeometry(pose, translucent ? RenderTypes.entityTranslucent(tex) : RenderTypes.entityCutout(tex),
				(p, out) -> model.render(ThanosRenderer.stack(p), out, bones, state.lightCoords, overlay, (bone, cube) -> color));
		if (state.rage) {
			collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EMISSIVE),
					(p, out) -> model.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
							OverlayTexture.NO_OVERLAY, (bone, cube) -> color));
		}
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}

	private static void rotate(Map<String, GeoModel.BonePose> bones, String name, float x, float y, float z) {
		GeoModel.BonePose bone = new GeoModel.BonePose();
		bone.rx = x;
		bone.ry = y;
		bone.rz = z;
		bones.put(name, bone);
	}
}
