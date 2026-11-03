package com.dantemorrigan.gauntlet.celestial;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Celestial Apocalypse (Power stone): aim the gauntlet at the sun or the moon, break it, and rain its pieces
 * on the world for {@link GauntletConfig#meteorRainSeconds} REAL seconds. Craters, fire, explosions and damage
 * are server-side; the sky, cracks and trails are client visuals driven by {@link ModNetwork.CelestialSync}.
 *
 * <p>Timeline (ticks from aim start, cf. event_timeline.json): lock 60, cracks 60/90/120/150, pieces detach 180,
 * pulled down + red sky 200, first impact 240, then the real-time rain.
 */
public final class CelestialApocalypse {
	public static final int T_FRACTURE = 60, T_DETACH = 180, T_PULL = 200, T_FIRST_IMPACT = 240;
	private static final int GRACE_TICKS = 200;

	/** Persistent event (overworld attachment). rainDeadlineEpochMs = 0 until the first impact. */
	public record Event(String body, Vec3 center, long startGameTime, long rainDeadlineEpochMs, boolean ending) {
		public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("body").forGetter(Event::body),
				Vec3.CODEC.fieldOf("center").forGetter(Event::center),
				Codec.LONG.fieldOf("start").forGetter(Event::startGameTime),
				Codec.LONG.fieldOf("rain_deadline_epoch_ms").forGetter(Event::rainDeadlineEpochMs),
				Codec.BOOL.fieldOf("ending").forGetter(Event::ending)
		).apply(i, Event::new));
	}

	/** Destroyed bodies are stored separately from the temporary red sky. */
	public static final AttachmentType<List<String>> DESTROYED = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("destroyed_celestials"));
	public static final AttachmentType<Event> EVENT = AttachmentRegistry.<Event>builder()
			.persistent(Event.CODEC).buildAndRegister(InfinityGauntlet.id("celestial_event"));

	private record Meteor(int id, Vec3 to, long arriveTick, int size, boolean solar) {}

	private static final List<Meteor> METEORS = new ArrayList<>();
	private static final Map<UUID, Integer> AIM = new HashMap<>();
	private static long rainDeadlineNanos;
	private static long endingSince = -1;
	private static float spawnAccumulator;
	private static int nextMeteorId = 1;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CelestialApocalypse::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			METEORS.clear();
			Event e = server.overworld().getAttached(EVENT);
			if (e != null && e.rainDeadlineEpochMs() > 0) {
				long remaining = Math.max(0, e.rainDeadlineEpochMs() - System.currentTimeMillis());
				rainDeadlineNanos = System.nanoTime() + remaining * 1_000_000L; // monotonic from here on
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.getPlayer(), sync(server)));
	}

	// ------------------------------------------------------------------ body geometry

	/** Direction to the sun or moon, same transform as the vanilla sky (rotY(-90) * rotX(angle) * +Y). */
	public static Vec3 bodyDirection(Level level, String body) {
		float deg = level.environmentAttributes().getDimensionValue(body.equals("sun") ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE);
		float a = deg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(a), Mth.cos(a), 0);
	}

	public static boolean isDestroyed(MinecraftServer server, String body) {
		List<String> d = server.overworld().getAttached(DESTROYED);
		return d != null && d.contains(body);
	}

	/** Which body the player is looking at (above the horizon, not destroyed), or null. */
	public static String aimedBody(ServerPlayer player) {
		if (!player.level().dimension().equals(Level.OVERWORLD)) return null;
		Vec3 look = player.getLookAngle();
		double cos = Math.cos(GauntletConfig.get().celestialAimDegrees * Mth.DEG_TO_RAD);
		for (String body : new String[]{"sun", "moon"}) {
			Vec3 dir = bodyDirection(player.level(), body);
			if (dir.y > 0.03 && look.dot(dir) > cos && !isDestroyed(player.level().getServer(), body)) return body;
		}
		return null;
	}

	// ------------------------------------------------------------------ activation

	/** Called every use tick while the gauntlet charges in Celestial mode. Returns true when it locked on. */
	public static boolean aimTick(ServerPlayer player, ItemStack gauntlet, int useTicks) {
		MinecraftServer server = player.level().getServer();
		if (server.overworld().getAttached(EVENT) != null) {
			if (useTicks % 20 == 1) player.sendOverlayMessage(Component.literal("§5Небо уже рушится"));
			return false;
		}
		String body = aimedBody(player);
		int lock = Math.round(GauntletConfig.get().celestialAimSeconds * 20);
		if (body == null) {
			AIM.remove(player.getUUID());
			if (useTicks % 10 == 1) player.sendOverlayMessage(Component.literal("§5Наведи перчатку на солнце или луну"));
			return false;
		}
		int t = AIM.merge(player.getUUID(), 1, Integer::sum);
		if (t % 5 == 0) {
			player.sendOverlayMessage(Component.literal("§5Захват: " + (body.equals("sun") ? "СОЛНЦЕ" : "ЛУНА") + " " + Math.min(100, t * 100 / lock) + "%"));
			player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F + t / (float) lock);
		}
		if (t >= lock) {
			AIM.remove(player.getUUID());
			start(player, body);
			return true;
		}
		return false;
	}

	public static void resetAim(ServerPlayer player) {
		AIM.remove(player.getUUID());
	}

	/** Start the event (also used by the debug command). The event clock starts at the end of the lock. */
	public static boolean start(ServerPlayer player, String body) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		if (overworld.getAttached(EVENT) != null) return false;
		BlockPos ground = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
		Event e = new Event(body, Vec3.atBottomCenterOf(ground), overworld.getGameTime() - T_FRACTURE, 0, false);
		overworld.setAttached(EVENT, e);
		endingSince = -1;
		METEORS.clear();
		ItemStack g = GauntletState.activeGauntlet(player);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.of(Stone.POWER));
		overworld.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3.0F, 0.6F);
		broadcast(server);
		InfinityGauntlet.LOGGER.info("Celestial Apocalypse: {} by {}", body, player.getName().getString());
		return true;
	}

	public static void stop(MinecraftServer server) {
		server.overworld().removeAttached(EVENT);
		METEORS.clear();
		endingSince = -1;
		broadcast(server);
	}

	public static void restoreBodies(MinecraftServer server) {
		server.overworld().removeAttached(DESTROYED);
		broadcast(server);
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Event e = level.getAttached(EVENT);
		if (e == null) return;
		long t = level.getGameTime() - e.startGameTime();
		GauntletConfig cfg = GauntletConfig.get();

		if (t == T_FRACTURE || t == T_FRACTURE + 30 || t == T_FRACTURE + 60 || t == T_FRACTURE + 90) {
			level.playSound(null, BlockPos.containing(e.center()), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 6.0F, 0.4F);
		}
		if (t == T_DETACH) {
			List<String> destroyed = new ArrayList<>(level.getAttachedOrElse(DESTROYED, List.of()));
			if (!destroyed.contains(e.body())) destroyed.add(e.body());
			level.setAttached(DESTROYED, destroyed);
			level.playSound(null, BlockPos.containing(e.center()), SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 8.0F, 0.5F);
			broadcast(server);
		}
		if (t == T_PULL) broadcast(server);
		if (t >= T_FIRST_IMPACT - 45 && e.rainDeadlineEpochMs() == 0 && !e.ending()) {
			// First impacts: start the real-time deadline at the first ground hit.
			if (t == T_FIRST_IMPACT - 45) for (int i = 0; i < 3; i++) launch(level, e, 45);
			if (t >= T_FIRST_IMPACT) {
				long ms = cfg.meteorRainSeconds * 1000L;
				rainDeadlineNanos = System.nanoTime() + ms * 1_000_000L;
				e = new Event(e.body(), e.center(), e.startGameTime(), System.currentTimeMillis() + ms, false);
				level.setAttached(EVENT, e);
				broadcast(server);
			}
		}
		if (e.rainDeadlineEpochMs() > 0 && !e.ending()) {
			if (System.nanoTime() >= rainDeadlineNanos) {
				e = new Event(e.body(), e.center(), e.startGameTime(), e.rainDeadlineEpochMs(), true);
				level.setAttached(EVENT, e);
				endingSince = level.getGameTime();
				broadcast(server);
			} else {
				spawnAccumulator += cfg.meteorImpactsPerSecond / 20f;
				while (spawnAccumulator >= 1f) {
					spawnAccumulator -= 1f;
					if (METEORS.size() < cfg.maxActiveMeteors) launch(level, e, 0);
				}
			}
		}
		// Arrivals.
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor m = it.next();
			if (level.getGameTime() >= m.arriveTick()) {
				it.remove();
				impact(level, m);
			}
		}
		if (e.ending()) {
			if (endingSince < 0) endingSince = level.getGameTime();
			if (METEORS.isEmpty() || level.getGameTime() - endingSince > GRACE_TICKS) {
				METEORS.clear();
				level.removeAttached(EVENT);
				broadcast(server);
				return;
			}
		}
		if (level.getGameTime() % 40 == 0) broadcast(server);
	}

	private static void launch(ServerLevel level, Event e, int forcedFlight) {
		GauntletConfig cfg = GauntletConfig.get();
		RandomSource rnd = level.getRandom();
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(rnd.nextDouble()) * cfg.meteorImpactRadius;
			BlockPos column = BlockPos.containing(e.center().x + Math.cos(a) * r, e.center().y, e.center().z + Math.sin(a) * r);
			if (!level.hasChunkAt(column)) continue;
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			Vec3 to = Vec3.atBottomCenterOf(ground);
			boolean nearPlayer = false;
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceTo(to) < cfg.meteorPlayerSafeRadius) nearPlayer = true;
			}
			if (nearPlayer) continue;
			// Come in from the side of the sky where the body was.
			Vec3 bodyDir = bodyDirection(level, e.body());
			Vec3 horiz = new Vec3(bodyDir.x, 0, bodyDir.z);
			if (horiz.lengthSqr() < 1e-4) horiz = new Vec3(1, 0, 0);
			horiz = horiz.normalize();
			double height = 90 + rnd.nextDouble() * 60;
			Vec3 from = to.add(horiz.scale(40 + rnd.nextDouble() * 40)).add(rnd.nextGaussian() * 15, height, rnd.nextGaussian() * 15);
			int flight = forcedFlight > 0 ? forcedFlight : (int) Math.max(20, from.distanceTo(to) / 3.2);
			int size = rnd.nextFloat() < 0.15f ? 2 : rnd.nextFloat() < 0.5f ? 1 : 0;
			Meteor m = new Meteor(nextMeteorId++, to, level.getGameTime() + flight, size, e.body().equals("sun"));
			METEORS.add(m);
			ModNetwork.MeteorSpawn packet = new ModNetwork.MeteorSpawn(m.id(), from, to, flight, size, m.solar());
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceTo(to) < 600) ServerPlayNetworking.send(p, packet);
			}
			return;
		}
	}

	private static void impact(ServerLevel level, Meteor m) {
		GauntletConfig cfg = GauntletConfig.get();
		if (!level.hasChunkAt(BlockPos.containing(m.to()))) return;
		RandomSource rnd = level.getRandom();
		BlockState ground = level.getBlockState(BlockPos.containing(m.to()).below());
		float power = Mth.lerp((m.size() + rnd.nextFloat()) / 3f, cfg.craterPowerMin, cfg.craterPowerMax);
		level.explode(null, m.to().x, m.to().y + 0.5, m.to().z, power, true, Level.ExplosionInteraction.TNT);
		// Extra fires around the crater rim.
		int radius = Math.round(power) + 2;
		for (int i = 0; i < 6 + m.size() * 4; i++) {
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
					BlockPos.containing(m.to().x + rnd.nextGaussian() * radius, m.to().y, m.to().z + rnd.nextGaussian() * radius));
			if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, net.minecraft.core.Direction.UP)) {
				level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
			}
		}
		ModNetwork.MeteorImpact packet = new ModNetwork.MeteorImpact(m.id(), m.to(), m.size(), Block.getId(ground));
		for (ServerPlayer p : PlayerLookup.around(level, m.to(), 256)) ServerPlayNetworking.send(p, packet);
	}

	// ------------------------------------------------------------------ sync

	public static ModNetwork.CelestialSync sync(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Event e = level.getAttached(EVENT);
		boolean sun = isDestroyed(server, "sun"), moon = isDestroyed(server, "moon");
		if (e == null) return new ModNetwork.CelestialSync(0, "", Vec3.ZERO, 0, 0, sun, moon);
		int t = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - e.startGameTime());
		int phase = e.ending() ? 3 : e.rainDeadlineEpochMs() > 0 ? 2 : 1;
		int remaining = e.rainDeadlineEpochMs() > 0 ? (int) Math.max(0, (rainDeadlineNanos - System.nanoTime()) / 1_000_000L) : 0;
		return new ModNetwork.CelestialSync(phase, e.body(), e.center(), t, remaining, sun, moon);
	}

	private static void broadcast(MinecraftServer server) {
		ModNetwork.CelestialSync s = sync(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, s);
	}

	private CelestialApocalypse() {}
}
