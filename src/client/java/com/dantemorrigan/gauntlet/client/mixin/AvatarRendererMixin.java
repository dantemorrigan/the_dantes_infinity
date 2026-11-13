package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.render.WornGauntlet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the worn gauntlet on top of the first-person arm drawn by {@link ItemInHandRendererMixin}. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	/** With Doom's cuirass on, the first-person arm is Doom's arm (and the gauntlet, if worn, still sits on it). */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$doomArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
			Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null || !player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(com.dantemorrigan.gauntlet.ModItems.DOOM_CHESTPLATE)) return;
		var model = (net.minecraft.client.model.player.PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		arm.resetPose();
		arm.visible = true;
		arm.zRot = right ? 0.1f : -0.1f;
		if (WornGauntlet.firstPersonStack != null && WornGauntlet.firstPersonGesture != null) {
			float side = WornGauntlet.firstPersonArm == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : -1;
			var g = WornGauntlet.firstPersonGesture;
			WornGauntlet.orientArm(arm, (WornGauntlet.GESTURE_PITCH - g.tilt()) * g.lift(), 0, side * g.roll());
		}
		com.dantemorrigan.gauntlet.client.render.DoomArmorLayer.firstPersonArm(poseStack, submitNodeCollector, lightCoords, arm, right);
		if (WornGauntlet.firstPersonStack != null) WornGauntlet.submitOnArm(arm, poseStack, submitNodeCollector, lightCoords, WornGauntlet.firstPersonStack);
		ci.cancel();
	}

	/** Gesture: raise the arm a little and twist the forearm slightly around its own axis. */
	@Inject(method = "renderHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
	private void infinityGauntlet$gestureTwist(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
			Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		if (WornGauntlet.firstPersonStack != null && WornGauntlet.firstPersonGesture != null) {
			float side = WornGauntlet.firstPersonArm == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : -1;
			var g = WornGauntlet.firstPersonGesture;
			WornGauntlet.orientArm(arm, (WornGauntlet.GESTURE_PITCH - g.tilt()) * g.lift(), 0, side * g.roll());
		}
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void infinityGauntlet$gauntletOnHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
			Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		if (WornGauntlet.firstPersonStack != null) {
			WornGauntlet.submitOnArm(arm, poseStack, submitNodeCollector, lightCoords, WornGauntlet.firstPersonStack);
		} else {
			// The bare hand in first person: show the Uru sleeve if the chestplate is worn.
			com.dantemorrigan.gauntlet.client.render.UruClient.firstPersonArm(
					(net.minecraft.client.model.player.PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel(), arm, poseStack, submitNodeCollector, lightCoords);
		}
	}
}
