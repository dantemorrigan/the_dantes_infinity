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
 * During the gesture ({@link GauntletGestureClient}) the first-person arm is gently raised and twisted.
 */
public final class WornGauntlet {
	/** Set while the first-person off-hand arm is being drawn for a worn gauntlet. */
	public static ItemStack firstPersonStack;
	public static GauntletGestureClient.Pose firstPersonGesture;
	public static HumanoidArm firstPersonArm = HumanoidArm.LEFT;

	private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();

	/** First-person gesture: how far (degrees) the arm is pitched up at full lift. */
	public static final float GESTURE_PITCH = -14f;

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
		pose.scale(0.92f, 0.583f, 1.15f);
		RENDER_STATE.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	private WornGauntlet() {}
}
