package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera shake for the power strike / planet destruction. Zero offset when nothing is shaking. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private float yRot;
	@Shadow private float xRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void infinityGauntlet$shake(float partialTicks, CallbackInfo ci) {
		float dy = Cutscene.shakeYaw(partialTicks);
		float dx = Cutscene.shakePitch(partialTicks);
		if (dy != 0 || dx != 0) this.setRotation(this.yRot + dy, this.xRot + dx);
	}
}
