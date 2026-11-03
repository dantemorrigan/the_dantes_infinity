package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.space.SpaceRecord;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time stone: rewind.
 * <ul>
 *   <li>Normal world: the player is played back along their own recorded path (last N seconds) and their
 *       health/hunger restored to that moment.</li>
 *   <li>In Dead Space: the destruction itself is undone — the planet reassembles and every player from the
 *       same event is returned to where they stood before it.</li>
 * </ul>
 */
public final class TimeAbility {
	private record Snapshot(ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, float health, int food, float saturation) {}

	private static final class Rewind {
		final List<Snapshot> path; // newest first
		final boolean world;
		final int duration;
		int tick;

		Rewind(List<Snapshot> path, boolean world, int duration) {
			this.path = path;
			this.world = world;
			this.duration = duration;
		}
	}

	private static final int SAMPLE_EVERY = 2;
	private static final Map<UUID, Deque<Snapshot>> HISTORY = new HashMap<>();
	private static final Map<UUID, Rewind> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TimeAbility::tick);
		// Rewinding players are outside of time: nothing can hurt them.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, s, a) -> !(e instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())));
	}

	public static boolean isRewinding(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static void request(ServerPlayer player) {
		GauntletConfig cfg = GauntletConfig.get();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty() || !GauntletState.inserted(gauntlet).contains(Stone.TIME)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.literal("Нужна перчатка с Камнем Времени в левой руке"));
			return;
		}
		if (ACTIVE.containsKey(player.getUUID()) || PlanetDestruction.isBusy(player) && !SpaceManager.inSpace(player)) return;
		long now = player.level().getServer().overworld().getGameTime();
		Long until = COOLDOWN.get(player.getUUID());
		if (until != null && now < until && !player.isCreative()) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.literal("§2Камень Времени: " + (until - now) / 20 + " с"));
			return;
		}
		COOLDOWN.put(player.getUUID(), now + cfg.timeCooldownTicks);
		GauntletState.setInUse(gauntlet, EnumSet.of(Stone.TIME));

		SpaceRecord record = player.getAttached(SpaceManager.RECORD);
		if (record != null && SpaceManager.inSpace(player)) {
			// Undo the destruction for everyone who lived through the same event.
			for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
				SpaceRecord r = p.getAttached(SpaceManager.RECORD);
				if (r != null && r.eventStartTime() == record.eventStartTime() && SpaceManager.inSpace(p) && !ACTIVE.containsKey(p.getUUID())) {
					ACTIVE.put(p.getUUID(), new Rewind(List.of(), true, cfg.timeWorldRewindTicks));
					ServerPlayNetworking.send(p, new ModNetwork.TimeRewindFx(p.getId(), true, cfg.timeWorldRewindTicks));
				}
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
			return;
		}

		// Personal rewind along recorded history.
		Deque<Snapshot> history = HISTORY.getOrDefault(player.getUUID(), new ArrayDeque<>());
		List<Snapshot> path = new ArrayList<>();
		int max = cfg.timeRewindSeconds * 20 / SAMPLE_EVERY;
		for (Snapshot s : history) {          // deque: newest first
			if (!s.dim().equals(player.level().dimension()) || path.size() >= max) break;
			path.add(s);
		}
		if (path.size() < 3) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.literal("§2Нечего отматывать"));
			return;
		}
		ACTIVE.put(player.getUUID(), new Rewind(path, false, cfg.timeRewindPlaybackTicks));
		HISTORY.remove(player.getUUID());
		ModNetwork.TimeRewindFx fx = new ModNetwork.TimeRewindFx(player.getId(), false, cfg.timeRewindPlaybackTicks);
		for (ServerPlayer p : PlayerLookup.around(player.level(), player.position(), 64)) ServerPlayNetworking.send(p, fx);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 1.6F);
	}

	public static void cancelLevel(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			HISTORY.remove(player.getUUID());
			if (ACTIVE.remove(player.getUUID()) != null) {
				clearGlow(player);
				ServerPlayNetworking.send(player, new ModNetwork.TimeRewindFx(player.getId(), false, 0));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long time = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Rewind r = ACTIVE.get(p.getUUID());
			if (r != null) {
				step(p, r);
				continue;
			}
			if (time % SAMPLE_EVERY == 0 && p.isAlive()) {
				Deque<Snapshot> h = HISTORY.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
				h.addFirst(new Snapshot(p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), p.getHealth(),
						p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel()));
				int max = GauntletConfig.get().timeRewindSeconds * 20 / SAMPLE_EVERY + 4;
				while (h.size() > max) h.removeLast();
			}
		}
		ACTIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void step(ServerPlayer p, Rewind r) {
		r.tick++;
		if (r.world) {
			if (r.tick >= r.duration) {
				ACTIVE.remove(p.getUUID());
				SpaceManager.returnFromSpace(p);
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.4F);
				clearGlow(p);
			}
			return;
		}
		// Walk the recorded path backwards, faster than it was lived.
		int index = Math.min(r.path.size() - 1, (int) ((r.tick / (float) r.duration) * r.path.size()));
		Snapshot s = r.path.get(index);
		ServerLevel level = p.level();
		p.teleport(new TeleportTransition(level, s.pos(), Vec3.ZERO, s.yRot(), s.xRot(), TeleportTransition.DO_NOTHING));
		p.fallDistance = 0;
		if (r.tick >= r.duration) {
			Snapshot oldest = r.path.get(r.path.size() - 1);
			p.setHealth(Math.max(p.getHealth(), Math.min(p.getMaxHealth(), oldest.health())));
			p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), oldest.food()));
			p.getFoodData().setSaturation(Math.max(p.getFoodData().getSaturationLevel(), oldest.saturation()));
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			ACTIVE.remove(p.getUUID());
			level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
			clearGlow(p);
		}
	}

	private static void clearGlow(ServerPlayer p) {
		ItemStack g = GauntletState.activeGauntlet(p);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
	}

	private TimeAbility() {}
}
