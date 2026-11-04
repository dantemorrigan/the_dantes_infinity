package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Meteor Stream visuals. The caster sees the artillery ring follow their aim on the ground; while a stream runs,
 * everyone within sight sees its circle (a danger zone) and the sky turns red over it. The server announces streams
 * to nearby players only, and a stream also expires locally, so a lost packet can never leave a zone behind.
 */
public final class MeteorStreamClient {
	private static final class Marker {
		Vec3 center;
		float radius;
		boolean solar;
		long startTick, endTick;
	}

	/** A Power meteor shower: the sky (and the sun) take on a red cast around it. */
	private record Shower(Vec3 center, long start, long end) {}

	private static final List<Shower> SHOWERS = new ArrayList<>();
	private static final List<Marker> MARKERS = new ArrayList<>();
	private static long clientTicks;
	/** Aim marker of the local player (valid = the ground point can be used), refreshed by the server every few ticks. */
	private static Vec3 aimCenter;
	private static boolean aimValid;
	private static float aimRadius = 24;
	private static long aimUntil;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.MeteorStreamFx.TYPE, (fx, ctx) -> onFx(fx));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PowerShowerFx.TYPE, (fx, ctx) ->
				SHOWERS.add(new Shower(net.minecraft.world.phys.Vec3.atCenterOf(fx.center()), clientTicks, clientTicks + fx.ticks())));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {MARKERS.clear(); SHOWERS.clear(); aimCenter = null; aimUntil = 0;});
		ClientTickEvents.END_CLIENT_TICK.register(MeteorStreamClient::tick);
	}

	private static void onFx(ModNetwork.MeteorStreamFx fx) {
		switch (fx.kind()) {
			case 0 -> {
				aimCenter = fx.center();
				aimValid = fx.valid();
				aimRadius = fx.radius();
				aimUntil = clientTicks + 10;
			}
			case 3 -> aimUntil = 0;
			case 1 -> {
				Marker known = find(fx.center());
				if (known == null) {
					known = new Marker();
					known.center = fx.center();
					known.startTick = clientTicks;
					MARKERS.add(known);
				}
				known.radius = fx.radius();
				known.solar = fx.solar();
				known.endTick = clientTicks + fx.ticks();
			}
			case 2 -> {
				Marker known = find(fx.center());
				if (known != null) known.endTick = Math.min(known.endTick, clientTicks + 1);
			}
			default -> {}
		}
	}

	private static Marker find(Vec3 center) {
		for (Marker m : MARKERS) if (m.center.distanceToSqr(center) < 1.0) return m;
		return null;
	}

	/** 0..1: how strongly a Power meteor shower reddens the sky for a viewer at {@code pos} (ramps in, holds, fades out). */
	public static float showerTint(Vec3 pos) {
		float best = 0;
		for (Shower s : SHOWERS) {
			double d = Math.sqrt(Math.pow(pos.x - s.center.x, 2) + Math.pow(pos.z - s.center.z, 2));
			float radial = (float) Mth.clamp(1 - (d - 260) / 240.0, 0, 1);
			float in = Mth.clamp((clientTicks - s.start) / 70f, 0, 1);
			float out = Mth.clamp((s.end - clientTicks) / 160f, 0, 1);
			best = Math.max(best, radial * Math.min(in, out));
		}
		return best;
	}

	/** 0..1 red-sky strength this stream adds at a position: grows when it starts, fades as it ends, only near the circle. */
	public static float redFactor(Vec3 pos) {
		float best = 0;
		for (Marker m : MARKERS) {
			double d = Math.sqrt(Math.pow(pos.x - m.center.x, 2) + Math.pow(pos.z - m.center.z, 2));
			float radial = (float) Mth.clamp(1 - (d - 180) / 120.0, 0, 1);
			float in = Mth.clamp((clientTicks - m.startTick) / 80f, 0, 1);
			float out = Mth.clamp((m.endTick - clientTicks) / 100f, 0, 1);
			best = Math.max(best, 0.55f * radial * Math.min(in, out));
		}
		// A Power shower reddens the sky, softly: less than the Apocalypse, more than a hint.
		return Math.max(best, 0.4f * showerTint(pos));
	}

	private static void tick(Minecraft mc) {
		clientTicks++;
		if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
			MARKERS.clear();
			SHOWERS.clear();
			return;
		}
		SHOWERS.removeIf(s -> clientTicks >= s.end);
		for (Iterator<Marker> it = MARKERS.iterator(); it.hasNext(); ) {
			Marker m = it.next();
			if (clientTicks >= m.endTick) {
				it.remove();
				continue;
			}
			if (clientTicks % 6 == 0) ring(mc, m.center, m.radius * 2, false);
			if (clientTicks % 3 == 0) rim(mc, m.center, m.radius, false);
		}
		if (aimCenter != null && clientTicks < aimUntil && aimValid) {
			if (clientTicks % 5 == 0) ring(mc, aimCenter, aimRadius * 2, true);
			if (clientTicks % 2 == 0) rim(mc, aimCenter, aimRadius, true);
		}
	}

	/** The flat ring sprite; the aim ring is pushed up a little so it is never hidden in the grass. */
	private static void ring(Minecraft mc, Vec3 center, float diameter, boolean aim) {
		Vec3 p = center.add(0, aim ? 0.25 : 0.2, 0);
		ClientPower.spawn(ModParticles.METEOR_RETICLE, p, 0, diameter, 0);
	}

	/** Flames along the circle that follow the terrain, so the area stays readable on hills. */
	private static void rim(Minecraft mc, Vec3 center, float radius, boolean aim) {
		RandomSource rnd = mc.level.getRandom();
		int count = aim ? 10 : 7;
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
			double y = mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(x, center.y, z))
					? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) : center.y;
			mc.level.addParticle(aim && rnd.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, true, true, x, y + 0.2, z, 0, 0.05 + rnd.nextDouble() * 0.05, 0);
		}
	}

	private MeteorStreamClient() {}
}
