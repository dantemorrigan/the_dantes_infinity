package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/**
 * Thanos from the pack's Bedrock models (with / without his gauntlet), the double sword in his right hand, the pack's
 * keyframe animations plus a few made here (backhand, war cry, kneeling, leaving). The defeat ends with the body
 * crumbling to dust piece by piece, from the hands and head inward, like in Endgame.
 */
public final class ThanosRenderer extends EntityRenderer<Thanos, ThanosRenderer.State> {
	public static final Identifier ATLAS = InfinityGauntlet.id("textures/entity/thanos/titan_atlas.png");
	public static final Identifier EMISSIVE = InfinityGauntlet.id("textures/entity/thanos/titan_atlas_emissive.png");

	public static final class State extends EntityRenderState {
		float bodyYaw;
		int action;
		float actionTime;
		float walkPos, walkSpeed;
		boolean gauntlet, swordOut, enraged, hurt;
		float deathTime;
		float headPitch;
	}

	public ThanosRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Thanos entity) {
		return super.getBoundingBoxForCulling(entity).inflate(2.0, 1.0, 2.0);
	}

	@Override
	public void extractRenderState(Thanos e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		s.action = e.action();
		s.actionTime = (e.tickCount - e.actionStartTick + partial) / 20f;
		s.walkPos = e.walkAnimation.position(partial);
		s.walkSpeed = Math.min(1f, e.walkAnimation.speed(partial));
		s.gauntlet = e.hasGauntlet();
		s.swordOut = e.flag(Thanos.FLAG_SWORD_OUT);
		s.enraged = e.flag(Thanos.FLAG_ENRAGED);
		s.hurt = e.hurtTime > 0 && !e.isDeadOrDying();
		s.deathTime = e.isDeadOrDying() ? e.deathTime + partial : 0;
		s.headPitch = e.getXRot(partial);
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		GeoModel model = ThanosModels.body(s.gauntlet);
		Map<String, GeoModel.BonePose> poses = ThanosModels.pose(s.action, s.actionTime, s.walkPos, s.walkSpeed, s.ageInTicks, s.deathTime, s.swordOut);
		ThanosModels.headPitch(poses, s.action == Thanos.A_IDLE ? s.headPitch : 0);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(s.hurt));
		int light = s.lightCoords;

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyYaw));
		float dust = s.deathTime <= Thanos.DUST_START ? 0 : (s.deathTime - Thanos.DUST_START) / (Thanos.DEATH_TICKS - Thanos.DUST_START);
		if (dust <= 0) {
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(ATLAS), (p, out) ->
					model.render(stack(p), out, poses, light, overlay, null));
			collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EMISSIVE), (p, out) ->
					model.render(stack(p), out, poses, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
							(b, c) -> ARGB.color(s.enraged ? 255 : 200, 255, 255, 255)));
			if (!s.swordOut) submitSword(model, poses, pose, collector, light, overlay, 1f);
		} else {
			// Each piece greys out, then flakes away: extremities first, the core last.
			GeoModel.CubeTint tint = (bone, cube) -> {
				float a = dustAlpha(bone, cube, dust);
				if (a <= 0.01f) return 0;
				float grey = Mth.clamp(dust * 3f, 0, 1);
				int r = (int) Mth.lerp(grey, 255, 170), g = (int) Mth.lerp(grey, 255, 150), b = (int) Mth.lerp(grey, 255, 130);
				return ARGB.color((int) (a * 255), r, g, b);
			};
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(ATLAS), (p, out) ->
					model.render(stack(p), out, poses, light, OverlayTexture.NO_OVERLAY, tint));
			if (dust < 0.45f) submitSword(model, poses, pose, collector, light, OverlayTexture.NO_OVERLAY, 1 - dust / 0.45f);
		}
		pose.popPose();
		super.submit(s, pose, collector, camera);
	}

	/** 0..1 visibility of a cube while the body turns to dust. */
	static float dustAlpha(GeoModel.Bone bone, GeoModel.Cube cube, float dust) {
		float order = switch (bone.name) {
			case "left_hand", "right_hand", "head" -> 0.0f;
			case "left_forearm", "right_forearm" -> 0.12f;
			case "left_arm", "right_arm", "left_shin", "right_shin" -> 0.28f;
			case "left_leg", "right_leg" -> 0.42f;
			default -> 0.5f;
		};
		float start = order + cube.seed * 0.3f;
		return Mth.clamp(1 - (dust - start) / 0.22f, 0, 1);
	}

	private static void submitSword(GeoModel body, Map<String, GeoModel.BonePose> poses, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay, float alpha) {
		pose.pushPose();
		body.transformTo(pose, "sword_socket", poses);
		GeoModel.Bone socket = body.bone("sword_socket");
		if (socket != null) pose.translate(socket.px / 16f, socket.py / 16f, socket.pz / 16f);
		// Long axis of the sword (X) runs forward out of the fist, the edges up and down.
		pose.mulPose(Axis.YP.rotationDegrees(90));
		GeoModel sword = ThanosModels.sword();
		RenderType type = alpha < 1 ? RenderTypes.entityTranslucent(ATLAS) : RenderTypes.entityCutout(ATLAS);
		int color = ARGB.color((int) (alpha * 255), 255, 255, 255);
		collector.submitCustomGeometry(pose, type, (p, out) -> sword.render(stack(p), out, null, light, overlay, (b, c) -> color));
		pose.popPose();
	}

	static PoseStack stack(PoseStack.Pose p) {
		PoseStack s = new PoseStack();
		s.last().pose().set(p.pose());
		s.last().normal().set(p.normal());
		return s;
	}
}
