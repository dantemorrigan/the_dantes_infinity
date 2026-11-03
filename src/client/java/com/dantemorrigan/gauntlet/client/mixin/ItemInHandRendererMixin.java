package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import com.dantemorrigan.gauntlet.client.render.WornGauntlet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: a worn (off-hand) gauntlet is drawn as the player's arm wearing it, not as a held item. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight, float attackValue, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$wornArm(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
			ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (hand != InteractionHand.OFF_HAND || !itemStack.is(ModItems.GAUNTLET) || player.isScoping() || player.isInvisible()) return;
		HumanoidArm arm = player.getMainArm().getOpposite();
		GauntletGestureClient.Pose gesture = GauntletGestureClient.pose(player.getId());
		poseStack.pushPose();
		if (gesture != null) {
			// Bring the hand up toward the middle of the view, forearm angled in from the lower corner.
			float invert = arm == HumanoidArm.RIGHT ? 1 : -1, lift = gesture.lift();
			poseStack.translate(-invert * 0.30f * lift, 0.22f * lift, -0.10f * lift);
			poseStack.mulPose(Axis.ZP.rotationDegrees(-invert * 18f * lift));
			poseStack.mulPose(Axis.XP.rotationDegrees(gesture.tilt() * lift));
			inverseArmHeight *= 1 - lift;
			attack *= 1 - lift;
		}
		WornGauntlet.firstPersonStack = itemStack;
		WornGauntlet.firstPersonGesture = gesture;
		WornGauntlet.firstPersonArm = arm;
		try {
			this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
		} finally {
			WornGauntlet.firstPersonStack = null;
			WornGauntlet.firstPersonGesture = null;
			poseStack.popPose();
		}
		ci.cancel();
	}
}
