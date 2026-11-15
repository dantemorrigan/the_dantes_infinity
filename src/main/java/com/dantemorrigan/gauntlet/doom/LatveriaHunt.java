package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * powers) draws the robots to where it came from.
 *
 * <p>When a beam fills it the alarm sounds and a squad of Interceptors launches from the castle. They remember where the
 * intruder was last seen and which way they were going, split up and each search their own sector round that place
 * (widening as time goes by), stop townsfolk and ask them (a witness points the way, or to the house the intruder went
 * into), knock on doors and scan the houses through their walls, and look in at the windows. An intruder found inside a
 * house is surrounded: the robots take posts round it and order them out. Slip out unseen, or come out and fight; when
 * the time is up they break the door in. Lose them and the district becomes a hot zone: more robots patrol it on foot
 * and in the air, and the city's great lamps circle it, for three minutes before the squad goes home.
 */
public final class LatveriaHunt {
	public static final String TAG = "latveria_hunter";
	public static final int SQUAD = 4, PATROL = 2, LOSE_SIGHT = 200;
	/** Two minutes of search, three of hot zone, half a minute of siege (tests shorten them). */
	public static int giveUp = 2400, hotTime = 3600, siegeTime = 600;
	/** Over the roofs and the great statue's hood. */
	private static final double CRUISE = 140;
	/** What a client is told about the hunt. */
	public static final int CALM = 0, SEARCHING = 1, CHASING = 2, INBOUND = 3, HOTZONE = 4, SIEGE = 5;

	private enum Phase { INBOUND, SEARCH, CHASE, SIEGE, HOTZONE, OUTBOUND }

	private enum Kind { SWEEP, ASK, SCAN, PEEK, LISTEN, POST, WAIT }

	private static final class Task {
		Kind kind = Kind.WAIT;
		Vec3 to;
		House house;
		BlockPos window;
		UUID villager;
		int ticks, length, step, tries;
		boolean walk, there;
	}

	/** A house by its door: the spot in front of it, the way into it, and the door itself. */
	record House(BlockPos spot, Direction in, BlockPos door) {
		Vec3 doorCenter() { return Vec3.atBottomCenterOf(door); }

		Vec3 center() { return doorCenter().add(Vec3.atLowerCornerOf(in.getUnitVec3i()).scale(5)); }

		boolean contains(Vec3 at) {
			Vec3 rel = at.subtract(doorCenter());
			Vec3 into = Vec3.atLowerCornerOf(in.getUnitVec3i()), side = Vec3.atLowerCornerOf(in.getClockWise().getUnitVec3i());
			double along = rel.dot(into), lateral = Math.abs(rel.dot(side));
			return along > 0.3 && along < 12 && lateral < 7.5 && rel.y > -1.5 && rel.y < 13;
		}
	}

	/** What a townsman saw: where, when, and the house the intruder went into, if they saw that. */
	private record Sighting(Vec3 at, long when, House house) {}

	private static final class Hunt {
		Phase phase = Phase.INBOUND;
		UUID quarry;
		Vec3 lkp, heading = Vec3.ZERO, hot;
		long lastSeen, phaseAt, sightAt, inHouseSince;
		House siege, inHouse;
		final List<UUID> bots = new ArrayList<>();
		final Map<UUID, Task> tasks = new HashMap<>();
		final Set<UUID> asked = new HashSet<>();
		final Set<Long> scanned = new HashSet<>(), peeked = new HashSet<>();
		final Map<UUID, Sighting> witnesses = new HashMap<>();
	}

	private static Hunt hunt;
	private static long clock;
	private static final Map<UUID, Float> METER = new HashMap<>();
	/** When each bot last sensed the intruder (game time), for the "?" over its head. */
	private static final Map<UUID, Long> SENSED = new HashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>(), VELOCITY = new HashMap<>();
	private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();
	private static final Map<Long, Direction> FACING = new HashMap<>();

	private LatveriaHunt() {}

	public static void clear() {
		hunt = null;
		METER.clear();
		SENSED.clear();
		LAST_POS.clear();
		VELOCITY.clear();
		WAS_ON_GROUND.clear();
		GRACE.clear();
		Searchlights.focus(false, 0, 0, clock);
	}

	/** Debug: the phase, the last known place and the squad's size. */
	public static String debug() {
		if (hunt == null) return "none drives=" + drives;
		return hunt.phase + " lkp=" + (hunt.lkp == null ? "-" : BlockPos.containing(hunt.lkp).toShortString()) + " bots=" + hunt.bots.size()
				+ (hunt.siege != null ? " siege=" + hunt.siege.door().toShortString() : "") + (hunt.hot != null ? " hot=" + BlockPos.containing(hunt.hot).toShortString() : "")
				+ " tasks=" + hunt.tasks.values().stream().map(t -> t.kind.name()).toList() + " witnesses=" + hunt.witnesses.size() + " inHouse=" + (hunt.inHouse == null ? "-" : hunt.inHouse.door().toShortString()) + " scanned=" + hunt.scanned.size() + " drives=" + drives;
	}

	/** The phase's name, for tests. */
	public static String phase() { return hunt == null ? "NONE" : hunt.phase.name(); }

	/** Until when (game time) each newcomer is beneath notice: nobody sees, hears or lights them. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();

	/** Gives a player who has just arrived a quiet spell: no beam, robot or noise counts against them for {@code ticks}. */
	public static void grace(ServerPlayer p, int ticks) {
		GRACE.put(p.getUUID(), p.level().getServer().overworld().getGameTime() + ticks);
		METER.put(p.getUUID(), 0f);
		if (hunt != null && p.getUUID().equals(hunt.quarry)) hunt.phase = Phase.OUTBOUND;
	}

	/** True while a player is still in their quiet spell after arriving. */
	public static boolean graced(ServerPlayer p) {
		Long until = GRACE.get(p.getUUID());
		if (until == null) return false;
		if (p.level().getServer().overworld().getGameTime() >= until) { GRACE.remove(p.getUUID()); return false; }
		return true;
	}

	public static float meter(ServerPlayer p) { return METER.getOrDefault(p.getUUID(), 0f); }

	private static void raise(ServerPlayer p, float by) {
		if (graced(p)) return;
		if (hunt != null && (hunt.phase == Phase.SIEGE || hunt.phase == Phase.HOTZONE)) by *= 1.5f;
		METER.put(p.getUUID(), Mth.clamp(meter(p) + by, 0, 1));
	}

	/** The intruder is in a searchlight's beam: true once the detection is full and the alarm should sound. */
	static boolean lit(ServerLevel level, ServerPlayer p) {
		if (graced(p)) return false;
		raise(p, 0.3f);
		if (hunt != null && hunt.quarry.equals(p.getUUID())) seen(level, p);
		return meter(p) >= 1 && (hunt == null || hunt.phase == Phase.OUTBOUND || hunt.phase == Phase.HOTZONE || hunt.phase == Phase.SEARCH);
	}

	/** True while the robots know exactly where this intruder is and fight them; otherwise they will not lock on. */
	public static boolean engaged(net.minecraft.world.entity.player.Player p) {
		return hunt != null && hunt.phase == Phase.CHASE && p.getUUID().equals(hunt.quarry) && clock - hunt.sightAt < 80;
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
			// Caught in a beam while they search or patrol: they know where you are now.
			if (hunt.phase == Phase.SEARCH || hunt.phase == Phase.HOTZONE) setPhase(level, Phase.CHASE);
			return;
		}
		if (hunt != null) for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.discard();
		hunt = new Hunt();
		hunt.quarry = quarry.getUUID();
		hunt.lkp = quarry.position();
		hunt.lastSeen = hunt.phaseAt = hunt.sightAt = now;
		Vec3 from = approach(level, quarry.position());
		for (int i = 0; i < SQUAD; i++) spawn(level, from, i);
		DoomFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3f, 0.6f);
		DoomFx.sound(level, from, SoundEvents.WITHER_SHOOT, 2f, 0.5f);
		for (ServerPlayer p : level.players()) {
			DoomFx.tell(p, "hunt.launch");
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2f, 0.5f);
		}
		DoomFx.near(level, from, 60, DoomFx.SHAKE, 0, 0.7f, 18);
	}

	/**
	 * Where robots sent to a place come from: the castle's galleries when the place is near enough for them to be in the
	 * world; otherwise as far toward the castle as the world is awake, high over the roofs, so they are seen coming in.
	 */
	private static Vec3 approach(ServerLevel level, Vec3 to) {
		Vec3 castle = new Vec3(0.5, LatveriaPlan.FLOOR + 72, -259.5);
		if (level.isPositionEntityTicking(BlockPos.containing(castle)) && castle.distanceTo(to) <= 140) return castle;
		Vec3 back = castle.subtract(to).multiply(1, 0, 1).normalize();
		double out = 90;
		Vec3 from = to.add(back.scale(out));
		while (out > 20 && !level.isPositionEntityTicking(BlockPos.containing(from))) {
			out -= 10;
			from = to.add(back.scale(out));
		}
		return new Vec3(from.x, Math.max(CRUISE, ground(level, from) + 25), from.z);
	}

	private static void spawn(ServerLevel level, Vec3 from, int i) {
		Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
		if (bot == null) return;
		bot.setup(Doombot.INTERCEPTOR, -1);
		bot.addTag(TAG);
		bot.addTag(Searchlights.ALARM_TAG);
		Vec3 at = from.add((i % 4 - 1.5) * 3.2, (i % 2) * 2.0, Math.abs(i % 4 - 1.5) * 2.5 + i / 4 * 3);
		bot.snapTo(at.x, at.y, at.z, 0, 0);
		if (!level.addFreshEntity(bot)) return;
		hunt.bots.add(bot.getUUID());
		bot.huntFly(at.add(0, 6, 0), 0.6);
		DoomVfx.on(level, DoomVfx.JET, bot, 1, 60, DoomVfx.GOLD);
		DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at, 2.2);
	}

	private static double ground(ServerLevel level, Vec3 at) {
		BlockPos p = BlockPos.containing(at);
		if (!level.hasChunkAt(p)) return LatveriaPlan.surface(p.getX(), p.getZ());
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
	}

	/** Someone saw the intruder just now: the last known place moves to them, with the way they were going. */
	private static void seen(ServerLevel level, ServerPlayer p) {
		if (hunt == null) return;
		hunt.lkp = p.position();
		hunt.heading = VELOCITY.getOrDefault(p.getUUID(), Vec3.ZERO);
		hunt.lastSeen = level.getGameTime();
	}

	/** Where the intruder is likely to be now: on from the last known place the way they were going, a little each second. */
	private static Vec3 predicted(long now) {
		Vec3 h = hunt.heading.multiply(1, 0, 1);
		if (h.length() < 0.04) return hunt.lkp;
		double far = Math.min(22, (now - hunt.lastSeen) / 20.0 * 2.5);
		return hunt.lkp.add(h.normalize().scale(far));
	}

	// ------------------------------------------------------------------ houses

	private static Direction facing(ServerLevel level, BlockPos spot) {
		Direction known = FACING.get(spot.asLong());
		if (known != null) return known;
		Direction d = doorFacing(level, spot);
		if (d != null) FACING.put(spot.asLong(), d);
		return d;
	}

	private static House house(ServerLevel level, BlockPos spot) {
		Direction in = facing(level, spot);
		return in == null ? null : new House(spot, in, spot.above().relative(in, 2));
	}

	/** The house someone is inside (under its roof, between its walls), or null. */
	static House houseAt(ServerLevel level, Vec3 at) {
		BlockPos p = BlockPos.containing(at);
		if (!level.hasChunkAt(p) || level.canSeeSky(p.above())) return null;
		for (BlockPos spot : LatveriaPlan.VILLAGERS) {
			if (Math.abs(spot.getX() - p.getX()) > 16 || Math.abs(spot.getZ() - p.getZ()) > 16) continue;
			House h = house(level, spot);
			if (h != null && h.contains(at)) return h;
		}
		return null;
	}

	/** The nearest house to a place that nobody has scanned yet in this hunt. */
	private static House houseNear(ServerLevel level, Vec3 c, double range) {
		House best = null;
		double bd = range * range;
		for (BlockPos spot : LatveriaPlan.VILLAGERS) {
			if (hunt.scanned.contains(spot.asLong())) continue;
			double d = spot.distToCenterSqr(c);
			if (d >= bd) continue;
			House h = house(level, spot);
			if (h != null) { bd = d; best = h; }
		}
		return best;
	}

	/** A window in a house's front wall: a glass pane beside the door. */
	private static BlockPos window(ServerLevel level, House h) {
		Direction side = h.in().getClockWise();
		for (int s = 1; s <= 6; s++) for (int sign : new int[]{1, -1}) for (int dy = 0; dy <= 1; dy++) {
			BlockPos w = h.door().relative(side, s * sign).above(dy);
			BlockState st = level.getBlockState(w);
			if (st.getBlock() instanceof IronBarsBlock && st.getBlock() != net.minecraft.world.level.block.Blocks.IRON_BARS) return w;
		}
		return null;
	}

	// ------------------------------------------------------------------ every tick

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		clock = now;
		// The meters cool when nobody is watching.
		for (ServerPlayer p : level.players()) {
			float m = meter(p);
			if (m > 0 && now % 2 == 0) METER.put(p.getUUID(), Math.max(0, m - (hunt != null && hunt.phase == Phase.CHASE ? 0.002f : 0.006f)));
		}
		if (hunt != null) {
			hunt.bots.removeIf(id -> !(level.getEntity(id) instanceof Doombot b) || !b.isAlive());
			if (hunt.bots.isEmpty() && (hunt.phase != Phase.INBOUND || now - hunt.phaseAt > 200)) {
				hunt = null;
				Searchlights.focus(false, 0, 0, now);
			}
		}
		ServerPlayer quarry = hunt == null ? null : level.getServer().getPlayerList().getPlayer(hunt.quarry);
		if (hunt != null) {
			boolean gone = quarry == null || !quarry.isAlive() || quarry.level() != level || quarry.isCreative() || quarry.isSpectator();
			if (gone && hunt.phase != Phase.OUTBOUND) setPhase(level, Phase.OUTBOUND);
			if (!gone && now % 2 == 0) senses(level, quarry, now);
			if (!gone && now % 10 == 0) watch(level, quarry, now);
			switch (hunt.phase) {
				case INBOUND -> {
					boolean arrived = true;
					for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b && b.position().distanceTo(hunt.lkp) > 22) arrived = false;
					if (arrived || now - hunt.phaseAt > 600) setPhase(level, Phase.SEARCH);
				}
				case SEARCH -> {
					if (now - hunt.lastSeen > giveUp) setPhase(level, Phase.HOTZONE);
				}
				case CHASE -> {
					// Gone into a house where no robot can see them: surround it.
					if (hunt.inHouse != null && now - hunt.inHouseSince > 50 && now - hunt.sightAt > 30) siege(level, hunt.inHouse);
					else if (now - hunt.sightAt > LOSE_SIGHT) {
						setPhase(level, Phase.SEARCH);
						if (quarry != null) METER.put(quarry.getUUID(), 0.6f);
					}
				}
				case SIEGE -> siegeTick(level, quarry, now);
				case HOTZONE -> {
					if (now - hunt.phaseAt > hotTime) setPhase(level, Phase.OUTBOUND);
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
		// What each player is told: their meter, the state of the hunt, and where the city's lamps are turned.
		if (now % 4 == 0) {
			Vec3 focus = hunt != null && hunt.phase == Phase.HOTZONE && hunt.hot != null ? hunt.hot : Vec3.ZERO;
			for (ServerPlayer p : level.players()) {
				int state = CALM, left = 0;
				if (hunt != null && p.getUUID().equals(hunt.quarry)) {
					state = switch (hunt.phase) {
						case INBOUND -> INBOUND;
						case SEARCH -> SEARCHING;
						case CHASE -> CHASING;
						case SIEGE -> SIEGE;
						case HOTZONE -> HOTZONE;
						default -> CALM;
					};
					if (hunt.phase == Phase.SEARCH) left = (int) Math.max(0, (giveUp - (now - hunt.lastSeen)) / 20);
					if (hunt.phase == Phase.SIEGE) left = (int) Math.max(0, (siegeTime - (now - hunt.phaseAt)) / 20);
					if (hunt.phase == Phase.HOTZONE) left = (int) Math.max(0, (hotTime - (now - hunt.phaseAt)) / 20);
				}
				DoomFx.send(p, DoomFx.STEALTH, state, focus, meter(p), left);
			}
		}
		// Awareness over the robots' heads: "?" while one senses something, "!" while they fight or hold a house.
		if (hunt != null && now % 5 == 0) {
			for (UUID id : hunt.bots) {
				if (!(level.getEntity(id) instanceof Doombot b)) continue;
				int a = hunt.phase == Phase.CHASE || hunt.phase == Phase.SIEGE ? 2 : now - SENSED.getOrDefault(id, -1000L) < 60 ? 1 : 0;
				b.setAwareness(a);
			}
		}
	}

	/** Townsfolk who see the intruder remember it; and which house the intruder is in, if any. */
	private static void watch(ServerLevel level, ServerPlayer q, long now) {
		House in = houseAt(level, q.position());
		if (in != null && (hunt.inHouse == null || !hunt.inHouse.spot().equals(in.spot()))) {
			hunt.inHouseSince = now;
			// Whoever saw them a moment ago saw them go in.
			for (var e : hunt.witnesses.entrySet()) if (now - e.getValue().when() < 100) e.setValue(new Sighting(e.getValue().at(), e.getValue().when(), in));
		}
		hunt.inHouse = in;
		for (Villager v : level.getEntitiesOfClass(Villager.class, q.getBoundingBox().inflate(26, 8, 26))) {
			if (!v.hasLineOfSight(q)) continue;
			hunt.witnesses.put(v.getUUID(), new Sighting(q.position(), now, in));
			hunt.asked.remove(v.getUUID());
		}
	}

	private static void setPhase(ServerLevel level, Phase phase) {
		long now = level.getGameTime();
		Phase was = hunt.phase;
		hunt.phase = phase;
		hunt.phaseAt = now;
		hunt.tasks.clear();
		if (was == Phase.HOTZONE && phase != Phase.HOTZONE) Searchlights.focus(false, 0, 0, now);
		ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(hunt.quarry);
		switch (phase) {
			case SEARCH -> {
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.disengage();
				// They search where the intruder must have gone, not only where they last stood.
				if (was == Phase.CHASE) hunt.lkp = predicted(now);
				if (quarry != null) DoomFx.tell(quarry, "hunt.search");
			}
			case CHASE -> {
				hunt.sightAt = now;
				hunt.siege = null;
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b && quarry != null) b.engage(quarry);
				if (quarry != null) {
					DoomFx.tell(quarry, "hunt.spotted");
					level.playSound(null, quarry.getX(), quarry.getY(), quarry.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2f, 1.1f);
				}
			}
			case HOTZONE -> {
				hunt.hot = hunt.lkp;
				hunt.lastSeen = now;
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.disengage();
				// Reinforcements to patrol the district, and the city's great lamps turned on it.
				Vec3 from = approach(level, hunt.hot);
				for (int i = 0; i < PATROL; i++) spawn(level, from, SQUAD + i);
				Searchlights.focus(true, hunt.hot.x, hunt.hot.z, now);
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { say(level, b, "bot.hot"); break; }
				if (quarry != null) DoomFx.tell(quarry, "hunt.hotzone");
			}
			case OUTBOUND -> {
				hunt.siege = null;
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { b.disengage(); b.setAwareness(0); }
				if (quarry != null) DoomFx.tell(quarry, "hunt.lost");
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) DoomFx.sound(level, b.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2f, 0.7f);
			}
			default -> {}
		}
	}

	// ------------------------------------------------------------------ the siege

	/** The intruder is in this house: the robots surround it and order them out. */
	private static void siege(ServerLevel level, House h) {
		if (hunt.phase == Phase.SIEGE) return;
		setPhase(level, Phase.SIEGE);
		hunt.siege = h;
		hunt.lkp = h.center();
		hunt.lastSeen = level.getGameTime();
		hunt.scanned.add(h.spot().asLong());
		ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
		for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) b.disengage();
		for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { say(level, b, "bot.siege.1"); break; }
		DoomFx.sound(level, h.center(), SoundEvents.RAID_HORN.value(), 2f, 0.8f);
		if (q != null) DoomFx.tell(q, "hunt.siege");
	}

	/** The posts round a besieged house: before the door, behind, and on both sides. */
	private static Vec3 post(House h, int index) {
		Vec3 into = Vec3.atLowerCornerOf(h.in().getUnitVec3i()), side = Vec3.atLowerCornerOf(h.in().getClockWise().getUnitVec3i());
		Vec3 c = h.center();
		return switch (index % 6) {
			case 0 -> h.doorCenter().subtract(into.scale(4));
			case 1 -> c.add(into.scale(9));
			case 2 -> c.add(side.scale(10));
			case 3 -> c.subtract(side.scale(10));
			case 4 -> h.doorCenter().subtract(into.scale(6)).add(side.scale(4));
			default -> h.doorCenter().subtract(into.scale(6)).subtract(side.scale(4));
		};
	}

	private static void siegeTick(ServerLevel level, ServerPlayer q, long now) {
		House h = hunt.siege;
		if (h == null) { setPhase(level, Phase.SEARCH); return; }
		long t = now - hunt.phaseAt;
		Doombot speaker = null;
		for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { speaker = b; break; }
		if (speaker != null) {
			if (t == siegeTime / 2) say(level, speaker, "bot.siege.2");
			if (t == siegeTime * 5 / 6) say(level, speaker, "bot.siege.3");
		}
		if (t < siegeTime) return;
		// Time is up: the door is broken in.
		BlockState door = level.getBlockState(h.door());
		if (door.getBlock() instanceof DoorBlock) {
			level.setBlock(h.door(), door.setValue(DoorBlock.OPEN, true), 10);
			BlockState up = level.getBlockState(h.door().above());
			if (up.getBlock() instanceof DoorBlock) level.setBlock(h.door().above(), up.setValue(DoorBlock.OPEN, true), 10);
		}
		DoomFx.sound(level, h.doorCenter(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.6f);
		DoomFx.sound(level, h.doorCenter(), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.2f);
		DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, h.doorCenter().add(0, 1, 0), 2.5);
		DoomFx.near(level, h.doorCenter(), 24, DoomFx.SHAKE, 0, 0.6f, 12);
		if (speaker != null) say(level, speaker, "bot.siege.storm");
		boolean inside = q != null && q.level() == level && (h.contains(q.position()) || q.position().distanceTo(h.center()) < 7);
		if (inside) {
			seen(level, q);
			METER.put(q.getUUID(), 1f);
			setPhase(level, Phase.CHASE);
		} else {
			if (speaker != null) say(level, speaker, "bot.siege.empty");
			hunt.lkp = h.center();
			hunt.heading = Vec3.ZERO;
			hunt.lastSeen = now;
			setPhase(level, Phase.SEARCH);
		}
	}

	// ------------------------------------------------------------------ eyes and ears

	private static void senses(ServerLevel level, ServerPlayer p, long now) {
		if (graced(p)) return;
		Vec3 eye = p.getEyePosition();
		// Noise: how far the intruder can be heard right now.
		Vec3 last = LAST_POS.getOrDefault(p.getUUID(), p.position());
		Vec3 step = p.position().subtract(last);
		double moved = step.horizontalDistance();
		boolean wasGround = WAS_ON_GROUND.getOrDefault(p.getUUID(), true);
		LAST_POS.put(p.getUUID(), p.position());
		WAS_ON_GROUND.put(p.getUUID(), p.onGround());
		if (moved > 0.02) VELOCITY.put(p.getUUID(), VELOCITY.getOrDefault(p.getUUID(), Vec3.ZERO).lerp(step.scale(10), 0.3));
		double noise = 0;
		if (p.onGround() && moved > 0.05) noise = p.isSprinting() ? 16 : p.isShiftKeyDown() ? 0 : 8;
		if (!wasGround && p.onGround()) noise = Math.max(noise, p.isShiftKeyDown() ? 4 : 11);
		if (p.swinging) noise = Math.max(noise, 14);
		if (!com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p).isEmpty()
				&& !com.dantemorrigan.gauntlet.GauntletState.inUse(com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p)).isEmpty()) noise = Math.max(noise, 28);
		int light = level.getMaxLocalRawBrightness(p.blockPosition());
		boolean chase = hunt.phase == Phase.CHASE;
		for (UUID id : hunt.bots) {
			if (!(level.getEntity(id) instanceof Doombot b)) continue;
			double d = b.distanceTo(p);
			// Sight: in front of it, in range, nothing in the way. Darkness, crouching and stillness hide. In a chase they
			// track their target all round, a little further.
			double range = chase ? 44 : 34;
			if (d < range) {
				Vec3 look = Vec3.directionFromRotation(Mth.clamp(b.getXRot(), -30, 30), b.getYHeadRot());
				Vec3 to = eye.subtract(b.getEyePosition()).normalize();
				boolean inCone = chase || d < 4 || look.dot(to) > Math.cos(Math.toRadians(62));
				if (inCone) {
					var hit = level.clip(new ClipContext(b.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
					if (hit.getType() == HitResult.Type.MISS) {
						float rate = (float) (0.14 * (1 - d / range));
						rate *= p.isShiftKeyDown() ? 0.45f : 1;
						rate *= 0.35f + 0.65f * light / 15f;
						rate *= moved > 0.05 ? 1.25f : 0.8f;
						if (d < 6) rate = Math.max(rate, 0.12f);
						raise(p, rate);
						SENSED.put(id, now);
						if (chase || meter(p) > 0.5f) hunt.sightAt = now;
						seen(level, p);
						if (hunt.phase == Phase.SEARCH || hunt.phase == Phase.HOTZONE) investigate(b, p.position(), true);
					}
				}
			}
			// Hearing: they turn toward a sound and come to look.
			if (noise > 0 && d < noise && (hunt.phase == Phase.SEARCH || hunt.phase == Phase.HOTZONE)) {
				raise(p, 0.03f);
				SENSED.put(id, now);
				investigate(b, p.position().add((level.getRandom().nextDouble() - 0.5) * 6, 0, (level.getRandom().nextDouble() - 0.5) * 6), false);
			}
		}
		if (meter(p) >= 1 && hunt.phase != Phase.CHASE && hunt.phase != Phase.OUTBOUND) {
			seen(level, p);
			House in = houseAt(level, p.position());
			if (in != null && hunt.phase != Phase.SIEGE) siege(level, in);
			else if (in == null) {
				if (hunt.phase == Phase.SIEGE) for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot b) { say(level, b, "bot.siege.out"); break; }
				setPhase(level, Phase.CHASE);
			}
		}
	}

	private static void investigate(Doombot b, Vec3 at, boolean sighted) {
		Task t = hunt.tasks.computeIfAbsent(b.getUUID(), k -> new Task());
		if (t.kind == Kind.LISTEN && t.to != null && t.to.distanceTo(at) < 4) return;
		if (t.kind == Kind.SCAN && t.step > 0) return;
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
				b.huntFly(new Vec3(slot.x, far > 25 ? Math.max(CRUISE, b.getY()) : alt, slot.z), far > 25 ? 1.35 : 0.7);
			}
			case OUTBOUND -> {
				Vec3 home = new Vec3(0.5, LatveriaPlan.FLOOR + 90, -260.5);
				Vec3 up = b.position().add(0, 2, 0);
				b.huntFly(b.getY() < hunt.lkp.y + 30 ? up : home, 1.4);
			}
			case SEARCH, HOTZONE -> search(level, b, index);
			case SIEGE -> {
				House h = hunt.siege;
				if (h == null) return true;
				Vec3 post = post(h, index);
				double g = ground(level, post);
				// On the street they stand; over a neighbour's roof they hover above it.
				boolean street = g <= LatveriaPlan.VALLEY_Y + 2;
				Vec3 at = new Vec3(post.x, street ? g : g + 2, post.z);
				if (move(level, b, at, street, 0.8)) {
					b.huntStop();
					b.huntFace(h.center().add(0, 1.5, 0));
					// The eyes sweep the house they hold.
					if (b.tickCount % 6 == 0) {
						Vec3 eye = b.getEyePosition();
						Vec3 aim = h.center().add(0, 1 + Math.sin(b.tickCount * 0.1) * 1.5, 0).subtract(eye).normalize();
						DoomVfx.line(level, DoomVfx.TRAIL, eye.add(aim.scale(0.8)), Searchlights.march(level, eye.add(aim.scale(0.8)), eye.add(aim.scale(14))), 0.5f, 6, DoomVfx.GOLD);
					}
				}
			}
			case CHASE -> {
				ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
				if (q != null && engaged(q)) { b.engage(q); return false; }
				// Lost sight for a moment: on to where they must be.
				b.disengage();
				Vec3 to = predicted(level.getGameTime());
				move(level, b, to, false, 1.0);
			}
		}
		return true;
	}

	private static void search(ServerLevel level, Doombot b, int index) {
		Task t = hunt.tasks.computeIfAbsent(b.getUUID(), k -> new Task());
		if (t.kind == Kind.WAIT || t.ticks >= t.length) newTask(level, b, t, index);
		t.ticks++;
		ServerPlayer q = level.getServer().getPlayerList().getPlayer(hunt.quarry);
		if (q != null && q.level() != level) q = null;
		switch (t.kind) {
			case SWEEP, LISTEN, POST -> {
				if (move(level, b, t.to, t.walk, t.kind == Kind.LISTEN ? 0.8 : 0.55)) {
					// There: turn slowly, looking over the place.
					b.huntLook(b.getYRot() + 6);
					if (t.kind == Kind.POST && t.ticks < t.length - 30) t.ticks = Math.max(t.ticks, t.length - 30);
				}
			}
			case ASK -> ask(level, b, t, q);
			case SCAN -> scan(level, b, t, q);
			case PEEK -> peek(level, b, t, q);
			default -> {}
		}
	}

	/** Stops a townsman and asks; a witness points the way, or to the house the intruder went into. */
	private static void ask(ServerLevel level, Doombot b, Task t, ServerPlayer q) {
		Villager v = t.villager == null ? null : level.getEntity(t.villager) instanceof Villager vv ? vv : null;
		if (v == null || !v.isAlive()) { t.ticks = t.length; return; }
		if (b.distanceTo(v) > 3.6) {
			if (!move(level, b, v.position(), ++t.tries < 80, 0.7)) t.ticks = t.tries > 300 ? t.length : Math.min(t.ticks, 10);
			return;
		}
		b.huntStop();
		b.huntFace(v.getEyePosition());
		v.getNavigation().stop();
		v.getLookControl().setLookAt(b, 30, 30);
		if (t.ticks == 20) {
			say(level, b, "bot.ask." + (1 + level.getRandom().nextInt(3)));
			DoomFx.sound(level, b.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.6f, 0.5f);
		}
		if (t.ticks != 55) return;
		long now = level.getGameTime();
		boolean sees = q != null && q.distanceTo(v) < 26 && v.hasLineOfSight(q);
		Sighting s = hunt.witnesses.get(v.getUUID());
		if (sees) {
			DoomFx.sound(level, v.position(), SoundEvents.VILLAGER_YES, 1.2f, 1f);
			say(level, b, "bot.tip");
			raise(q, 0.3f);
			seen(level, q);
			v.getLookControl().setLookAt(q, 30, 30);
			for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot o) investigate(o, q.position(), true);
		} else if (s != null && now - s.when() < 2400) {
			DoomFx.sound(level, v.position(), SoundEvents.VILLAGER_YES, 1.2f, 1f);
			// A lead: the search starts over from what the witness saw.
			hunt.lastSeen = Math.max(hunt.lastSeen, now - giveUp / 3);
			if (s.house() != null) {
				say(level, b, "bot.tip.house");
				hunt.lkp = s.house().center();
				hunt.heading = Vec3.ZERO;
				hunt.scanned.remove(s.house().spot().asLong());
				startScan(t, s.house());
				// Two more come to watch the house while it is scanned.
				int sent = 0;
				for (UUID id : hunt.bots) {
					if (sent >= 2 || id.equals(b.getUUID()) || !(level.getEntity(id) instanceof Doombot o)) continue;
					Task ot = hunt.tasks.computeIfAbsent(id, k -> new Task());
					ot.kind = Kind.POST;
					ot.to = post(s.house(), 1 + sent);
					ot.ticks = 0;
					ot.length = 200;
					ot.walk = true;
					sent++;
				}
			} else {
				say(level, b, "bot.tip.seen");
				hunt.lkp = s.at();
				for (UUID id : hunt.bots) if (level.getEntity(id) instanceof Doombot o && o != b) investigate(o, s.at(), false);
			}
		} else {
			DoomFx.sound(level, v.position(), SoundEvents.VILLAGER_NO, 1.2f, 1f);
			if (level.getRandom().nextBoolean()) say(level, b, "bot.nothing");
		}
	}

	private static void startScan(Task t, House h) {
		hunt.scanned.add(h.spot().asLong());
		t.kind = Kind.SCAN;
		t.house = h;
		t.to = h.doorCenter().subtract(Vec3.atLowerCornerOf(h.in().getUnitVec3i()).scale(1.6));
		t.ticks = 0;
		t.step = 0;
		t.tries = 0;
		t.length = 150;
		t.there = false;
	}

	/** At a house's door: knocks, then scans the house through its walls; whoever hides inside shows up on the scan. */
	private static void scan(ServerLevel level, Doombot b, Task t, ServerPlayer q) {
		House h = t.house;
		if (!t.there) {
			if (!reach(level, b, t, t.to)) return;
			t.there = true;
			t.ticks = 0;
		}
		b.huntStop();
		b.huntFace(h.doorCenter().add(0, 1.2, 0));
		t.step = t.ticks;
		if (t.ticks == 4 || t.ticks == 13 || t.ticks == 22) {
			DoomFx.sound(level, h.doorCenter().add(0, 1, 0), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.9f, 0.55f);
			DoomFx.near(level, h.doorCenter(), 8, DoomFx.SHAKE, 0, 0.12f, 3);
		}
		if (t.ticks == 6) say(level, b, "bot.knock." + (1 + level.getRandom().nextInt(2)));
		if (t.ticks < 36) return;
		if (t.ticks == 36) {
			say(level, b, "bot.xray");
			DoomFx.sound(level, b.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.4f);
		}
		// The scan: green beams fan through the walls into the house.
		Vec3 eye = b.getEyePosition();
		Vec3 into = Vec3.atLowerCornerOf(h.in().getUnitVec3i()), side = new Vec3(-into.z, 0, into.x);
		double sweep = Math.sin(t.ticks * 0.12) * 0.8;
		Vec3 aim = into.add(side.scale(sweep)).add(0, Math.sin(t.ticks * 0.05) * 0.25, 0).normalize();
		b.huntFace(eye.add(aim.scale(6)));
		if (t.ticks % 3 == 0) DoomVfx.line(level, DoomVfx.TRAIL, eye.add(aim.scale(0.8)), eye.add(aim.scale(13)), 0.6f, 6, DoomVfx.GREEN);
		if (t.ticks % 20 == 0) DoomFx.sound(level, eye, SoundEvents.BEACON_AMBIENT, 1.4f, 1.7f);
		if (q != null && h.contains(q.position())) {
			raise(q, q.isShiftKeyDown() ? 0.014f : 0.024f);
			SENSED.put(b.getUUID(), level.getGameTime());
			if (meter(q) >= 1) { seen(level, q); siege(level, h); }
		}
	}

	/** Looks in at a window: the eyes shine through the glass, and whoever stands in the light inside is seen. */
	private static void peek(ServerLevel level, Doombot b, Task t, ServerPlayer q) {
		House h = t.house;
		BlockPos w = t.window;
		Vec3 into = Vec3.atLowerCornerOf(h.in().getUnitVec3i());
		if (!t.there) {
			if (!reach(level, b, t, t.to)) return;
			t.there = true;
			t.ticks = 0;
		}
		b.huntStop();
		Vec3 pane = Vec3.atCenterOf(w), inside = pane.add(into.scale(0.7));
		Vec3 side = new Vec3(-into.z, 0, into.x);
		Vec3 aim = into.add(side.scale(Math.sin(t.ticks * 0.1) * 0.6)).add(0, -0.15, 0).normalize();
		b.huntFace(inside.add(aim.scale(5)));
		if (t.ticks % 4 == 0) DoomVfx.line(level, DoomVfx.TRAIL, inside, Searchlights.march(level, inside, inside.add(aim.scale(10))), 0.5f, 5, DoomVfx.GREEN);
		if (q == null) return;
		Vec3 rel = q.getEyePosition().subtract(inside);
		double d = rel.length();
		if (d < 14 && rel.normalize().dot(into) > Math.cos(Math.toRadians(55))) {
			var hit = level.clip(new ClipContext(inside, q.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
			if (hit.getType() == HitResult.Type.MISS) {
				raise(q, q.isShiftKeyDown() ? 0.05f : 0.09f);
				SENSED.put(b.getUUID(), level.getGameTime());
				seen(level, q);
				if (meter(q) >= 1 && h.contains(q.position())) siege(level, h);
			}
		}
	}

	private static void newTask(ServerLevel level, Doombot b, Task t, int index) {
		var rnd = level.getRandom();
		long now = level.getGameTime();
		t.ticks = 0;
		t.step = 0;
		t.tries = 0;
		t.there = false;
		t.villager = null;
		t.house = null;
		t.walk = rnd.nextFloat() < 0.6f;
		boolean hot = hunt.phase == Phase.HOTZONE;
		Vec3 c = hot ? hunt.hot : predicted(now);
		int n = Math.max(1, hunt.bots.size());
		// Each robot keeps to its own sector round the place, and the circle widens as time goes by.
		double since = (now - hunt.lastSeen) / 20.0;
		double angle = index * Math.PI * 2 / n + now * 0.0008;
		double reach = hot ? 14 + 8 * (index % 3) : 6 + Math.min(34, since * 0.5);
		Vec3 sector = c.add(Math.cos(angle) * reach * 0.6, 0, Math.sin(angle) * reach * 0.6);
		// In the hot zone the robots walk their rounds: a ring of posts round the place, one after another.
		if (hot && rnd.nextFloat() < 0.55f) {
			double a = angle + (now / 160) * 0.7;
			Vec3 p = c.add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
			t.kind = Kind.POST;
			t.to = new Vec3(p.x, ground(level, p), p.z);
			t.walk = index % 2 == 0;
			t.length = 160;
			return;
		}
		// A witness first: someone who saw the intruder and has not been asked.
		Villager best = null;
		double bd = 50 * 50;
		boolean witness = false;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(sector, sector).inflate(50, 12, 50))) {
			if (hunt.asked.contains(v.getUUID())) continue;
			boolean w = hunt.witnesses.containsKey(v.getUUID());
			double d = v.distanceToSqr(b) * (w ? 0.25 : 1);
			if (d < bd) { bd = d; best = v; witness = w; }
		}
		if (best != null && (witness ? rnd.nextFloat() < 0.8f : rnd.nextFloat() < 0.3f)) {
			hunt.asked.add(best.getUUID());
			t.kind = Kind.ASK;
			t.villager = best.getUUID();
			t.length = 90;
			return;
		}
		// Knock on a door in its sector and scan the house.
		if (rnd.nextFloat() < 0.5f) {
			House h = houseNear(level, sector, 30);
			if (h != null) { startScan(t, h); return; }
		}
		// Look in at a window.
		if (rnd.nextFloat() < 0.5f) {
			House h = houseNear(level, sector, 30);
			if (h != null && !hunt.peeked.contains(h.spot().asLong())) {
				BlockPos w = window(level, h);
				hunt.peeked.add(h.spot().asLong());
				if (w != null) {
					t.kind = Kind.PEEK;
					t.house = h;
					t.window = w;
					Vec3 out = Vec3.atBottomCenterOf(w.below(w.getY() - h.door().getY())).subtract(Vec3.atLowerCornerOf(h.in().getUnitVec3i()).scale(1.6));
					t.to = out;
					t.length = 100;
					return;
				}
			}
		}
		// Otherwise sweep a stretch of street in its sector.
		double a = angle + (rnd.nextDouble() - 0.5) * 1.6, r = reach * (0.5 + rnd.nextDouble() * 0.6);
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

	/**
	 * Gets a robot to the place a task needs it, on foot first and, when the way is blocked, in the air; true once there.
	 * A place it cannot reach in fifteen seconds ends the task.
	 */
	private static boolean reach(ServerLevel level, Doombot b, Task t, Vec3 to) {
		if (move(level, b, to, ++t.tries < 80, 0.6)) return true;
		t.ticks = t.tries > 300 ? t.length : Math.min(t.ticks, 5);
		return false;
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
		for (ServerPlayer p : level.players()) if (p.distanceTo(b) < 40) p.sendSystemMessage(line);
	}
}
