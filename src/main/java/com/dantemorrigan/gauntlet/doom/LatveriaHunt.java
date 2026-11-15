package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The hunt, played like a stealth game. Every intruder has a detection meter: a searchlight's beam fills it fast, a
 * Doombot that sees them fills it by distance, light and stance, and noise (running, jumping, fighting, the gauntlet's
 * powers) draws the robots to where it came from. When a beam fills it the alarm sounds and a squad of at least three
 * Interceptors launches from the castle with smoke and fire, flies to where the intruder was last seen and searches:
 * they sweep the streets, land and stomp through the lanes, stop townsfolk and ask where the intruder went (who saw
 * them, points the way), and shine their eyes through the doors of houses. A robot that sees the intruder long enough
 * calls the others in and they attack; lose them and the search goes on from where they were last seen. Two minutes
 * without a trace and the squad gives up and flies home.
 */
public final class LatveriaHunt {
	public static final String TAG = "latveria_hunter";
	public static final int SQUAD = 4, LOSE_SIGHT = 200;
	/** Two minutes without a trace and they go home (tests shorten it). */
	public static int giveUp = 2400;
	/** Over the roofs and the great statue's hood. */
	private static final double CRUISE = 140;
	/** What a client is told about the hunt: 0 calm, 1 searching, 2 chasing, 3 squad on its way. */
	public static final int CALM = 0, SEARCHING = 1, CHASING = 2, INBOUND = 3;

	private enum Phase { INBOUND, SEARCH, CHASE, OUTBOUND }

	private enum Kind { SWEEP, ASK, SCAN, LISTEN, WAIT }

	private static final class Task {
		Kind kind = Kind.WAIT;
		Vec3 to;
		Direction facing;
		UUID villager;
		int ticks, length;
		boolean walk;
	}

	private static final class Hunt {
		Phase phase = Phase.INBOUND;
		UUID quarry;
		Vec3 lkp;
		long lastSeen, phaseAt, sightAt;
		final List<UUID> bots = new ArrayList<>();
		final Map<UUID, Task> tasks = new HashMap<>();
		final Set<UUID> asked = new HashSet<>();
		final Set<Long> scanned = new HashSet<>();
	}

	private static Hunt hunt;
	private static final Map<UUID, Float> METER = new HashMap<>();
	/** When each bot last sensed the intruder (game time), for the "?" over its head. */
	private static final Map<UUID, Long> SENSED = new HashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();

	private LatveriaHunt() {}

	public static void clear() {
		hunt = null;
		METER.clear();
		SENSED.clear();
		LAST_POS.clear();
		WAS_ON_GROUND.clear();
	}

	/** Debug: the phase, the last known place and the squad's size. */
	public static String debug() {
		return (hunt == null ? "none" : hunt.phase + " lkp=" + hunt.lkp + " bots=" + hunt.bots.size()) + " drives=" + drives;
	}

	public static float meter(ServerPlayer p) { return METER.getOrDefault(p.getUUID(), 0f); }

	private static void raise(ServerPlayer p, float by) {
		METER.put(p.getUUID(), Mth.clamp(meter(p) + by, 0, 1));
	}

	/** The intruder is in a searchlight's beam: true once the detection is full and the alarm should sound. */
	static boolean lit(ServerLevel level, ServerPlayer p) {
		raise(p, 0.3f);
		if (hunt != null && hunt.quarry.equals(p.getUUID())) seen(level, p);
		return meter(p) >= 1 && (hunt == null || hunt.phase == Phase.OUTBOUND);
	}

	/** True while the robots know exactly where this intruder is and fight them; otherwise they will not lock on. */
	public static boolean engaged(net.minecraft.world.entity.player.Player p) {
		return hunt != null && hunt.phase == Phase.CHASE && p.getUUID().equals(hunt.quarry);
	}

	public static boolean hunter(Doombot bot) { return bot.entityTags().contains(TAG); }

	/** Struck by the intruder: there is no hiding from the one who was hit. */
	static void attacked(ServerLevel level, ServerPlayer p) {
		if (hunt == null || !p.getUUID().equals(hunt.quarry) || hunt.phase == Phase.OUTBOUND) return;
		METER.put(p.getUUID(), 1f);
		seen(level, p);
		if (hunt.phase != Phase.CHASE) setPhase(level, Phase.CHASE);
		hunt.sightAt = level.getGameTime();
	}

	// ------------------------------------------------------------------ the launch

	/** The alarm: a squad of Interceptors takes off from the castle (or comes in out of the night) for the intruder. */
	public static void launch(ServerLevel level, ServerPlayer quarry) {
		long now = level.getGameTime();
		if (hunt != null && hunt.phase != Phase.OUTBOUND) {
			hunt.quarry = quarry.getUUID();
			seen(level, quarry);
			return;
		}
		if (hunt != null) for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.discard();
		hunt = new Hunt();
		hunt.quarry = quarry.getUUID();
		hunt.lkp = quarry.position();
		hunt.lastSeen = hunt.phaseAt = now;
		// Launch from the castle's galleries when the intruder is near enough for them to be in the world; otherwise
		// the squad is already on its way, coming in fast from the castle's side, high over the roofs.
		Vec3 castle = new Vec3(0.5, LatveriaPlan.FLOOR + 72, -259.5);
		Vec3 from = castle;
		if (!level.isPositionEntityTicking(BlockPos.containing(castle)) || castle.distanceTo(quarry.position()) > 140) {
			Vec3 back = castle.subtract(quarry.position()).multiply(1, 0, 1).normalize();
			// As far toward the castle as the world is awake, so they are seen coming in out of the dark.
			double out = 90;
			from = quarry.position().add(back.scale(out));
			while (out > 20 && !level.isPositionEntityTicking(BlockPos.containing(from))) {
				out -= 10;
				from = quarry.position().add(back.scale(out));
			}
			from = new Vec3(from.x, Math.max(CRUISE, ground(level, from) + 25), from.z);
		}
		for (int i = 0; i < SQUAD; i++) {
			Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
			if (bot == null) continue;
			bot.setup(Doombot.INTERCEPTOR, -1);
			bot.addTag(TAG);
			bot.addTag(Searchlights.ALARM_TAG);
			Vec3 at = from.add((i - 1.5) * 3.2, (i % 2) * 2.0, Math.abs(i - 1.5) * 2.5);
			bot.snapTo(at.x, at.y, at.z, 0, 0);
			if (!level.addFreshEntity(bot)) continue;
			hunt.bots.add(bot.getUUID());
			bot.huntFly(at.add(0, 6, 0), 0.6);
			DoomVfx.on(level, DoomVfx.JET, bot, 1, 60, DoomVfx.GOLD);
			DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at, 2.2);
		}
		DoomFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3f, 0.6f);
		DoomFx.sound(level, from, SoundEvents.WITHER_SHOOT, 2f, 0.5f);
		for (ServerPlayer p : level.players()) {
			DoomFx.tell(p, "hunt.launch");
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 0.5f);
		}
		DoomFx.near(level, from, 60, DoomFx.SHAKE, 0, 0.7f, 18);
	}

	private static double ground(ServerLevel level, Vec3 at) {
		BlockPos p = BlockPos.containing(at);
		if (!level.hasChunkAt(p)) return LatveriaPlan.surface(p.getX(), p.getZ());
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
	}

	/** Someone saw the intruder just now: the last known place moves to them. */
	private static void seen(ServerLevel level, ServerPlayer p) {
		if (hunt == null) return;
		hunt.lkp = p.position();
		hunt.lastSeen = level.getGameTime();
	}

	// ------------------------------------------------------------------ every tick

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		// The meters cool when nobody is watching.
		for (ServerPlayer p : level.players()) {
			float m = meter(p);
			if (m > 0 && now % 2 == 0) METER.put(p.getUUID(), Math.max(0, m - (hunt != null && hunt.phase == Phase.CHASE ? 0.002f : 0.006f)));
		}
		if (hunt != null) {
			hunt.bots.removeIf(id -> !(level.getEntity(id) instanceof Doombot b) || !b.isAlive());
			if (hunt.bots.isEmpty() && hunt.phase != Phase.INBOUND || now - hunt.phaseAt > 200 && hunt.bots.isEmpty()) {
				hunt = null;
			}
		}
		if (hunt != null) {
			ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(hunt.quarry);
			boolean gone = quarry == null || !quarry.isAlive() || quarry.level() != level || quarry.isCreative() || quarry.isSpectator();
			if (gone && hunt.phase != Phase.OUTBOUND) setPhase(level, Phase.OUTBOUND);
			if (!gone && now % 2 == 0) senses(level, quarry, now);
			switch (hunt.phase) {
				case INBOUND -> {
					boolean arrived = true;
					for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b && b.position().distanceTo(hunt.lkp) > 22) arrived = false;
					if (arrived || now - hunt.phaseAt > 600) setPhase(level, Phase.SEARCH);
				}
				case SEARCH -> {
					if (now - hunt.lastSeen > giveUp) setPhase(level, Phase.OUTBOUND);
				}
				case CHASE -> {
					if (now - hunt.sightAt > LOSE_SIGHT) {
						setPhase(level, Phase.SEARCH);
						if (quarry != null) METER.put(quarry.getUUID(), 0.6f);
					}
				}
				case OUTBOUND -> {
					for (UUID id : List.copyOf(hunt.bots)) {
						if (!(level.getEntity(id) instanceof Doombot b)) continue;
						boolean far = true;
						for (ServerPlayer p : level.players()) if (p.distanceTo(b) < 110) far = false;
						if (far || now - hunt.phaseAt > 500) {
							DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, b.position(), 1.5);
							b.discard();
						}
					}
				}
			}
		}
		// What each player is told: their meter and the state of the hunt.
		if (now % 4 == 0) {
			for (ServerPlayer p : level.players()) {
				int state = CALM, left = 0;
				if (hunt != null && p.getUUID().equals(hunt.quarry)) {
					state = switch (hunt.phase) {
						case INBOUND -> INBOUND;
						case SEARCH -> SEARCHING;
						case CHASE -> CHASING;
						default -> CALM;
					};
					if (hunt.phase == Phase.SEARCH) left = (int) Math.max(0, (giveUp - (now - hunt.lastSeen)) / 20);
				}
				DoomFx.send(p, DoomFx.STEALTH, state, Vec3.ZERO, meter(p), left);
			}
		}
		// Awareness over the robots' heads: "?" while one senses something, "!" while they fight.
		if (hunt != null && now % 5 == 0) {
			for (UUID id : hunt.bots) {
				if (!(level.getEntity(id) instanceof Doombot b)) continue;
				int a = hunt.phase == Phase.CHASE ? 2 : now - SENSED.getOrDefault(id, -1000L) < 60 ? 1 : 0;
				b.setAwareness(a);
			}
		}
	}

	private static void setPhase(ServerLevel level, Phase phase) {
		long now = level.getGameTime();
		hunt.phase = phase;
		hunt.phaseAt = now;
		hunt.tasks.clear();
		ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(hunt.quarry);
		switch (phase) {
			case SEARCH -> {
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.disengage();
				if (quarry != null) DoomFx.tell(quarry, "hunt.search");
			}
			case CHASE -> {
				hunt.sightAt = now;
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b && quarry != null) b.engage(quarry);
				if (quarry != null) {
					DoomFx.tell(quarry, "hunt.spotted");
					level.playSound(null, quarry.getX(), quarry.getY(), quarry.getZ(), SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 1.1f);
				}
			}
			case OUTBOUND -> {
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { b.disengage(); b.setAwareness(0); }
				if (quarry != null) DoomFx.tell(quarry, "hunt.lost");
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) DoomFx.sound(level, b.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.7f);
			}
			default -> {}
		}
	}

	// ------------------------------------------------------------------ eyes and ears

	private static void senses(ServerLevel level, ServerPlayer p, long now) {
		Vec3 eye = p.getEyePosition();
		// Noise: how far the intruder can be heard right now.
		Vec3 last = LAST_POS.getOrDefault(p.getUUID(), p.position());
		double moved = p.position().subtract(last).horizontalDistance();
		boolean wasGround = WAS_ON_GROUND.getOrDefault(p.getUUID(), true);
		LAST_POS.put(p.getUUID(), p.position());
		WAS_ON_GROUND.put(p.getUUID(), p.onGround());
		double noise = 0;
		if (p.onGround() && moved > 0.05) noise = p.isSprinting() ? 16 : p.isShiftKeyDown() ? 0 : 8;
		if (!wasGround && p.onGround()) noise = Math.max(noise, p.isShiftKeyDown() ? 4 : 11);
		if (p.swinging) noise = Math.max(noise, 14);
		if (!com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p).isEmpty()
				&& !com.dantemorrigan.gauntlet.GauntletState.inUse(com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p)).isEmpty()) noise = Math.max(noise, 28);
		int light = level.getMaxLocalRawBrightness(p.blockPosition());
		for (UUID id : hunt.bots) {
			if (!(level.getEntity(id) instanceof Doombot b)) continue;
			double d = b.distanceTo(p);
			// Sight: in front of it, in range, nothing in the way. Darkness, crouching and stillness hide.
			if (d < 34) {
				Vec3 look = Vec3.directionFromRotation(Mth.clamp(b.getXRot(), -30, 30), b.getYHeadRot());
				Vec3 to = eye.subtract(b.getEyePosition()).normalize();
				boolean inCone = d < 4 || look.dot(to) > Math.cos(Math.toRadians(62));
				if (inCone) {
					var hit = level.clip(new ClipContext(b.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
					if (hit.getType() == HitResult.Type.MISS) {
						float rate = (float) (0.14 * (1 - d / 34));
						rate *= p.isShiftKeyDown() ? 0.45f : 1;
						rate *= 0.35f + 0.65f * light / 15f;
						rate *= moved > 0.05 ? 1.25f : 0.8f;
						if (d < 6) rate = Math.max(rate, 0.12f);
						raise(p, rate);
						SENSED.put(id, now);
						if (hunt.phase == Phase.CHASE) hunt.sightAt = now;
						seen(level, p);
						if (hunt.phase == Phase.SEARCH) investigate(b, p.position(), true);
					}
				}
			}
			// Hearing: they turn toward a sound and come to look.
			if (noise > 0 && d < noise && hunt.phase == Phase.SEARCH) {
				raise(p, 0.03f);
				SENSED.put(id, now);
				investigate(b, p.position().add((level.getRandom().nextDouble() - 0.5) * 6, 0, (level.getRandom().nextDouble() - 0.5) * 6), false);
			}
		}
		if (meter(p) >= 1 && hunt.phase != Phase.CHASE && hunt.phase != Phase.OUTBOUND) {
			seen(level, p);
			setPhase(level, Phase.CHASE);
		}
	}

	private static void investigate(Doombot b, Vec3 at, boolean sighted) {
		Task t = hunt.tasks.computeIfAbsent(b.getUUID(), k -> new Task());
		if (t.kind == Kind.LISTEN && t.to != null && t.to.distanceTo(at) < 4) return;
		t.kind = Kind.LISTEN;
		t.to = at;
		t.ticks = 0;
		t.length = sighted ? 80 : 120;
		t.walk = false;
	}

	// ------------------------------------------------------------------ driving the robots

	/** Called by a hunter with nothing to fight: moves it for the hunt. False when the hunt has no use for it. */
	static int drives;

	static boolean drive(ServerLevel level, Doombot b) {
		drives++;
		if (hunt == null || !hunt.bots.contains(b.getUUID())) {
			// A hunter left behind by a hunt that ended: it flies away.
			b.huntFly(b.position().add(0, 2, -3), 1.0);
			if (b.tickCount % 200 == 0) b.discard();
			return true;
		}
		int index = hunt.bots.indexOf(b.getUUID());
		switch (hunt.phase) {
			case INBOUND -> {
				// A loose V high over the roofs toward the last known place, then down.
				Vec3 slot = hunt.lkp.add((index - 1.5) * 4, 0, Math.abs(index - 1.5) * 3);
				double alt = Math.max(ground(level, slot) + 14, slot.y + 10);
				double far = b.position().multiply(1, 0, 1).distanceTo(slot.multiply(1, 0, 1));
				// Cruise high over everything, then drop onto the place.
				b.huntFly(new Vec3(slot.x, far > 25 ? Math.max(CRUISE, b.getY()) : alt, slot.z), far > 25 ? 1.35 : 0.7);
			}
			case OUTBOUND -> {
				Vec3 home = new Vec3(0.5, LatveriaPlan.FLOOR + 90, -260.5);
				Vec3 up = b.position().add(0, 2, 0);
				b.huntFly(b.getY() < hunt.lkp.y + 30 ? up : home, 1.4);
			}
			case SEARCH -> search(level, b, index);
			case CHASE -> {
				ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
				if (q != null) { b.engage(q); return false; }
				b.huntFly(hunt.lkp.add(0, 4, 0), 0.8);
			}
		}
		return true;
	}

	private static void search(ServerLevel level, Doombot b, int index) {
		Task t = hunt.tasks.computeIfAbsent(b.getUUID(), k -> new Task());
		if (t.kind == Kind.WAIT || t.ticks >= t.length) newTask(level, b, t, index);
		t.ticks++;
		switch (t.kind) {
			case SWEEP, LISTEN -> {
				if (move(level, b, t.to, t.walk, t.kind == Kind.LISTEN ? 0.8 : 0.55)) {
					// There: turn slowly, looking over the place.
					b.huntLook(b.getYRot() + 6);
				}
			}
			case ASK -> {
				Villager v = t.villager == null ? null : level.getEntity(t.villager) instanceof Villager vv ? vv : null;
				if (v == null || !v.isAlive()) { t.ticks = t.length; return; }
				if (b.distanceTo(v) > 3.6) { move(level, b, v.position(), true, 0.7); t.ticks = Math.min(t.ticks, 10); return; }
				b.huntStop();
				b.huntFace(v.getEyePosition());
				v.getNavigation().stop();
				v.getLookControl().setLookAt(b, 30, 30);
				if (t.ticks == 20) {
					say(level, b, "bot.ask." + (1 + level.getRandom().nextInt(3)));
					DoomFx.sound(level, b.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.6f, 0.5f);
				}
				if (t.ticks == 55) {
					ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
					boolean knows = q != null && q.level() == level && q.distanceTo(v) < 26 && v.hasLineOfSight(q);
					if (knows) {
						DoomFx.sound(level, v.position(), SoundEvents.VILLAGER_YES, 1.2f, 1f);
						say(level, b, "bot.tip");
						raise(q, 0.3f);
						seen(level, q);
						v.getLookControl().setLookAt(q, 30, 30);
						for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot o) investigate(o, q.position(), true);
					} else {
						DoomFx.sound(level, v.position(), SoundEvents.VILLAGER_NO, 1.2f, 1f);
						if (level.getRandom().nextBoolean()) say(level, b, "bot.nothing");
					}
				}
			}
			case SCAN -> {
				if (!move(level, b, t.to, true, 0.6)) { t.ticks = Math.min(t.ticks, 10); return; }
				b.huntStop();
				Vec3 eye = b.getEyePosition();
				Vec3 dir = Vec3.atLowerCornerOf(t.facing.getUnitVec3i());
				double sweep = Math.sin(t.ticks * 0.15) * 0.45;
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				Vec3 aim = dir.add(side.scale(sweep)).add(0, -0.25, 0).normalize();
				b.huntFace(eye.add(aim.scale(6)));
				if (t.ticks % 4 == 0) {
					DoomVfx.line(level, DoomVfx.TRAIL, eye.add(aim.scale(0.8)), Searchlights.march(level, eye.add(aim.scale(0.8)), eye.add(aim.scale(12))), 0.55f, 5, DoomVfx.GREEN);
				}
				if (t.ticks % 20 == 0) DoomFx.sound(level, eye, SoundEvents.BEACON_AMBIENT, 1.2f, 1.6f);
				ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
				if (q != null && q.level() == level) {
					Vec3 rel = q.getEyePosition().subtract(eye);
					double along = rel.dot(dir), lateral = rel.subtract(dir.scale(along)).horizontalDistance();
					if (along > 0 && along < 13 && lateral < 6) {
						var hit = level.clip(new ClipContext(eye, q.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
						if (hit.getType() == HitResult.Type.MISS) {
							raise(q, q.isShiftKeyDown() ? 0.06f : 0.12f);
							SENSED.put(b.getUUID(), level.getGameTime());
							seen(level, q);
						}
					}
				}
			}
			default -> {}
		}
	}

	private static void newTask(ServerLevel level, Doombot b, Task t, int index) {
		var rnd = level.getRandom();
		t.ticks = 0;
		t.villager = null;
		t.walk = rnd.nextFloat() < 0.5f;
		Vec3 c = hunt.lkp;
		// Ask a townsman who has not been asked yet.
		if (rnd.nextFloat() < 0.35f) {
			Villager best = null;
			double bd = 45 * 45;
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(c, c).inflate(45, 12, 45))) {
				if (hunt.asked.contains(v.getUUID())) continue;
				double d = v.distanceToSqr(b);
				if (d < bd) { bd = d; best = v; }
			}
			if (best != null) {
				hunt.asked.add(best.getUUID());
				t.kind = Kind.ASK;
				t.villager = best.getUUID();
				t.length = 90;
				return;
			}
		}
		// Look into a house near the last known place.
		if (rnd.nextFloat() < 0.5f) {
			BlockPos door = null;
			double bd = 40 * 40;
			for (BlockPos p : LatveriaPlan.VILLAGERS) {
				if (hunt.scanned.contains(p.asLong())) continue;
				double d = p.distSqr(BlockPos.containing(c));
				if (d < bd) { bd = d; door = p; }
			}
			if (door != null) {
				Direction facing = doorFacing(level, door);
				hunt.scanned.add(door.asLong());
				if (facing != null) {
					t.kind = Kind.SCAN;
					t.to = Vec3.atBottomCenterOf(door.above()).subtract(Vec3.atLowerCornerOf(facing.getUnitVec3i()));
					t.facing = facing;
					t.length = 110;
					return;
				}
			}
		}
		// Otherwise sweep a stretch of street round the place.
		double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble() * 24;
		Vec3 p = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
		t.kind = Kind.SWEEP;
		t.to = new Vec3(p.x, ground(level, p), p.z);
		t.length = 120 + rnd.nextInt(80);
	}

	/** Which way a house's door looks into it from the spot in front of it: the side where the doorway stands between walls. */
	private static Direction doorFacing(ServerLevel level, BlockPos spot) {
		if (!level.hasChunkAt(spot)) return null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos door = spot.above().relative(d, 2);
			if (LatveriaKit.doorway(level.getBlockState(door)) && !level.getBlockState(door.relative(d.getClockWise())).isAir()
					&& !level.getBlockState(door.relative(d.getCounterClockWise())).isAir()) return d;
		}
		return null;
	}

	/** Toward a point, on foot or low in the air; true once there. */
	private static boolean move(ServerLevel level, Doombot b, Vec3 to, boolean walk, double speed) {
		double d = b.position().multiply(1, 0, 1).distanceTo(to.multiply(1, 0, 1));
		if (d < 2.2) return true;
		if (walk && d < 40) b.huntWalk(to, 1.0);
		else b.huntFly(new Vec3(to.x, Math.max(ground(level, to) + 5, to.y + 3), to.z), speed);
		return false;
	}

	private static void say(ServerLevel level, Doombot b, String key) {
		Component line = Component.translatable("msg.infinity_gauntlet.latveria.bot.says", Component.translatable("msg.infinity_gauntlet.latveria." + key));
		for (ServerPlayer p : level.players()) if (p.distanceTo(b) < 36) p.sendSystemMessage(line);
	}
}
