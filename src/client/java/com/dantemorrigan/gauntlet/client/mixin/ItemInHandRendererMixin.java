package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.ModItems;
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
	@Shadow
	protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight, float attackValue, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$wornArm(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
			ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (hand != InteractionHand.OFF_HAND || !itemStack.is(ModItems.GAUNTLET) || player.isScoping() || player.isInvisible()) return;
		HumanoidArm arm = player.getMainArm().getOpposite();
		poseStack.pushPose();
		WornGauntlet.firstPersonStack = itemStack;
		try {
			this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
		} finally {
			WornGauntlet.firstPersonStack = null;
			poseStack.popPose();
		}
		ci.cancel();
	}
}
