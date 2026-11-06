package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
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
		var mc = Minecraft.getInstance();
		if (mc.level != null && mc.level.getEntity(avatar.id) instanceof Player player) {
			boolean main = player.getMainHandItem().is(ModItems.STORMBREAKER);
			boolean off = player.getOffhandItem().is(ModItems.STORMBREAKER);
			boolean pulling = StormbreakerClient.isPulling(avatar.id);
			if (main || off || pulling) {
				HumanoidArm hand = main || pulling ? state.mainArm : state.mainArm.getOpposite();
				boolean left = hand == HumanoidArm.LEFT;
				ModelPart axeArm = left ? model.leftArm : model.rightArm;
				if (!pulling) {
					axeArm.xRot -= 14 * Mth.DEG_TO_RAD;
					axeArm.zRot += (left ? -5 : 5) * Mth.DEG_TO_RAD;
				}
				var motion = StormbreakerClient.pose(avatar.id, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				if (motion != null && (main || pulling)) {
					var rotation = motion.rotation();
					float side = left ? -1 : 1;
					axeArm.xRot += (float) rotation.x * .94f * Mth.DEG_TO_RAD;
					axeArm.yRot += (float) rotation.y * .88f * side * Mth.DEG_TO_RAD;
					axeArm.zRot += (float) rotation.z * .86f * side * Mth.DEG_TO_RAD;
				}
			}
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
