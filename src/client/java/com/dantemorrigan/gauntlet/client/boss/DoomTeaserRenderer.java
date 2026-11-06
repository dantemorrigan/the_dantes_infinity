package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** A visual apparition for the Latveria preview. It has no entity or hitbox. */
public final class DoomTeaserRenderer {
	private static final Identifier MODEL = InfinityGauntlet.id("doctor_doom/doctor_doom.geo.json");
	private static final Identifier ANIMATIONS = InfinityGauntlet.id("doctor_doom/doctor_doom.animation.json");
	private static final Identifier TEXTURE = InfinityGauntlet.id("textures/entity/doctor_doom/doctor_doom.png");
	// The hand reaches its impact keyframe 0.9 s into the gesture.
	private static final int PUSH_START = SpaceStone.LATVERIA_PUSH_TICK - 18, PUSH_END = PUSH_START + 38;

	private static final class Assets {
		private static final GeoModel MODEL_DATA = GeoModel.load(MODEL);
		private static final Map<String, GeoAnimation> CLIPS = GeoAnimation.load(ANIMATIONS);
	}

	public static void render(LevelRenderContext ctx, PoseStack pose, float age) {
		if (age <= SpaceStone.LATVERIA_ARRIVE || age >= SpaceStone.LATVERIA_VANISH) return;
		float arrive = progress(age, SpaceStone.LATVERIA_ARRIVE, SpaceStone.LATVERIA_VISIBLE);
		float leave = progress(age, SpaceStone.LATVERIA_DEPART, SpaceStone.LATVERIA_VANISH);
		float opacity = smooth(arrive) * (1 - smooth(leave));
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		if (age < SpaceStone.LATVERIA_VISIBLE) {
			apply(bones, "intro", arrive * 5);
		} else if (age < PUSH_START) {
			apply(bones, "idle", (age - SpaceStone.LATVERIA_VISIBLE) / 20);
		} else if (age < PUSH_END) {
			apply(bones, "gauntlet_bolt", (age - PUSH_START) / 20);
		} else if (age < SpaceStone.LATVERIA_DEPART) {
			apply(bones, "idle", (age - PUSH_END) / 20);
		} else {
			apply(bones, "intro", (1 - leave) * 5);
		}
		pose.pushPose();
		// The portal's transform already faces its owner. Turn the -Z model toward that side.
		pose.translate(0, -(SpaceStone.PORTAL_SIZE / 2 - 0.1), Mth.lerp(opacity, -0.7, 0.72));
		pose.mulPose(Axis.YP.rotationDegrees(180));
		int color = ARGB.colorFromFloat(opacity, 0.58f + opacity * 0.42f, 1, 0.69f + opacity * 0.31f);
		var renderType = opacity >= 1 ? RenderTypes.entityCutout(TEXTURE) : RenderTypes.entityTranslucent(TEXTURE);
		ctx.submitNodeCollector().submitCustomGeometry(pose, renderType, (p, out) ->
				Assets.MODEL_DATA.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, (b, c) -> color));
		pose.popPose();
	}

	private static float progress(float age, int start, int end) {
		return Mth.clamp((age - start) / (end - start), 0, 1);
	}

	private static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}

	private static void apply(Map<String, GeoModel.BonePose> bones, String name, float seconds) {
		GeoAnimation clip = Assets.CLIPS.get(name);
		if (clip != null) clip.apply(bones, seconds, 1);
	}

	private DoomTeaserRenderer() {}
}
