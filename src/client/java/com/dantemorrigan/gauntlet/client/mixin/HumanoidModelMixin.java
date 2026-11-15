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
	/** Under a piece of Doom's armor the wearer's own skin is not drawn: the piece is Doom's model (DoomArmorLayer). */
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void infinityGauntlet$doomArmor(HumanoidRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		boolean head = state.headEquipment.is(ModItems.DOOM_HELMET), chest = state.chestEquipment.is(ModItems.DOOM_CHESTPLATE);
		boolean legs = state.legsEquipment.is(ModItems.DOOM_LEGGINGS);
		model.head.skipDraw = head;
		model.hat.skipDraw = head;
		model.body.skipDraw = chest;
		model.rightArm.skipDraw = chest;
		model.leftArm.skipDraw = chest;
		model.rightLeg.skipDraw = legs;
		model.leftLeg.skipDraw = legs;
		if (model instanceof net.minecraft.client.model.player.PlayerModel player) {
			player.jacket.skipDraw = chest;
			player.rightSleeve.skipDraw = chest;
			player.leftSleeve.skipDraw = chest;
			player.rightPants.skipDraw = legs;
			player.leftPants.skipDraw = legs;
		}
	}

	/**
	 * Under Doom's jets nobody walks: the body is carried, so the walk cycle (which would swing arms and legs with the
	 * speed of the flight) gives way to a held pose: legs straight and together, arms down at the sides a little out and
	 * back against the thrust, with only a faint sway. The gesture (applied after this) can still lift the gauntlet arm.
	 */
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void infinityGauntlet$jetPose(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar) || !com.dantemorrigan.gauntlet.client.render.JetLean.jetting(avatar.id)) return;
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float sway = Mth.sin(state.ageInTicks * 0.09f) * 0.03f;
		for (ModelPart leg : new ModelPart[]{model.rightLeg, model.leftLeg}) {
			leg.xRot = 0.1f + sway;
			leg.yRot = 0;
			leg.zRot = 0;
		}
		model.rightLeg.zRot = 0.03f;
		model.leftLeg.zRot = -0.03f;
		model.rightArm.xRot = 0.18f - sway;
		model.rightArm.yRot = 0;
		model.rightArm.zRot = 0.14f;
		model.leftArm.xRot = 0.18f + sway;
		model.leftArm.yRot = 0;
		model.leftArm.zRot = -0.14f;
	}

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
