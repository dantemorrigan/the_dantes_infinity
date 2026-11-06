package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.LivingTribunal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class TribunalRenderer extends EntityRenderer<LivingTribunal, TribunalRenderer.State> {
	private static final Identifier GOLD = InfinityGauntlet.id("textures/entity/tribunal/tribunal.png");
	private static final Identifier EMISSIVE = InfinityGauntlet.id("textures/entity/tribunal/tribunal_emissive.png");
	private static final Identifier HOLE = InfinityGauntlet.id("textures/tribunal/singularity.png");
	private static final Identifier HALO = InfinityGauntlet.id("textures/tribunal/halo.png");
	private static final Identifier EDICT = InfinityGauntlet.id("textures/tribunal/edict.png");
	private static final Identifier GEOMETRY = InfinityGauntlet.id("tribunal/tribunal.geo.json");
	private static final float SCALE = 16;
	private static GeoModel model;

	public TribunalRenderer(EntityRendererProvider.Context context) { super(context); }

	public static final class State extends EntityRenderState {
		int phase, attack;
		float yaw, ticks, death;
		Vec3 mark = Vec3.ZERO, base = Vec3.ZERO;
		final ItemStackRenderState gauntlet = new ItemStackRenderState();
	}

	@Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(LivingTribunal e) { return e.getBoundingBox().inflate(30); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(LivingTribunal boss, State state, float partial) {
		super.extractRenderState(boss, state, partial);
		state.phase = boss.phase();
		state.attack = boss.attack();
		state.yaw = boss.getYRot();
		state.ticks = boss.sceneAge() + partial;
		state.death = boss.deathTime + partial;
		state.mark = boss.mark();
		state.base = boss.getPosition(partial);
		if (state.phase == LivingTribunal.VERDICT && state.ticks < LivingTribunal.SHATTER_TICK) {
			ItemStack stack = new ItemStack(ModItems.GAUNTLET);
			for (Stone stone : Stone.values()) GauntletState.putStone(stack, new ItemStack(stone.item()));
			stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, InfinityGauntlet.id("gauntlet_worn"));
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.gauntlet, stack,
					ItemDisplayContext.NONE, boss.level(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (model == null) model = GeoModel.load(GEOMETRY);
		float reveal = state.phase == LivingTribunal.ARRIVAL ? smooth((state.ticks - 20) / 85) : 1;
		float fade = state.phase == LivingTribunal.DEPARTURE ? 1 - smooth(state.death / 52) : reveal;
		float rise = -30 * (1 - reveal);
		if (state.phase == LivingTribunal.ARRIVAL) {
			float radius = 34 * smooth(state.ticks / 18) * (1 - smooth((state.ticks - 95) / 35));
			Vec3 behind = Vec3.directionFromRotation(0, state.yaw).scale(-12).add(0, 24, 0);
			billboard(pose, collector, HOLE, behind, radius, 0, 0xffffffff);
			billboard(pose, collector, HALO, behind, radius * 1.15f, state.ticks * 2.1f, 0xe6ffffff);
		}
		Map<String, GeoModel.BonePose> bones = bones(state, reveal);

		if (fade > 0) {
			pose.pushPose();
			modelTransform(pose, state.yaw, rise);
			int tint = ARGB.color((int) (fade * 255), 255, 255, 255);
			collector.submitCustomGeometry(pose, fade < .99f ? RenderTypes.entityTranslucent(GOLD) : RenderTypes.entityCutout(GOLD),
					(p, out) -> model.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
							OverlayTexture.NO_OVERLAY, (bone, cube) -> tint));
			collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EMISSIVE),
					(p, out) -> model.render(ThanosRenderer.stack(p), out, bones, LightCoordsUtil.FULL_BRIGHT,
							OverlayTexture.NO_OVERLAY, (bone, cube) -> tint));
			pose.popPose();
		}
		if (state.attack > 0) {
			Vec3 at = state.mark.subtract(state.base);
			float radius = state.attack == 3 ? 20 : state.attack == 2 ? 13 : 8;
			float opacity = 1 - smooth((state.ticks - LivingTribunal.WINDUP) / 12);
			int color = ARGB.color((int) (opacity * 235), 255, 216, 151);
			billboard(pose, collector, EDICT, at, radius * (state.ticks < LivingTribunal.WINDUP ? 1 : 1.3f),
					state.ticks * 2, color);
			// The edict focuses into a cosmic ray; the marked radius is the server's actual hit radius.
			beam(pose, collector, new Vec3(0, 31, -3), at, state.ticks < LivingTribunal.WINDUP ? .12f : 1.6f,
					ARGB.color((int) (opacity * (state.ticks < LivingTribunal.WINDUP ? 85 : 255)), 230, 238, 255));
			if (state.ticks >= LivingTribunal.WINDUP) {
				billboard(pose, collector, HALO, at, radius * 1.65f, state.ticks * 5, color);
			}
		}
		if (state.phase == LivingTribunal.VERDICT) {
			PoseStack handPose = new PoseStack();
			modelTransform(handPose, state.yaw, 0);
			Vec3 hand = new Vec3(model.pivotOf("right_hand", bones, handPose)).add(palmOffset(state.yaw));
			float progress = smooth((state.ticks - 16) / 70);
			Vec3 glove = state.mark.subtract(state.base).lerp(hand, progress).add(0, 5 * Mth.sin(Mth.PI * progress), 0);
			if (state.ticks < LivingTribunal.SHATTER_TICK) {
				pose.pushPose();
				pose.translate(glove.x, glove.y, glove.z);
				pose.mulPose(Axis.YP.rotationDegrees(state.ticks * 8));
				float scale = 1.2f + progress * 4.8f;
				pose.scale(scale, scale, scale);
				state.gauntlet.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
				billboard(pose, collector, HALO, glove, 1.1f + progress * 2.9f, -state.ticks * 5, 0xadffffff);
			} else {
				float shatter = smooth((state.ticks - LivingTribunal.SHATTER_TICK) / 24);
				billboard(pose, collector, EDICT, hand, 2 + 17 * shatter, state.ticks * 5,
						ARGB.color((int) (255 * (1 - shatter)), 255, 246, 206));
			}
		}
		super.submit(state, pose, collector, camera);
	}

	private static Map<String, GeoModel.BonePose> bones(State state, float reveal) {
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		float reach = state.phase == LivingTribunal.VERDICT ? smooth(state.ticks / 28)
				: state.attack > 0 ? Mth.sin(Mth.PI * Mth.clamp(state.ticks / 48, 0, 1)) : 0;
		float open = state.phase == LivingTribunal.ARRIVAL ? Mth.sin(reveal * Mth.PI) : 0;
		bone(bones, "right_arm", -70 * reach - 9 * open, -14 * reach, -10 - 24 * open);
		bone(bones, "left_arm", -28 * reach - 9 * open, 0, 10 + 24 * open);
		bone(bones, "right_forearm", -27 * reach, 0, 0);
		bone(bones, "left_forearm", -16 * reach, 0, 0);
		bone(bones, "right_hand", 19 * smooth((state.ticks - 87) / 18) * reach, 0, 0);
		bone(bones, "head", state.phase == LivingTribunal.DEPARTURE ? 32 * smooth(state.death / 52) : 8 * reach,
				6 * Mth.sin(state.ageInTicks * .048f), 0);
		bone(bones, "body", 0, 0, 2 * Mth.sin(state.ageInTicks * .039f));

		return bones;
	}

	public static Vec3 handPosition(LivingTribunal boss, float partial) {
		if (model == null) model = GeoModel.load(GEOMETRY);
		State state = new State();
		state.phase = boss.phase(); state.attack = boss.attack(); state.ticks = boss.sceneAge() + partial;
		state.yaw = boss.getYRot(); state.ageInTicks = boss.tickCount + partial;
		PoseStack pose = new PoseStack(); modelTransform(pose, state.yaw, 0);
		return boss.getPosition(partial).add(new Vec3(model.pivotOf("right_hand", bones(state, 1), pose))).add(palmOffset(state.yaw));
	}

	private static Vec3 palmOffset(float yaw) { return Vec3.directionFromRotation(0, yaw).scale(4).add(0, 1.5, 0); }

	private static void modelTransform(PoseStack pose, float yaw, float rise) {
		pose.translate(0, rise, 0);
		pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
		pose.scale(SCALE, SCALE, SCALE);
	}

	private static void billboard(PoseStack pose, SubmitNodeCollector collector, Identifier texture, Vec3 at,
			float radius, float roll, int color) {
		if (radius < .01f || (color >>> 24) == 0) return;
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		collector.order(-1).submitCustomGeometry(pose, texture.equals(HOLE) ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityTranslucentEmissive(texture), (p, out) -> {
			vertex(out, p, -radius, -radius, 0, 1, color);
			vertex(out, p, radius, -radius, 1, 1, color);
			vertex(out, p, radius, radius, 1, 0, color);
			vertex(out, p, -radius, radius, 0, 0, color);
		});
		pose.popPose();
	}

	private static void beam(PoseStack pose, SubmitNodeCollector collector, Vec3 from, Vec3 to, float width, int color) {
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() < .01) return;
		float length = (float) d.length();
		pose.pushPose(); pose.translate(from.x, from.y, from.z);
		pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,0,1), d.normalize().toVector3f()));
		collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(HALO), (p,out) -> {
			for (int side = 0; side < 2; side++) {
				float x = side == 0 ? width : 0, y = side == 0 ? 0 : width;
				out.addVertex(p,-x,-y,0).setColor(color).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,x,y,0).setColor(color).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,x,y,length).setColor(color).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,-x,-y,length).setColor(color).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
			}
		});
		pose.popPose();
	}

	private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		out.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}

	private static void bone(Map<String, GeoModel.BonePose> bones, String name, float x, float y, float z) {
		var pose = new GeoModel.BonePose();
		pose.rx = x; pose.ry = y; pose.rz = z;
		bones.put(name, pose);
	}

	private static float smooth(float t) {
		float v = Mth.clamp(t, 0, 1);
		return v * v * (3 - 2 * v);
	}
}
