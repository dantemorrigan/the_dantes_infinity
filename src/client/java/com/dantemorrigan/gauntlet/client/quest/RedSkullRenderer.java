package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.boss.GeoAnimation;
import com.dantemorrigan.gauntlet.client.boss.GeoModel;
import com.dantemorrigan.gauntlet.stones.RedSkull;
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

/** The Red Skull, drawn from his Bedrock model: he rises into view, hovers, gestures when he speaks and fades away when he leaves. */
public final class RedSkullRenderer extends EntityRenderer<RedSkull, RedSkullRenderer.State> {
	private static final GeoModel MODEL = GeoModel.load(InfinityGauntlet.id("red_skull/red_skull.geo.json"));
	private static final Map<String, GeoAnimation> CLIPS = GeoAnimation.load(InfinityGauntlet.id("red_skull/red_skull.animation.json"));
	private static final Identifier TEXTURE = InfinityGauntlet.id("textures/entity/red_skull/red_skull.png");
	private static final float APPEAR = 2.4f, GUIDE = 3.2f, DEPART = 2.3f;

	public static final class State extends EntityRenderState {
		float bodyYaw, life, depart = -1, guide = -1;
	}

	public RedSkullRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public State createRenderState() { return new State(); }

	@Override
	protected AABB getBoundingBoxForCulling(RedSkull entity) {
		return super.getBoundingBoxForCulling(entity).inflate(1.5, 1.0, 1.5);
	}

	@Override
	public void extractRenderState(RedSkull e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		s.life = (e.tickCount + partial) / 20f;
		s.depart = e.departing() ? (e.departAge() + partial) / 20f : -1;
		float since = (e.sinceTalk() + partial) / 20f;
		s.guide = since >= 0 && since < GUIDE ? since : -1;
	}

	private static void play(Map<String, GeoModel.BonePose> bones, String name, float seconds, float weight) {
		GeoAnimation clip = CLIPS.get(name);
		if (clip != null) clip.apply(bones, seconds, weight);
	}

	private static PoseStack stack(PoseStack.Pose p) {
		PoseStack s = new PoseStack();
		s.last().pose().set(p.pose());
		s.last().normal().set(p.normal());
		return s;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		float alpha = 1f;
		if (s.depart >= 0) {
			play(bones, "depart", Math.min(s.depart, DEPART), 1);
			alpha = Mth.clamp(1 - s.depart / DEPART, 0, 1);
		} else {
			if (s.life < APPEAR) {
				play(bones, "appear", s.life, 1);
				alpha = Mth.clamp(s.life / 1.4f, 0, 1);
			} else play(bones, "idle", s.life, 1);
			if (s.guide >= 0) play(bones, "guide", s.guide, 1);
		}
		if (alpha <= 0.01f) return;
		int overlay = OverlayTexture.NO_OVERLAY;
		// Never fully dark: he is a pale thing in a dim place.
		int light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(s.lightCoords), 9), LightCoordsUtil.sky(s.lightCoords));
		int color = ARGB.color((int) (alpha * 255), 255, 255, 255);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - s.bodyYaw));
		var type = alpha < 1f ? RenderTypes.entityTranslucent(TEXTURE) : RenderTypes.entityCutout(TEXTURE);
		collector.submitCustomGeometry(pose, type, (p, out) -> MODEL.render(stack(p), out, bones, light, overlay, (b, c) -> color));
		pose.popPose();
		super.submit(s, pose, collector, camera);
	}
}
