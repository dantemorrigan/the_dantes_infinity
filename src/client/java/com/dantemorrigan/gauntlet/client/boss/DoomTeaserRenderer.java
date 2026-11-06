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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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

	public static void render(LevelRenderContext ctx, PoseStack pose, float age, Vec3 targetEyes) {
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
		double feet = -(SpaceStone.PORTAL_SIZE / 2 - 0.1);
		double forward = Mth.lerp(opacity, -0.7, 0.72);
		if (targetEyes != null) lookAt(bones, targetEyes, feet, forward, opacity);
		pose.pushPose();
		// The portal's transform already faces its owner. Turn the -Z model toward that side.
		pose.translate(0, feet, forward);
		pose.mulPose(Axis.YP.rotationDegrees(180));
		int color = ARGB.colorFromFloat(opacity, 0.58f + opacity * 0.42f, 1, 0.69f + opacity * 0.31f);
		var renderType = opacity >= 1 ? RenderTypes.entityCutout(TEXTURE) : RenderTypes.entityTranslucent(TEXTURE);
		ctx.submitNodeCollector().submitCustomGeometry(pose, renderType, (p, out) ->
				Assets.MODEL_DATA.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, (b, c) -> color));
		pose.popPose();
	}

	private static void lookAt(Map<String, GeoModel.BonePose> bones, Vec3 target, double feet, double forward, float weight) {
		GeoModel.Bone head = Assets.MODEL_DATA.bone("head");
		if (head == null) return;
		PoseStack frame = new PoseStack();
		frame.translate(0, feet, forward);
		frame.mulPose(Axis.YP.rotationDegrees(180));
		// Aim in the animated body's space so a lean or a twist during the gesture does not turn his eyes away.
		if (head.parent != null) Assets.MODEL_DATA.transformTo(frame, head.parent.name, bones);
		Vector3f offset = frame.last().pose().invert().transformPosition(new Vector3f((float)target.x, (float)target.y, (float)target.z));
		// Eye sockets sit above and in front of the neck pivot in the Doom model.
		offset.sub(head.px / 16f, (head.py + 5.35f) / 16f, (head.pz - 3.8f) / 16f);
		if (offset.lengthSquared() < 0.0001f) return;
		// GeoModel negates the authored angles; the unrotated head faces -Z.
		float yaw = (float)Math.toDegrees(Math.atan2(offset.x, -offset.z));
		float pitch = (float)-Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));
		GeoModel.BonePose aim = bones.computeIfAbsent(head.name, key -> new GeoModel.BonePose());
		aim.ry = Mth.lerp(weight, aim.ry, Mth.clamp(yaw, -65, 65));
		aim.rx = Mth.lerp(weight, aim.rx, Mth.clamp(pitch, -35, 35));
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
