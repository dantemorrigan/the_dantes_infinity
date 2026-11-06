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
	private static final int APPEAR = 12, PUSH_START = SpaceStone.LATVERIA_PUSH_TICK - 18, FADE = 65;

	private static final class Assets {
		private static final GeoModel MODEL_DATA = GeoModel.load(MODEL);
		private static final Map<String, GeoAnimation> CLIPS = GeoAnimation.load(ANIMATIONS);
	}

	public static void render(LevelRenderContext ctx, PoseStack pose, float age, float remaining) {
		float opacity = Mth.clamp((age - APPEAR) / 12, 0, 1) * Mth.clamp((remaining - 9) / 14, 0, 1);
		if (opacity <= 0) return;
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		if (age < PUSH_START) {
			apply(bones, "intro", (age - APPEAR) / (PUSH_START - APPEAR) * 5);
		} else if (age < FADE) {
			apply(bones, "gauntlet_bolt", (age - PUSH_START) / 20);
		} else {
			apply(bones, "idle", (age - FADE) / 20);
		}
		pose.pushPose();
		// The portal's transform already faces its owner. Turn the -Z model toward that side.
		pose.translate(0, -(SpaceStone.PORTAL_SIZE / 2 - 0.1), 0.62);
		pose.mulPose(Axis.YP.rotationDegrees(180));
		int color = ARGB.colorFromFloat(opacity, 1, 1, 1);
		var renderType = opacity >= 1 ? RenderTypes.entityCutout(TEXTURE) : RenderTypes.entityTranslucent(TEXTURE);
		ctx.submitNodeCollector().submitCustomGeometry(pose, renderType, (p, out) ->
				Assets.MODEL_DATA.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, (b, c) -> color));
		pose.popPose();
	}

	private static void apply(Map<String, GeoModel.BonePose> bones, String name, float seconds) {
		GeoAnimation clip = Assets.CLIPS.get(name);
		if (clip != null) clip.apply(bones, seconds, 1);
	}

	private DoomTeaserRenderer() {}
}
