package com.dantemorrigan.gauntlet.client.celestial;



/** Small bridge for mixins (keeps mixin classes free of logic). */
public final class CelestialClientAccess {
	public static float daylight(float partial) {
		return CelestialClient.daylightPublic(partial);
	}

	public static float[] sky(boolean day) {
		return CelestialClient.presetPublic(day ? "day" : "night", "sky_rgb", day ? new float[]{0.56f, 0.16f, 0.1f} : new float[]{0.045f, 0.006f, 0.014f});
	}

	public static float[] fog(boolean day) {
		return CelestialClient.presetPublic(day ? "day" : "night", "fog_rgb", day ? new float[]{0.5f, 0.2f, 0.15f} : new float[]{0.13f, 0.018f, 0.025f});
	}

	public static float stars() {
		return CelestialClient.presetPublic("night", "stars_alpha", new float[]{0.55f})[0];
	}

	private CelestialClientAccess() {}
}
