package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The gauntlet worn on the arm (off hand). Third person uses the item model's thirdperson_lefthand transform,
 * which places the same cuboid on the forearm; first person draws the player's own arm and this on top of it.
 * Arm space: model-part units, +Y toward the hand. The cuboid (5x12x4 px, fingers +Y, stones on +Z) is turned so
 * the stones face outward and scaled to wrap the lower arm from the wrist to the fist.
 * <p>
 * During the gesture ({@link GauntletGestureClient}) separate finger segments are drawn out of the fist. They are
 * placed in the cuboid's own frame (item axes, unscaled, origin at its centre) where the scaled cuboid spans
 * x ±2.3, y ±3.5, z ±2.3 px: back of the hand (stones) on +Z, palm on -Z, fingertips on +Y.
 */
public final class WornGauntlet {
	/** Set while the first-person off-hand arm is being drawn for a worn gauntlet. */
	public static ItemStack firstPersonStack;
	public static GauntletGestureClient.Pose firstPersonGesture;
	public static HumanoidArm firstPersonArm = HumanoidArm.LEFT;

	private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();
	private static final ItemStackRenderState FINGER_STATE = new ItemStackRenderState();

	/** Scale of the worn cuboid (same as the thirdperson_lefthand display scale). */
	private static final float SX = 0.92f, SY = 0.583f, SZ = 1.15f;

	// Four fingers from the thumb side: offset across the hand (px, multiplied by thumb side), length (px).
	private static final float[][] FINGERS = {{1.62f, 2.9f}, {0.54f, 3.2f}, {-0.54f, 3.0f}, {-1.62f, 2.4f}};

	/** First-person gesture: the arm is pitched up and swung in (degrees) so the hand stands upright mid-view. */
	public static final float GESTURE_PITCH = -60f, GESTURE_SWING = -45f;

	public static void init() {}

	/** Orients a first-person arm part: pitch/yaw-out first (shoulder), then the forearm twist around its own axis. */
	public static void orientArm(ModelPart arm, float xDeg, float zDeg, float rollDeg) {
		org.joml.Quaternionf q = new org.joml.Quaternionf().rotationZYX(arm.zRot + (float) Math.toRadians(zDeg), 0, (float) Math.toRadians(xDeg))
				.rotateY((float) Math.toRadians(rollDeg));
		org.joml.Vector3f e = q.getEulerAnglesZYX(new org.joml.Vector3f());
		arm.xRot = e.x;
		arm.yRot = e.y;
		arm.zRot = e.z;
	}

	public static void submitOnArm(ModelPart arm, PoseStack pose, SubmitNodeCollector collector, int light, ItemStack gauntlet) {
		Minecraft mc = Minecraft.getInstance();
		ItemStack worn = gauntlet.copy();
		worn.set(DataComponents.ITEM_MODEL, InfinityGauntlet.id("gauntlet_worn"));
		mc.getItemModelResolver().updateForTopItem(RENDER_STATE, worn, ItemDisplayContext.NONE, mc.level, null, 0);
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(1 / 16f, 6.9f / 16f, 0);
		pose.mulPose(Axis.YP.rotationDegrees(90));
		if (firstPersonGesture != null) submitFingers(pose, collector, light, firstPersonGesture.open(), firstPersonArm);
		pose.scale(SX, SY, SZ);
		RENDER_STATE.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/**
	 * Third person: pose is the item-in-hand frame right before the item is submitted with the
	 * thirdperson_lefthand display transform (translation 0,-2,3.1 / rotation -90,90,0, mirrored for the left hand).
	 */
	public static void submitThirdPersonFingers(PoseStack pose, SubmitNodeCollector collector, int light, float open, HumanoidArm arm) {
		pose.pushPose();
		pose.translate(0, -2 / 16f, 3.1f / 16f);
		pose.mulPose(new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(-90), (float) Math.toRadians(-90), 0));
		submitFingers(pose, collector, light, open, arm);
		pose.popPose();
	}

	/** Fingers and thumb in the cuboid frame. open 0 = curled inside the fist (hidden), 1 = straight and spread. */
	private static void submitFingers(PoseStack pose, SubmitNodeCollector collector, int light, float open, HumanoidArm arm) {
		if (open <= 0.001f) return;
		Minecraft mc = Minecraft.getInstance();
		ItemStack finger = new ItemStack(net.minecraft.world.item.Items.STICK);
		finger.set(DataComponents.ITEM_MODEL, InfinityGauntlet.id("gauntlet_finger"));
		mc.getItemModelResolver().updateForTopItem(FINGER_STATE, finger, ItemDisplayContext.NONE, mc.level, null, 0);
		float side = arm == HumanoidArm.LEFT ? 1 : -1;
		float curl = (1 - open) * 100f;
		for (float[] f : FINGERS) {
			float x = f[0] * side, length = f[1];
			pose.pushPose();
			pose.translate(x / 16f, 3.1f / 16f, 0.3f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(-x * 7f * open));
			segment(pose, collector, light, curl, length * 0.55f, 0.95f);
			segment(pose, collector, light, curl * 0.9f, length * 0.45f, 0.9f);
			pose.popPose();
		}
		// Thumb: from the side of the palm, out and up when open, across the fist when closed.
		pose.pushPose();
		pose.translate(side * 2.0f / 16f, 0.6f / 16f, -0.7f / 16f);
		pose.mulPose(Axis.ZP.rotationDegrees(side * mix(-38f, 30f, open)));
		pose.mulPose(Axis.XP.rotationDegrees(-mix(15f, 75f, open)));
		segment(pose, collector, light, 0, 1.5f, 1.05f);
		segment(pose, collector, light, (1 - open) * 45f, 1.2f, 0.95f);
		pose.popPose();
	}

	private static float mix(float openValue, float closedValue, float open) {
		return closedValue + (openValue - closedValue) * open;
	}

	/** One finger segment: bends toward the palm by curl degrees around its base, draws, then moves to its tip. */
	private static void segment(PoseStack pose, SubmitNodeCollector collector, int light, float curl, float length, float width) {
		pose.mulPose(Axis.XP.rotationDegrees(-curl));
		pose.pushPose();
		pose.scale(width, length, width);
		FINGER_STATE.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
		pose.translate(0, length / 16f, 0);
	}

	private WornGauntlet() {}
}
