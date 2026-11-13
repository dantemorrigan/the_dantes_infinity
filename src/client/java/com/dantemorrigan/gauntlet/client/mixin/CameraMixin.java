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

	@Shadow private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(net.minecraft.world.phys.Vec3 pos);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void infinityGauntlet$shake(float partialTicks, CallbackInfo ci) {
		var shot = com.dantemorrigan.gauntlet.client.doom.GodDoomScene.shot(partialTicks);
		if (shot != null) {
			// The ascension's own camera, free of the player: they are drawn, their hand is not.
			this.detached = true;
			this.setPosition(shot[0]);
			var to = shot[1].subtract(shot[0]);
			this.setRotation((float) Math.toDegrees(Math.atan2(-to.x, to.z)), (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()))
					+ Cutscene.shakePitch(partialTicks));
			return;
		}
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

