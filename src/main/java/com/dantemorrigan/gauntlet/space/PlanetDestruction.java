package com.dantemorrigan.gauntlet.space;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the Planet Destruction ultimate. Owns the event timeline, the participant list and the transfer.
 * The world itself is never modified: all destruction is client-side staging; the server only moves players.
 *
 * <p>Player states: IDLE → CHARGING (holding use) → STRIKE → DESTRUCTION → TRANSFER → SPACE.
 */
public final class PlanetDestruction {
	public enum Phase { IDLE, CHARGING, STRIKE, DESTRUCTION, TRANSFER, SPACE }

	/** Ticks of the strike phase before destruction starts (timeline: ground cracks at 4). */
	static final int STRIKE_TICKS = 4;

	static final class Event {
		final int id;
		final ServerLevel level;
		final Vec3 origin;
		final UUID activator;
		final Set<UUID> participants = new LinkedHashSet<>();
		/** The dimension each participant was in when it began (leaving it before the transfer drops them). */
		final Map<UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dims = new HashMap<>();
		final long startTime;
		final List<SpaceRecord.Fragment> fragments;
		int age;
		boolean transferred;

		Event(int id, ServerLevel level, Vec3 origin, UUID activator, long startTime, List<SpaceRecord.Fragment> fragments) {
			this.id = id;
			this.level = level;
			this.origin = origin;
			this.activator = activator;
			this.startTime = startTime;
			this.fragments = fragments;
		}
	}

	private static final Map<Integer, Event> EVENTS = new HashMap<>();
	private static final Map<UUID, Integer> PLAYER_EVENT = new HashMap<>();
	private static int nextId = 1;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PlanetDestruction::tick);
		// Participants are untouchable while the cutscene plays (until the transfer).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && PLAYER_EVENT.containsKey(p.getUUID())));
	}

	public static Phase phaseOf(ServerPlayer player) {
		Integer id = PLAYER_EVENT.get(player.getUUID());
		if (id != null) {
			Event e = EVENTS.get(id);
			if (e != null) return e.age < STRIKE_TICKS ? Phase.STRIKE : e.age < GauntletConfig.get().transferTick ? Phase.DESTRUCTION : Phase.TRANSFER;
		}
		if (SpaceManager.inSpace(player) && player.hasAttached(SpaceManager.RECORD)) return Phase.SPACE;
		if (player.isUsingItem() && player.getUseItem().is(com.dantemorrigan.gauntlet.ModItems.GAUNTLET)) return Phase.CHARGING;
		return Phase.IDLE;
	}

	private static boolean inVanillaDimension(ServerPlayer p) {
		var d = p.level().dimension();
		// Players in the End are never touched (the ability does not work there at all).
		return d.equals(net.minecraft.world.level.Level.OVERWORLD) || d.equals(net.minecraft.world.level.Level.NETHER);
	}

	public static boolean isBusy(ServerPlayer player) {
		return PLAYER_EVENT.containsKey(player.getUUID()) || player.hasAttached(SpaceManager.RECORD);
	}

	/** Start the event. Validation (stone, mode, charge, cooldown) is done by the caller. */
	public static boolean start(ServerPlayer activator, Vec3 impact) {
		if (!GauntletConfig.get().allowPlanetDestruction || !EVENTS.isEmpty()
				|| activator.hasAttached(com.dantemorrigan.gauntlet.snap.InfinitySnap.RECORD)) return false;
		// Never during Thanos's visit, and not in the End (the server decides, whatever the client asked).
		if (com.dantemorrigan.gauntlet.boss.ThanosEncounter.running()) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(activator, Component.translatable("ability.infinity_gauntlet.reason.thanos"));
			return false;
		}
		if (activator.level().dimension().equals(net.minecraft.world.level.Level.END)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(activator, Component.translatable("ability.infinity_gauntlet.reason.planet_end"));
			return false;
		}
		if (isBusy(activator)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(activator, Component.translatable("msg.infinity_gauntlet.event.in_progress"));
			return false;
		}
		ServerLevel level = activator.level();
		if (level.dimension().equals(SpaceManager.DEAD_SPACE)) return false;
		GauntletConfig cfg = GauntletConfig.get();
		List<SpaceRecord.Fragment> fragments = FragmentCapture.capture(level, BlockPos.containing(impact));
		MinecraftServer server = level.getServer();
		Event event = new Event(nextId++, level, impact, activator.getUUID(), server.overworld().getGameTime(), fragments);

		// The Earth itself is destroyed: every player of the server sees it, wherever they are (Overworld or Nether; the End is left alone).
		List<ServerPlayer> candidates = cfg.planetDestructionAllPlayers ? new ArrayList<>(server.getPlayerList().getPlayers().stream().filter(PlanetDestruction::inVanillaDimension).toList())
				: cfg.transferWholeDimension ? level.players() : level.getPlayers(p -> p.distanceToSqr(impact) <= cfg.participantRadius * cfg.participantRadius);
		for (ServerPlayer p : candidates) {
			if (p == activator || !isBusy(p) && !p.hasAttached(com.dantemorrigan.gauntlet.snap.InfinitySnap.RECORD) && !p.isSpectator() && p.isAlive()) event.participants.add(p.getUUID());
		}
		event.participants.add(activator.getUUID());
		EVENTS.put(event.id, event);
		com.dantemorrigan.gauntlet.ModAdvancements.award(activator, "planet_breaker");
		// A destroyed planet cannot continue its meteor event or maintain spatial portals.
		if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
			com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.stop(server);
		Set<ServerLevel> levels = new LinkedHashSet<>();
		levels.add(level);
		for (UUID id : event.participants) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) levels.add(p.level());
		}
		for (ServerLevel l : levels) {
			com.dantemorrigan.gauntlet.power.SpaceStone.cancelLevel(l);
			com.dantemorrigan.gauntlet.power.TimeAbility.cancelLevel(l);
			for (ServerPlayer p : l.players()) com.dantemorrigan.gauntlet.power.InfinityBeam.cancel(p);
		}
		for (UUID id : event.participants) {
			PLAYER_EVENT.put(id, event.id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) continue;
			event.dims.put(id, p.level().dimension());
			// Someone in another dimension has no ground at the impact point: the scene is staged around them.
			Vec3 origin = p.level() == level ? impact : p.position();
			ServerPlayNetworking.send(p, new ModNetwork.CutsceneStart(event.id, origin, 0, cfg.transferTick));
		}
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.5F);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.6F);
		InfinityGauntlet.LOGGER.info("Planet Destruction #{} by {} at {} with {} participant(s)", event.id, activator.getName().getString(), impact, event.participants.size());
		return true;
	}

	/** Abort an event for everyone (debug / activator gone). */
	public static void cancel(int eventId, String reason) {
		Event e = EVENTS.remove(eventId);
		if (e == null) return;
		MinecraftServer server = e.level.getServer();
		for (UUID id : e.participants) {
			PLAYER_EVENT.remove(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) ServerPlayNetworking.send(p, new ModNetwork.CutsceneStop(e.id));
		}
		InfinityGauntlet.LOGGER.info("Planet Destruction #{} cancelled: {}", eventId, reason);
	}

	public static void cancelFor(ServerPlayer player) {
		Integer id = PLAYER_EVENT.get(player.getUUID());
		if (id != null) cancel(id, "command");
	}

	private static void tick(MinecraftServer server) {
		if (EVENTS.isEmpty()) return;
		int transferTick = GauntletConfig.get().transferTick;
		for (Iterator<Event> it = new ArrayList<>(EVENTS.values()).iterator(); it.hasNext(); ) {
			Event e = it.next();
			e.age++;
			// Drop participants that died, left, or changed dimension before the transfer.
			for (Iterator<UUID> pit = e.participants.iterator(); pit.hasNext(); ) {
				UUID id = pit.next();
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				boolean gone = p == null || !p.isAlive() || !p.level().dimension().equals(e.dims.getOrDefault(id, e.level.dimension()));
				if (gone && !e.transferred) {
					if (id.equals(e.activator)) {
						cancel(e.id, "activator left the scene");
						break;
					}
					pit.remove();
					PLAYER_EVENT.remove(id);
					if (p != null) ServerPlayNetworking.send(p, new ModNetwork.CutsceneStop(e.id));
				}
			}
			if (!EVENTS.containsKey(e.id)) continue;

			if (!e.transferred && e.age >= transferTick) {
				e.transferred = true;
				for (UUID id : e.participants) {
					ServerPlayer p = server.getPlayerList().getPlayer(id);
					if (p == null) continue;
					SpaceRecord record = new SpaceRecord(p.level().dimension(), p.position(), e.startTime, e.id * 7919, e.fragments);
					SpaceManager.sendToSpace(p, record, e.age);
				}
			}
			// The client finishes the cutscene by itself; keep the event a bit for state queries, then forget it.
			if (e.transferred && e.age > transferTick + 20) {
				EVENTS.remove(e.id);
				e.participants.forEach(PLAYER_EVENT::remove);
			}
		}
	}

	private PlanetDestruction() {}
}
