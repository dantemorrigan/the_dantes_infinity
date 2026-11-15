package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Latveria's sky: a stormy Carpathian night under a great pale moon with a green halo, heavy clouds turning at two
 * speeds, green curtains of light over the north (where the castle stands), jagged snow-capped ridges all around,
 * and lightning that flashes on its own schedule (the same for every player, derived from the world time) with
 * thunder rolling in a few seconds later. Drawn in the sky pass in place of the End sky, like the Soul world's.
 */
public final class LatveriaSky {
	private static final String DIR = "textures/environment/latveria/";
	private static final Identifier DOME = tex("dome"), CLOUDS_A = tex("clouds_a"), CLOUDS_B = tex("clouds_b"), RIDGES = tex("ridges"),
			AURORA = tex("aurora"), MOON = tex("moon"), MOON_GLOW = tex("moon_glow"), BOLT = tex("bolt");
	private static final int SEG = 32;
	private static final long SLOT = 260;

	private static Identifier tex(String name) {
		return InfinityGauntlet.id(DIR + name + ".png");
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().equals(Latveria.LEVEL);
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LatveriaSky::tick);
	}

	// ------------------------------------------------------------------ lightning

	private static long hash(long n) {
		long h = n * 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 32);
	}

	/** Slot n holds at most one strike: its tick inside the slot, or -1. */
	private static int strikeAt(long n) {
		long h = hash(n);
		return Math.floorMod(h, 100) < 58 ? 24 + (int) Math.floorMod(h >>> 8, 170) : -1;
	}

	private static float azimuth(long n) {
		return (float) (Math.floorMod(hash(n) >>> 20, 360)) * Mth.DEG_TO_RAD;
	}

	private static int thunderAt(long n) {
		return strikeAt(n) + 36 + (int) Math.floorMod(hash(n) >>> 40, 70);
	}

	/** 0..1 brightness of the current flash: two quick pulses and a fade. */
	public static float flash(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		float time = mc.level.getGameTime() + partial;
		long n = (long) Math.floor(time / SLOT);
		int at = strikeAt(n);
		if (at < 0) return 0;
		float t = time - (n * SLOT + at);
		if (t < 0 || t > 16) return 0;
		if (t < 3) return 1;
		if (t < 5) return 0.25f;
		if (t < 8) return 0.9f;
		return Mth.clamp(1 - (t - 8) / 8f, 0, 1) * 0.45f;
	}

	private static void tick(Minecraft mc) {
		if (!active() || mc.isPaused()) return;
		long time = mc.level.getGameTime();
		long n = time / SLOT;
		for (long slot = n - 1; slot <= n; slot++) {
			if (slot >= 0 && strikeAt(slot) >= 0 && slot * SLOT + thunderAt(slot) == time) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55f + (hash(slot) & 7) * 0.02f, 0.55f));
			}
		}
	}

	// ------------------------------------------------------------------ drawing

	public static void draw() {
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
		float flash = flash(partial);
		float r = 100f, yTop = 69f, yBottom = -36f, R = (float) Math.sqrt(r * r + yTop * yTop);
		PoseStack pose = new PoseStack();
		int white = 0xFFFFFFFF;

		// The dome from just under the horizon to the zenith (v: 1 at the bottom, 0 at the zenith), so no gap shows
		// above the mountains, then the cone down to the nadir.
		double eMin = Math.toRadians(-12);
		CelestialSky.draw(pose, DOME, buf -> {
			int rings = 16;
			for (int k = 0; k < rings; k++) {
				double e0 = eMin + (Math.PI / 2 - eMin) * k / rings, e1 = eMin + (Math.PI / 2 - eMin) * (k + 1) / rings;
				float v0 = 1 - (float) k / rings, v1 = 1 - (float) (k + 1) / rings;
				for (int i = 0; i < SEG; i++) {
					double a0 = Math.PI * 2 * i / SEG, a1 = Math.PI * 2 * (i + 1) / SEG;
					quad(buf, sph(R, a0, e0), sph(R, a1, e0), sph(R, a1, e1), sph(R, a0, e1), (float) i / SEG, v0, (float) (i + 1) / SEG, v1, white);
				}
			}
		});
		// Green curtains of light in the north (azimuth 180 degrees), a little alive.
		float sway = Mth.sin(t * 0.006f) * 0.04f;
		float auroraAlpha = 0.85f + 0.15f * Mth.sin(t * 0.021f);
		arc(AURORA, 112f, 6f, 58f, sway, ARGB.colorFromFloat(auroraAlpha * (1 - flash * 0.4f), 1, 1, 1));
		// The moon: a big pale disc over the north-east with a halo.
		float elev = 33 * Mth.DEG_TO_RAD, az = Mth.PI - 0.5f;
		CelestialSky.draw(pose, MOON_GLOW, buf -> disc(buf, az, elev, 52, ARGB.colorFromFloat(0.8f + 0.1f * Mth.sin(t * 0.03f), 1, 1, 1)));
		CelestialSky.draw(pose, MOON, buf -> disc(buf, az, elev, 17, white));
		// Two layers of cloud turning at different speeds.
		arc(CLOUDS_A, 110f, 3f, 34f, t * 0.00004f, ARGB.colorFromFloat(0.92f, 1, 1, 1));
		arc(CLOUDS_B, 108f, 16f, 58f, -t * 0.00009f, ARGB.colorFromFloat(0.78f, 1, 1, 1));
		// Lightning: a glow behind the clouds and the bolt itself.
		if (flash > 0.01f) {
			long n = (long) Math.floor((mc.level.getGameTime() + partial) / SLOT);
			float baz = azimuth(n);
			CelestialSky.draw(pose, MOON_GLOW, buf -> disc(buf, baz, 24 * Mth.DEG_TO_RAD, 95, ARGB.colorFromFloat(flash * 0.8f, 0.75f, 1, 0.85f)));
			arc(CLOUDS_A, 109f, 3f, 34f, t * 0.00004f, ARGB.colorFromFloat(flash * 0.55f, 1, 1, 0.9f));
			CelestialSky.draw(pose, BOLT, buf -> {
				float d = 94f, half = 9f, yb = 4f, yt = 66f;
				float sa = Mth.sin(baz), ca = Mth.cos(baz), px = ca * half, pz = -sa * half;
				float cx = sa * d, cz = ca * d;
				int c = ARGB.colorFromFloat(Math.min(1, flash * 1.2f), 1, 1, 1);
				quadRaw(buf, new float[]{cx - px, yb, cz - pz}, new float[]{cx + px, yb, cz + pz}, new float[]{cx + px, yt, cz + pz}, new float[]{cx - px, yt, cz - pz}, 0, 1, 1, 0, c);
			});
		}
		// The Carpathians, last: they cut the sky and melt into the fog below the horizon.
		band(RIDGES, r, yBottom, yTop, white);
		CelestialSky.draw(pose, RIDGES, buf -> {
			for (int i = 0; i < SEG; i++) {
				double a0 = Math.PI * 2 * i / SEG, a1 = Math.PI * 2 * (i + 1) / SEG;
				float[] b0 = {(float) Math.sin(a0) * r, yBottom, (float) Math.cos(a0) * r}, b1 = {(float) Math.sin(a1) * r, yBottom, (float) Math.cos(a1) * r};
				float[] down = {0, -R, 0};
				quad(buf, b0, b1, down, down, 0, 0.99f, 1, 0.99f, white);
			}
		});
	}

	/** A seamless 360-degree band of the sphere between two elevations (degrees), turned by {@code spin} radians. */
	private static void arc(Identifier texture, float radius, float elev0, float elev1, float spin, int color) {
		CelestialSky.draw(new PoseStack(), texture, buf -> {
			int seg = SEG * 2, rings = 6;
			for (int k = 0; k < rings; k++) {
				double e0 = Math.toRadians(elev0 + (elev1 - elev0) * k / rings), e1 = Math.toRadians(elev0 + (elev1 - elev0) * (k + 1) / rings);
				float v0 = 1 - (float) k / rings, v1 = 1 - (float) (k + 1) / rings;
				for (int i = 0; i < seg; i++) {
					double a0 = Math.PI * 2 * i / seg + spin, a1 = Math.PI * 2 * (i + 1) / seg + spin;
					quad(buf, sph(radius, a0, e0), sph(radius, a1, e0), sph(radius, a1, e1), sph(radius, a0, e1), (float) i / seg, v0, (float) (i + 1) / seg, v1, color);
				}
			}
		});
	}

	/** A seamless 360-degree cylinder between two heights (the mountains). */
	private static void band(Identifier texture, float r, float yBottom, float yTop, int color) {
		CelestialSky.draw(new PoseStack(), texture, buf -> {
			int seg = SEG * 2;
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
				float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
				float[] b0 = {(float) Math.sin(a0) * r, yBottom, (float) Math.cos(a0) * r}, b1 = {(float) Math.sin(a1) * r, yBottom, (float) Math.cos(a1) * r};
				float[] t1 = {b1[0], yTop, b1[2]}, t0 = {b0[0], yTop, b0[2]};
				quad(buf, b0, b1, t1, t0, u0, 1, u1, 0, color);
			}
		});
	}

	private static float[] sph(float R, double a, double e) {
		return new float[]{(float) (Math.sin(a) * Math.cos(e) * R), (float) (Math.sin(e) * R), (float) (Math.cos(a) * Math.cos(e) * R)};
	}

	/** A square of the sky facing the origin at azimuth {@code az}, elevation {@code el}, half-size {@code half}. */
	private static void disc(BufferBuilder buf, float az, float el, float half, int color) {
		float d = 96f;
		float cx = Mth.sin(az) * Mth.cos(el) * d, cy = Mth.sin(el) * d, cz = Mth.cos(az) * Mth.cos(el) * d;
		float rx = Mth.cos(az), rz = -Mth.sin(az); // east-west tangent
		float ux = -Mth.sin(az) * Mth.sin(el), uy = Mth.cos(el), uz = -Mth.cos(az) * Mth.sin(el);
		float[] p0 = {cx - rx * half - ux * half, cy - uy * half, cz - rz * half - uz * half};
		float[] p1 = {cx + rx * half - ux * half, cy - uy * half, cz + rz * half - uz * half};
		float[] p2 = {cx + rx * half + ux * half, cy + uy * half, cz + rz * half + uz * half};
		float[] p3 = {cx - rx * half + ux * half, cy + uy * half, cz - rz * half + uz * half};
		quadRaw(buf, p0, p1, p2, p3, 0, 1, 1, 0, color);
	}

	private static void quad(BufferBuilder buf, float[] p0, float[] p1, float[] p2, float[] p3, float u0, float v0, float u1, float v1, int color) {
		quadRaw(buf, p0, p1, p2, p3, u0, v0, u1, v1, color);
	}

	/** Both sides, so the face is seen from inside whichever way the sky pass culls. */
	private static void quadRaw(BufferBuilder buf, float[] p0, float[] p1, float[] p2, float[] p3, float u0, float v0, float u1, float v1, int color) {
		float[][] p = {p0, p1, p2, p3};
		float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		for (int i = 0; i < 4; i++) buf.addVertex(p[i][0], p[i][1], p[i][2]).setUv(uv[i][0], uv[i][1]).setColor(color);
		for (int i = 3; i >= 0; i--) buf.addVertex(p[i][0], p[i][1], p[i][2]).setUv(uv[i][0], uv[i][1]).setColor(color);
	}

	private LatveriaSky() {}
}
