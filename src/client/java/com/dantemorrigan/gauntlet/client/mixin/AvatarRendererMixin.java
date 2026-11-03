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
		}
	}
}
