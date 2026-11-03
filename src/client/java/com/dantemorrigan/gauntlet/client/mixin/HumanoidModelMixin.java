package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: the gauntlet arm of a gesturing player is raised in front of the chest and twisted (see the gesture). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void infinityGauntlet$gesture(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar)) return;
		GauntletGestureClient.Pose gesture = GauntletGestureClient.pose(avatar.id);
		if (gesture == null) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		boolean left = state.mainArm.getOpposite() == net.minecraft.world.entity.HumanoidArm.LEFT;
		ModelPart arm = left ? model.leftArm : model.rightArm;
		float side = left ? 1 : -1, lift = gesture.lift();
		// Raise the forearm forward and in front of the body, then twist it around its own (local Y) axis.
		float raise = (float) Math.toRadians(-(80f + gesture.tilt()) * lift);
		float inward = (float) Math.toRadians(side * 14f * lift);
		Quaternionf q = new Quaternionf().rotationZYX(arm.zRot * (1 - lift), inward, raise)
				.rotateY((float) Math.toRadians(side * gesture.roll()));
		Vector3f euler = q.getEulerAnglesZYX(new Vector3f());
		arm.xRot = Mth.rotLerpRad(lift, arm.xRot, euler.x);
		arm.yRot = Mth.rotLerpRad(lift, arm.yRot, euler.y);
		arm.zRot = Mth.rotLerpRad(lift, arm.zRot, euler.z);
	}
}
