package com.dantemorrigan.gauntlet.client;

/** Smoothly lowers the vanilla music category while a boss theme plays, then lets it swell back. */
public final class MusicDuck {
	private static final double FADE_SECONDS = 2.5;
	private static long lastPulse = Long.MIN_VALUE / 2, lastUpdate = System.nanoTime();
	private static float factor = 1f;

	/** Called every tick by a playing boss theme. */
	public static void pulse() { lastPulse = System.nanoTime(); }

	/** Multiplier for vanilla music volume (1 = untouched, 0 = silent). */
	public static float factor() {
		long now = System.nanoTime();
		double dt = Math.min(.25, (now - lastUpdate) / 1e9);
		lastUpdate = now;
		float target = now - lastPulse < 400_000_000L ? 0f : 1f;
		float step = (float) (dt / FADE_SECONDS);
		factor += Math.max(-step, Math.min(step, target - factor));
		return factor;
	}

	private MusicDuck() {}
}
