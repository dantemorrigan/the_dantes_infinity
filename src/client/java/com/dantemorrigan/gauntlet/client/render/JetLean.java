package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.client.doom.DoomClient;
import com.dantemorrigan.gauntlet.client.doom.DoomVfxClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * A wearer of Doom's armor under jets leans the whole body into the flight like a rocket does: the body's axis tips
 * toward the direction of travel, the more the faster the flight, forward, back and sideways, and eases back upright
 * when the jets stop. The lean follows the real velocity (what the entity actually covered in the last tick), so it
 * is the same for the wearer and for everyone watching, and it is smoothed in real time, whatever the frame rate.
 */
public final class JetLean {
	/** Lean forward (+) or back (-) and to the right (+) or left (-), radians; and the time of the last update. */
	private record Lean(float forward, float right, long nanos) {}

	private static final Map<Integer, Lean> LEAN = new HashMap<>();
	/** Radians of lean per block per tick of speed, at the start of the curve. */
	private static final float GAIN_FORWARD = 1.5f, GAIN_SIDE = 1.2f;
	private static final float MAX_FORWARD = 72 * Mth.DEG_TO_RAD, MAX_BACK = 28 * Mth.DEG_TO_RAD, MAX_SIDE = 34 * Mth.DEG_TO_RAD;
	/** Seconds in which the lean closes most of the distance to where it should be. */
	private static final float EASE = 0.14f;

	private JetLean() {}

	public static void clear() { LEAN.clear(); }

	/** True while this entity flies under Doom's jets (the local player from its own state, others from the server's word). */
	public static boolean jetting(int id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return false;
		Entity e = mc.level.getEntity(id);
		return e != null && jetting(e);
	}

	private static boolean jetting(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		return e == mc.player ? DoomClient.jetActive() : DoomVfxClient.jetting(e.getId());
	}

	/** Called after the vanilla rotations, with the pose at the wearer's feet. */
	public static void apply(AvatarRenderState state, PoseStack pose, float bodyRot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity e = mc.level.getEntity(state.id);
		if (e == null || state.isFallFlying || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) return;
		Lean had = LEAN.get(state.id);
		boolean on = jetting(e);
		if (!on && had == null) return;
		long now = System.nanoTime();
		float wantForward = 0, wantRight = 0;
		if (on) {
			// What the body really covered in the last tick, in the body's own frame.
			double vx = e.getX() - e.xo, vz = e.getZ() - e.zo;
			float yaw = bodyRot * Mth.DEG_TO_RAD;
			double forward = vx * -Mth.sin(yaw) + vz * Mth.cos(yaw);
			double right = vx * -Mth.cos(yaw) + vz * -Mth.sin(yaw);
			wantForward = (float) Mth.clamp(Math.atan(GAIN_FORWARD * forward), -MAX_BACK, MAX_FORWARD);
			wantRight = (float) Mth.clamp(Math.atan(GAIN_SIDE * right), -MAX_SIDE, MAX_SIDE);
		}
		float dt = had == null ? 0 : Math.min(0.1f, (now - had.nanos) / 1.0e9f);
		float k = 1 - (float) Math.exp(-dt / EASE);
		float f = had == null ? wantForward * 0.2f : had.forward + (wantForward - had.forward) * k;
		float r = had == null ? wantRight * 0.2f : had.right + (wantRight - had.right) * k;
		if (!on && Math.abs(f) < 0.01f && Math.abs(r) < 0.01f) {
			LEAN.remove(state.id);
			return;
		}
		LEAN.put(state.id, new Lean(f, r, now));
		// Tip about the middle of the body, not the feet: the head swings out, the feet trail.
		pose.translate(0, 0.9, 0);
		pose.mulPose(Axis.ZP.rotation(-r));
		pose.mulPose(Axis.XP.rotation(-f));
		pose.translate(0, -0.9, 0);
	}
}
