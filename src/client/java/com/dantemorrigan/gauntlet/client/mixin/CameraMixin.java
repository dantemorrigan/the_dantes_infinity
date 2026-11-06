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
		var focus = com.dantemorrigan.gauntlet.client.boss.TribunalClient.cameraFocus(partialTicks);
		if (focus != null) {
			var camera = (Camera) (Object) this;
			var direction = focus.subtract(camera.position());
			this.setRotation((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
					(float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
		}
		float scale = com.dantemorrigan.gauntlet.GauntletConfig.get().cameraShakeScale;
		if (scale <= 0f) return;
		float dy = com.dantemorrigan.gauntlet.client.boss.TribunalClient.shake(partialTicks, false) * scale + Cutscene.shakeYaw(partialTicks) + (com.dantemorrigan.gauntlet.client.boss.ThanosClient.shakeYaw(partialTicks) + com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.shake(partialTicks, false)) * scale;
		float dx = com.dantemorrigan.gauntlet.client.boss.TribunalClient.shake(partialTicks, true) * scale + Cutscene.shakePitch(partialTicks) + (com.dantemorrigan.gauntlet.client.boss.ThanosClient.shakePitch(partialTicks) + com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.shake(partialTicks, true)) * scale;
		if (dy != 0 || dx != 0) this.setRotation(this.yRot + dy, this.xRot + dx);
	}
}

