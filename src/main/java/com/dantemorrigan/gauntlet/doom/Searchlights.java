package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModEntities;
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
 * The castle's searchlights. Three lamps (the two front towers and the gatehouse) sweep the slope before the gate while
 * it is shut and the generator runs. Their aim is a pure function of the game time, so client and server agree on
 * every beam without a packet. Whoever a beam finds sets off the alarm: a horn, the beams turn red and hold on the
 * intruder, and Interceptors launch from the towers. Switching the generator off in the laboratory puts them out.
 */
public final class Searchlights {
	public static final Vec3[] LAMPS = {
			new Vec3(-45.5, LatveriaPlan.FLOOR + 17.5, -198.0),
			new Vec3(46.5, LatveriaPlan.FLOOR + 17.5, -198.0),
			new Vec3(0.5, LatveriaPlan.FLOOR + 19.5, -200.2),
	};
	public static final double RANGE = 110;
	/** Radius of the lit spot on the ground. */
	public static final float SPOT = 3.6f;
	public static final int ALARM_TICKS = 160, ALARM_COOLDOWN = 600, MAX_ALARM_BOTS = 4;
	public static final String ALARM_TAG = "latveria_alarm";

	private static long cooldownUntil;

	private Searchlights() {}

	/** Where lamp {@code i} points at game time {@code t} (ticks, may be fractional). */
	public static Vec3 direction(int i, double t) {
		double center = i == 0 ? 24 : i == 1 ? -24 : 0, swing = i == 2 ? 34 : 26;
		double yaw = Math.toRadians(center + swing * Math.sin(t * (0.010 + 0.0027 * i) + i * 2.1));
		double pitch = Math.toRadians(-(34 + 7 * Math.sin(t * (0.016 + 0.002 * i) + i * 1.3)));
		return new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
	}

	/** Where the beam of lamp {@code i} lands: the first block in its way, or the end of its reach. */
	public static Vec3 spot(Level level, int i, Vec3 dir) {
		Vec3 from = LAMPS[i].add(dir.scale(1.5)), to = LAMPS[i].add(dir.scale(RANGE));
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
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
		if (now % 4 != 0 || now < cooldownUntil || !active(level.getServer())) return;
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
				double radius = 0.4 + along * SPOT / Math.max(1, reach);
				if (v.subtract(dir.scale(along)).length() > radius + 0.5) continue;
				var sight = level.clip(new ClipContext(LAMPS[i].add(dir.scale(1.5)), body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
				if (sight.getType() != HitResult.Type.MISS) continue;
				alarm(level, p);
				return;
			}
		}
	}

	/** Caught: the horn, the beams on the intruder, a glow that gives them away, and Interceptors from the towers. */
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
		int launched = level.getEntitiesOfClass(Doombot.class, new AABB(-200, 0, -400, 200, 320, 0), b -> b.isAlive() && b.entityTags().contains(ALARM_TAG)).size();
		for (int i = 0; i < 2 && launched < MAX_ALARM_BOTS; i++, launched++) {
			Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
			if (bot == null) return;
			bot.setup(Doombot.INTERCEPTOR, -1);
			bot.addTag(ALARM_TAG);
			Vec3 at = LAMPS[i].add(0, 1.5, 0);
			bot.snapTo(at.x, at.y, at.z, 180, 0);
			level.addFreshEntity(bot);
			DoomVfx.on(level, DoomVfx.JET, bot, 1, 40, DoomVfx.GOLD);
		}
	}

	public static void clear() { cooldownUntil = 0; }
}
