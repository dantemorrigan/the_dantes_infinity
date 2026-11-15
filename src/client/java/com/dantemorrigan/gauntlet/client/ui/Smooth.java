package com.dantemorrigan.gauntlet.client.ui;

/**
 * Frame-rate independent easing for the wheels and the scroll strip. Every value is moved by real elapsed time,
 * so the motion looks the same at 30 and at 300 fps and never waits for the next client tick.
 */
public final class Smooth {
	private static final float MAX_DT = 0.05f;

	/** Seconds since {@code lastNanos}, clamped so a hitch does not teleport animations. */
	public static float dt(long lastNanos, long nowNanos) {
		if (lastNanos == 0) return 1f / 60f;
		return Math.max(0f, Math.min(MAX_DT, (nowNanos - lastNanos) / 1_000_000_000f));
	}

	/** Exponential approach: {@code speed} is roughly "how many times per second the gap shrinks by 63%". */
	public static float approach(float current, float target, float speed, float dt) {
		float next = current + (target - current) * (1f - (float) Math.exp(-speed * dt));
		return Math.abs(target - next) < 0.002f ? target : next;
	}

	public static double approach(double current, double target, double speed, float dt) {
		double next = current + (target - current) * (1.0 - Math.exp(-speed * dt));
		return Math.abs(target - next) < 0.002 ? target : next;
	}

	public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

	public static float smoothstep(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Ease-out with a small overshoot: a settled-in "pop". */
	public static float outBack(float t) {
		t = clamp01(t);
		float c = 1.4f, u = t - 1f;
		return 1f + (c + 1f) * u * u * u + c * u * u;
	}

	private Smooth() {}
}
