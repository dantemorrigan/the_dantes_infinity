package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.render.JetLean;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Under Doom's jets the whole body leans into the flight (see {@link JetLean}). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarJetLeanMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void infinityGauntlet$jetLean(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
		JetLean.apply(state, pose, bodyRot);
	}
}
