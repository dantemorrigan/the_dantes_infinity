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

/** Third person: the gauntlet arm of a gesturing player is lifted a little and slightly twisted (see the gesture). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void infinityGauntlet$gesture(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar)) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var motion = com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.pose(
                avatar.id, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (motion != null && mc.level != null
                && mc.level.getEntity(avatar.id) instanceof net.minecraft.world.entity.player.Player player
                && (player.getMainHandItem().is(com.dantemorrigan.gauntlet.ModItems.STORMBREAKER)
                || com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.isPulling(avatar.id))) {
            boolean leftHand = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT;
            ModelPart axeArm = leftHand ? model.leftArm : model.rightArm;
            var rotation = motion.rotation();
            axeArm.xRot += (float) Math.toRadians(rotation.x * .9);
            axeArm.yRot += (float) Math.toRadians(rotation.y * (leftHand ? -.7 : .7));
            axeArm.zRot += (float) Math.toRadians(rotation.z * (leftHand ? -.65 : .65));
        }
		GauntletGestureClient.Pose gesture = GauntletGestureClient.pose(avatar.id);
		if (gesture == null) return;
		boolean left = state.mainArm.getOpposite() == net.minecraft.world.entity.HumanoidArm.LEFT;
		ModelPart arm = left ? model.leftArm : model.rightArm;
		float side = left ? 1 : -1, lift = gesture.lift();
		// Lift the arm a little forward, then twist it around its own (local Y) axis.
		float raise = (float) Math.toRadians(-(30f + gesture.tilt()) * lift);
		float inward = (float) Math.toRadians(side * 6f * lift);
		Quaternionf q = new Quaternionf().rotationZYX(arm.zRot * (1 - lift), inward, raise)
				.rotateY((float) Math.toRadians(side * gesture.roll()));
		Vector3f euler = q.getEulerAnglesZYX(new Vector3f());
		arm.xRot = Mth.rotLerpRad(lift, arm.xRot, euler.x);
		arm.yRot = Mth.rotLerpRad(lift, arm.yRot, euler.y);
		arm.zRot = Mth.rotLerpRad(lift, arm.zRot, euler.z);
	}
}
