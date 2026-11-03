package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Approximate world position of the gauntlet fist (off hand) for first and third person. */
public final class FistPosition {
	public static Vec3 of(Entity entity, float partial) {
		Minecraft mc = Minecraft.getInstance();
		boolean leftArm = !(entity instanceof Player p) || p.getMainArm() == HumanoidArm.RIGHT; // off hand side
		float side = leftArm ? 1f : -1f;
		Vec3 base = entity.getPosition(partial);
		if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) {
			// In front of the camera, lower-left, where the first-person arm is drawn.
			Vec3 eye = entity.getEyePosition(partial);
			Vec3 look = entity.getViewVector(partial);
			float yaw = entity.getViewYRot(partial) * Mth.DEG_TO_RAD;
			Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(side);
			return eye.add(look.scale(0.7)).add(left.scale(0.5)).add(0, -0.5, 0);
		}
		float bodyYaw = (entity instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : entity.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 left = new Vec3(Mth.cos(bodyYaw), 0, Mth.sin(bodyYaw)).scale(side * 0.38);
		Vec3 fwd = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw)).scale(0.12);
		return base.add(left).add(fwd).add(0, 0.72, 0);
	}

	/** Position while the entity is charging a gauntlet, or null to end the aura. */
	public static Vec3 ifCharging(Entity entity) {
		return entity instanceof LivingEntity le && GauntletItem.chargeTicks(le) > 0 && le.isAlive() ? of(entity, 1f) : null;
	}

	private FistPosition() {}
}
