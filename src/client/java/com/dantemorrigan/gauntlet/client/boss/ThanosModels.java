package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.boss.Thanos;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The Thanos models and his animation set. Pack animations: idle, walk, arrival, rift_entry, sword_swing,
 * sword_throw, stagger. Made here in the same convention (Bedrock degrees, model units): leap, slam landing,
 * gauntlet backhand, intro stance, war cry, kneeling, leaving.
 */
public final class ThanosModels {
	private static GeoModel withGauntlet, unarmed, sword, ship;
	private static Map<String, GeoAnimation> pack, shipAnims;
	private static final Map<String, GeoAnimation> CODE = new HashMap<>();

	static {
		CODE.put("leap", GeoAnimation.builder(2f, false)
				.rot("torso", 0, 0, 0, 0).rot("torso", 0.3f, 22, 0, 0).rot("torso", 0.45f, -12, 0, 0)
				.pos("root", 0, 0, 0, 0).pos("root", 0.3f, 0, -4, 0).pos("root", 0.45f, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0).rot("right_arm", 0.3f, -30, 0, -10).rot("right_arm", 0.5f, -150, 0, -8)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 0.3f, -20, 0, 10).rot("left_arm", 0.5f, -120, 0, 12)
				.rot("left_leg", 0, 0, 0, 0).rot("left_leg", 0.3f, -35, 0, 0).rot("left_leg", 0.55f, -45, 0, 0)
				.rot("left_shin", 0, 0, 0, 0).rot("left_shin", 0.3f, 60, 0, 0).rot("left_shin", 0.55f, 70, 0, 0)
				.rot("right_leg", 0, 0, 0, 0).rot("right_leg", 0.3f, -35, 0, 0).rot("right_leg", 0.55f, -10, 0, 0)
				.rot("right_shin", 0, 0, 0, 0).rot("right_shin", 0.3f, 60, 0, 0).rot("right_shin", 0.55f, 35, 0, 0)
				.build());
		CODE.put("slam", GeoAnimation.builder(0.85f, false)
				.rot("torso", 0, -12, 0, 0).rot("torso", 0.12f, 34, 0, 0).rot("torso", 0.85f, 0, 0, 0)
				.pos("root", 0, 0, 0, 0).pos("root", 0.12f, 0, -6, 0).pos("root", 0.85f, 0, 0, 0)
				.rot("right_arm", 0, -150, 0, -8).rot("right_arm", 0.12f, -56, 0, 0).rot("right_arm", 0.85f, 0, 0, 0)
				.rot("left_arm", 0, -120, 0, 12).rot("left_arm", 0.12f, -70, 0, 20).rot("left_arm", 0.85f, 0, 0, 0)
				.rot("left_leg", 0, -45, 0, 0).rot("left_leg", 0.12f, -50, 0, 0).rot("left_leg", 0.85f, 0, 0, 0)
				.rot("left_shin", 0, 70, 0, 0).rot("left_shin", 0.12f, 60, 0, 0).rot("left_shin", 0.85f, 0, 0, 0)
				.rot("right_leg", 0, -10, 0, 0).rot("right_leg", 0.12f, 20, 0, 0).rot("right_leg", 0.85f, 0, 0, 0)
				.rot("right_shin", 0, 35, 0, 0).rot("right_shin", 0.12f, 60, 0, 0).rot("right_shin", 0.85f, 0, 0, 0)
				.build());
		CODE.put("backhand", GeoAnimation.builder(0.8f, false)
				.rot("torso", 0, 0, 0, 0).rot("torso", 0.22f, 0, 28, 0).rot("torso", 0.38f, 6, -38, 0).rot("torso", 0.8f, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 0.22f, -85, 0, -55).rot("left_arm", 0.38f, -80, 0, 70).rot("left_arm", 0.8f, 0, 0, 0)
				.rot("left_forearm", 0, 0, 0, 0).rot("left_forearm", 0.22f, -40, 0, 0).rot("left_forearm", 0.38f, -5, 0, 0).rot("left_forearm", 0.8f, 0, 0, 0)
				.build());
		CODE.put("intro", GeoAnimation.builder(0.6f, false)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 0.6f, -70, 0, 18)
				.rot("left_forearm", 0, 0, 0, 0).rot("left_forearm", 0.6f, -45, 0, 0)
				.rot("right_arm", 0, 0, 0, 0).rot("right_arm", 0.6f, -12, 0, -10)
				.rot("head", 0, 0, 0, 0).rot("head", 0.6f, 6, 0, 0)
				.rot("torso", 0, 0, 0, 0).rot("torso", 0.6f, 4, 0, 0)
				.build());
		CODE.put("roar", GeoAnimation.builder(1.8f, false)
				.rot("torso", 0, 0, 0, 0).rot("torso", 0.3f, -14, 0, 0).rot("torso", 1.4f, -14, 0, 0).rot("torso", 1.8f, 0, 0, 0)
				.rot("head", 0, 0, 0, 0).rot("head", 0.3f, -22, 0, 0).rot("head", 1.4f, -22, 0, 0).rot("head", 1.8f, 0, 0, 0)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 0.3f, -20, 0, 55).rot("left_arm", 1.4f, -20, 0, 58).rot("left_arm", 1.8f, 0, 0, 0)
				.rot("right_arm", 0, 0, 0, 0).rot("right_arm", 0.3f, -20, 0, -55).rot("right_arm", 1.4f, -20, 0, -58).rot("right_arm", 1.8f, 0, 0, 0)
				.rot("left_forearm", 0, 0, 0, 0).rot("left_forearm", 0.3f, -35, 0, 0).rot("left_forearm", 1.4f, -35, 0, 0).rot("left_forearm", 1.8f, 0, 0, 0)
				.rot("right_forearm", 0, 0, 0, 0).rot("right_forearm", 0.3f, -35, 0, 0).rot("right_forearm", 1.4f, -35, 0, 0).rot("right_forearm", 1.8f, 0, 0, 0)
				.build());
		// Falls to one knee: right knee on the ground, left foot forward, gauntlet arm resting on the left knee.
		CODE.put("kneel", GeoAnimation.builder(20f, false)
				.pos("root", 0, 0, 0, 0).pos("root", 0.25f, 0, -2, 0).pos("root", 0.65f, 0, -9.6f, 0)
				.rot("right_leg", 0, 0, 0, 0).rot("right_leg", 0.65f, -8, 0, 0)
				.rot("right_shin", 0, 0, 0, 0).rot("right_shin", 0.65f, 96, 0, 0)
				.rot("left_leg", 0, 0, 0, 0).rot("left_leg", 0.65f, -82, 0, 0)
				.rot("left_shin", 0, 0, 0, 0).rot("left_shin", 0.65f, 82, 0, 0)
				.rot("torso", 0, 0, 0, 0).rot("torso", 0.25f, -8, 9, 0).rot("torso", 0.65f, 22, 0, 0).rot("torso", 4f, 26, 0, 0)
				.rot("head", 0, 0, 0, 0).rot("head", 0.65f, 12, 0, 0).rot("head", 3f, -14, 0, 0).rot("head", 5f, 24, 0, 0)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 0.65f, -42, 0, 6)
				.rot("left_forearm", 0, 0, 0, 0).rot("left_forearm", 0.65f, -38, 0, 0)
				.rot("right_arm", 0, 0, 0, 0).rot("right_arm", 0.25f, -30, 0, -20).rot("right_arm", 0.65f, -6, 0, -12)
				.build());
		CODE.put("descend", GeoAnimation.builder(1f, false)
				.rot("left_arm", 0, 0, 0, 18).rot("right_arm", 0, 0, 0, -18)
				.rot("head", 0, -8, 0, 0)
				.build());
		CODE.put("leave", GeoAnimation.builder(1f, false)
				.rot("left_arm", 0, 0, 0, 0).rot("left_arm", 1f, 0, 0, 14).rot("right_arm", 0, 0, 0, 0).rot("right_arm", 1f, 0, 0, -14)
				.rot("head", 0, 0, 0, 0).rot("head", 1f, -10, 0, 0)
				.build());
	}

	public static GeoModel body(boolean gauntlet) {
		if (withGauntlet == null) {
			withGauntlet = GeoModel.load("thanos_gauntlet.geo.json");
			unarmed = GeoModel.load("thanos_unarmed_left.geo.json");
		}
		return gauntlet ? withGauntlet : unarmed;
	}

	public static GeoModel sword() {
		if (sword == null) sword = GeoModel.load("thanos_double_sword.geo.json");
		return sword;
	}

	public static GeoModel ship() {
		if (ship == null) ship = GeoModel.load("sanctuary_ii.geo.json");
		return ship;
	}

	static GeoAnimation anim(String name) {
		if (pack == null) pack = GeoAnimation.load("titan.animation.json");
		GeoAnimation a = CODE.get(name);
		return a != null ? a : pack.get(name);
	}

	static GeoAnimation shipAnim(String name) {
		if (shipAnims == null) shipAnims = GeoAnimation.load("sanctuary_ii.animation.json");
		return shipAnims.get(name);
	}

	/** Drops cached models so a resource reload picks up edited files. */
	public static void invalidate() {
		withGauntlet = unarmed = sword = ship = null;
		pack = shipAnims = null;
	}

	static Map<String, GeoModel.BonePose> pose(int action, float t, float walkPos, float walkSpeed, float ageTicks, float deathTime, boolean swordOut) {
		Map<String, GeoModel.BonePose> poses = new HashMap<>();
		if (deathTime > 0) {
			apply(poses, "kneel", deathTime / 20f, 1);
			return poses;
		}
		apply(poses, "idle", ageTicks / 20f, 1);
		switch (action) {
			case Thanos.A_SWING -> apply(poses, "sword_swing", t, 1);
			case Thanos.A_THROW -> apply(poses, "sword_throw", t, 1);
			case Thanos.A_LEAP -> apply(poses, "leap", t, 1);
			case Thanos.A_SLAM -> apply(poses, "slam", t, 1);
			case Thanos.A_BACKHAND -> apply(poses, "backhand", t, 1);
			case Thanos.A_STAGGER -> apply(poses, "stagger", t, 1);
			case Thanos.A_DESCEND -> apply(poses, "descend", t, 1);
			case Thanos.A_RIFT -> apply(poses, "rift_entry", t, 1);
			case Thanos.A_INTRO -> apply(poses, "intro", t, 1);
			case Thanos.A_ROAR -> apply(poses, "roar", t, 1);
			case Thanos.A_KNEEL -> apply(poses, "kneel", t, 1);
			case Thanos.A_LEAVE -> apply(poses, "leave", t, 1);
			default -> {}
		}
		// Walking: legs always, arms only while they are free.
		if (walkSpeed > 0.01f && (action == Thanos.A_IDLE || action == Thanos.A_THROW && t > 0.9f)) {
			float cycle = walkPos / (Mth.TWO_PI / 0.6662f) * 0.8f;
			apply(poses, "walk", cycle, Math.min(1f, walkSpeed * 1.8f));
		}
		if (action == Thanos.A_IDLE && !swordOut) {
			// Sword held a little forward and out, ready.
			poses.computeIfAbsent("right_arm", k -> new GeoModel.BonePose()).add(new float[]{-14, 0, -6}, null);
			poses.computeIfAbsent("right_forearm", k -> new GeoModel.BonePose()).add(new float[]{-24, 0, 0}, null);
		}
		return poses;
	}

	static void headPitch(Map<String, GeoModel.BonePose> poses, float pitch) {
		if (pitch == 0) return;
		poses.computeIfAbsent("head", k -> new GeoModel.BonePose()).add(new float[]{Mth.clamp(pitch, -30, 30), 0, 0}, null);
	}

	private static void apply(Map<String, GeoModel.BonePose> poses, String name, float t, float weight) {
		GeoAnimation a = anim(name);
		if (a != null) a.apply(poses, t, weight);
	}

	private ThanosModels() {}
}
