package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The castle's searchlights. Three lamps (the two front towers and the gatehouse) sweep the slope before the gate, and
 * three great lamps high on the central tower reach all the way down into the city and comb its streets and roofs, while
 * the gate is shut and the generator runs. Their aim is a pure function of the game time, so client and server agree on
 * every beam without a packet. A beam that holds on someone fills their detection (see {@link LatveriaHunt}); once it is
 * full the alarm sounds: a horn, the beams turn red and hold on the intruder, and a squad of Interceptors comes for them.
 * Switching the generator off in the laboratory puts them out.
 */
public final class Searchlights {
	private static final int F = LatveriaPlan.FLOOR;
	public static final Vec3[] LAMPS = {
			new Vec3(-45.5, F + 17.5, -198.0),
			new Vec3(46.5, F + 17.5, -198.0),
			new Vec3(0.5, F + 19.5, -200.2),
			// The city lamps on the central tower's galleries.
			new Vec3(-14.0, F + 73.5, -259.0),
			new Vec3(15.0, F + 73.5, -259.0),
			new Vec3(0.5, F + 129.5, -263.6),
	};
	/** The first lamps sweep the slope; the rest are the city lamps. */
	public static final int SLOPE_LAMPS = 3;
	public static final double RANGE = 110, CITY_RANGE = 470;
	/** Radius of the lit spot on the ground. */
	public static final float SPOT = 3.6f, CITY_SPOT = 6.5f;
	public static final int ALARM_TICKS = 160, ALARM_COOLDOWN = 600;
	public static final String ALARM_TAG = "latveria_alarm";

	private static long cooldownUntil;

	private Searchlights() {}

	public static double range(int i) { return i < SLOPE_LAMPS ? RANGE : CITY_RANGE; }

	public static float spotRadius(int i) { return i < SLOPE_LAMPS ? SPOT : CITY_SPOT; }

	/** Where lamp {@code i} points at game time {@code t} (ticks, may be fractional). */
	public static Vec3 direction(int i, double t) {
		if (i >= SLOPE_LAMPS) {
			// The city lamps drive their spots along slow, crossing loops over the streets, the square and the roofs.
			int k = i - SLOPE_LAMPS;
			double x = 70 * Math.sin(t * (0.0042 + 0.0011 * k) + k * 2.1);
			double z = 38 + 84 * Math.sin(t * (0.0029 + 0.0007 * k) + k * 1.3);
			Vec3 to = new Vec3(x, LatveriaPlan.VALLEY_Y + 1, z);
			return to.subtract(LAMPS[i]).normalize();
		}
		double center = i == 0 ? 24 : i == 1 ? -24 : 0, swing = i == 2 ? 34 : 26;
		double yaw = Math.toRadians(center + swing * Math.sin(t * (0.010 + 0.0027 * i) + i * 2.1));
		double pitch = Math.toRadians(-(34 + 7 * Math.sin(t * (0.016 + 0.002 * i) + i * 1.3)));
		return new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
	}

	/**
	 * The first solid block between two points, marching in short steps; ground that is not loaded counts as open air, so a
	 * beam from the castle to the city never makes the world load the miles of land between them.
	 */
	public static Vec3 march(Level level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1e-3) return to;
		Vec3 step = d.scale(0.6 / len);
		Vec3 at = from;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (double walked = 0; walked < len; walked += 0.6, at = at.add(step)) {
			pos.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
			if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() || !level.hasChunkAt(pos)) continue;
			var state = level.getBlockState(pos);
			if (state.isAir()) continue;
			var shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty() && shape.bounds().move(pos).contains(at)) return at;
			if (!state.getFluidState().isEmpty()) return at;
		}
		return to;
	}

	/** Where the beam of lamp {@code i} lands: the first block in its way, or the end of its reach. */
	public static Vec3 spot(Level level, int i, Vec3 dir) {
		return march(level, LAMPS[i].add(dir.scale(1.5)), LAMPS[i].add(dir.scale(range(i))));
	}

	/** The lamps burn while the gate is shut, the generator runs and the castle is still Doom's. */
	public static boolean active(MinecraftServer server) {
		return !Latveria.conquered(server) && !Latveria.gateOpen(server) && !Latveria.generatorOff(server);
	}

	static void tick(ServerLevel level) {
		if (level.players().isEmpty()) return;
		long now = level.getGameTime();
		if (now % 40 == 0) {
			float on = active(level.getServer()) ? 1 : 0;
			for (ServerPlayer p : level.players()) DoomFx.send(p, DoomFx.LIGHTS, 0, Vec3.ZERO, on, 0);
		}
		if (now % 4 != 0 || !active(level.getServer())) return;
		for (int i = 0; i < LAMPS.length; i++) {
			Vec3 dir = direction(i, now);
			Vec3 spot = spot(level, i, dir);
			double reach = spot.distanceTo(LAMPS[i]);
			for (ServerPlayer p : level.players()) {
				if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
				Vec3 body = p.position().add(0, p.getBbHeight() * 0.5, 0);
				Vec3 v = body.subtract(LAMPS[i]);
				double along = v.dot(dir);
				if (along < 2 || along > reach + 2) continue;
				double radius = 0.4 + along * spotRadius(i) / Math.max(1, reach);
				if (v.subtract(dir.scale(along)).length() > radius + 0.5) continue;
				if (march(level, LAMPS[i].add(dir.scale(1.5)), body).distanceToSqr(body) > 1.0) continue;
				// Held in a beam: the detection fills fast, and the alarm sounds when it is full.
				if (LatveriaHunt.lit(level, p) && now >= cooldownUntil) alarm(level, p);
			}
		}
	}

	/** Caught: the horn, the beams on the intruder, a glow that gives them away, and a squad of Interceptors launched for them. */
	public static void alarm(ServerLevel level, ServerPlayer caught) {
		long now = level.getGameTime();
		cooldownUntil = now + ALARM_COOLDOWN;
		Latveria.markAlarmed(level.getServer());
		for (ServerPlayer p : level.players()) {
			DoomFx.send(p, DoomFx.ALARM, caught.getId(), caught.position(), 1, ALARM_TICKS);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4f, 0.8f);
			DoomFx.tell(p, "alarm");
		}
		caught.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
		LatveriaHunt.launch(level, caught);
	}

	public static void clear() { cooldownUntil = 0; }
}
