package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import com.dantemorrigan.gauntlet.client.render.WornGauntlet;
import com.mojang.blaze3d.vertex.PoseStack;
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
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void infinityGauntlet$tribunalCinematic(CallbackInfo ci) {
        if (com.dantemorrigan.gauntlet.client.boss.TribunalClient.cinematic() || com.dantemorrigan.gauntlet.client.doom.GodDoomScene.active()) ci.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void infinityGauntlet$stormbreakerStart(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
            ItemStack item, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (!infinityGauntlet$stormPose(player, hand, item)) return;
        pose.pushPose();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.firstPerson(
                pose, player.getId(), frameInterp, arm == HumanoidArm.LEFT);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void infinityGauntlet$stormbreakerEnd(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
            ItemStack item, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (infinityGauntlet$stormPose(player, hand, item)) pose.popPose();
    }

    private static boolean infinityGauntlet$stormPose(AbstractClientPlayer player, InteractionHand hand, ItemStack item) {
        return item.is(ModItems.STORMBREAKER) || hand == InteractionHand.MAIN_HAND
                && com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.isPulling(player.getId());
    }

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
			// Lift the hand slightly in place; no swing or equip bob while gesturing.
			float lift = gesture.lift();
			poseStack.translate(0, 0.05f * lift, 0);
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
