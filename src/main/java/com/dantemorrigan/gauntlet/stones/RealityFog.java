package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One Nether mist event can be active in the world. Nearby players share its spawn roll and pursue one stone.
 * The first player to hit the stone in melee or with an arrow gets it.
 */
public final class RealityFog {
	private static final double GROUP_RANGE = 24, WISP_RANGE = 64;
	private record Event(RealityWisp wisp, long end, Set<UUID> viewers) {}

	private static Event event;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RealityFog::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> event = null);
	}

	public static boolean active() { return event != null; }
	public static RealityWisp wisp() { return event == null ? null : event.wisp(); }

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (event != null) {
			if (event.wisp().isRemoved() || now >= event.end()) {
				end(server, false);
			} else if (now % 5 == 0 && event.wisp().level() instanceof ServerLevel level) {
				ServerPlayer anchor = nearestPlayer(event.wisp());
				List<ServerPlayer> group = groups(level.players()).stream()
						.filter(players -> players.contains(anchor)).findFirst().orElse(List.of());
				syncViewers(server, group);
			}
			return;
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (now % 1200 != 0 || !cfg.realityFogEnabled || StoneRegistry.found(server, Stone.REALITY)) return;
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) return;
		List<List<ServerPlayer>> groups = groups(nether.players());
		// Shuffle clusters so that a group later in the player list has the same chance to be selected.
		for (int i = groups.size() - 1; i > 0; i--) java.util.Collections.swap(groups, i, nether.getRandom().nextInt(i + 1));
		for (List<ServerPlayer> group : groups) {
			if (nether.getRandom().nextFloat() < cfg.realityFogChancePerMinute && start(group)) return;
		}
	}

	static List<List<ServerPlayer>> groups(List<ServerPlayer> players) {
		List<ServerPlayer> remaining = new ArrayList<>(players.stream().filter(RealityFog::eligible).toList());
		List<List<ServerPlayer>> groups = new ArrayList<>();
		while (!remaining.isEmpty()) {
			List<ServerPlayer> group = new ArrayList<>();
			group.add(remaining.removeFirst());
			for (int i = 0; i < group.size(); i++) {
				ServerPlayer neighbor = group.get(i);
				for (var it = remaining.iterator(); it.hasNext();) {
					ServerPlayer candidate = it.next();
					if (neighbor.distanceToSqr(candidate) <= GROUP_RANGE * GROUP_RANGE) {
						group.add(candidate);
						it.remove();
					}
				}
			}
			groups.add(group);
		}
		return groups;
	}

	private static boolean eligible(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator();
	}

	/** Operator preview starts the same event around the chosen player and their nearby group. */
	public static boolean start(ServerPlayer p) {
		for (List<ServerPlayer> group : groups(p.level().players())) {
			if (group.contains(p)) return start(group);
		}
		return false;
	}

	private static boolean start(List<ServerPlayer> group) {
		if (event != null) return false;
		ServerLevel level = group.getFirst().level();
		MinecraftServer server = level.getServer();
		if (StoneRegistry.released(server, Stone.REALITY)) return false;
		RealityWisp wisp = ModEntities.REALITY_WISP.create(level, EntitySpawnReason.EVENT);
		if (wisp == null) return false;
		Vec3 sum = Vec3.ZERO;
		for (ServerPlayer player : group) sum = sum.add(player.getEyePosition());
		Vec3 center = sum.scale(1.0 / group.size());
		ServerPlayer nearest = group.stream().min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(center))).orElseThrow();
		Vec3 origin = nearest.getEyePosition().add(nearest.getLookAngle().multiply(1, 0, 1).normalize().scale(6)).add(0, 1, 0);
		wisp.setPos(origin);
		if (!level.addFreshEntity(wisp)) return false;
		event = new Event(wisp, server.overworld().getGameTime() + GauntletConfig.get().realityFogSeconds * 20L, new HashSet<>());
		syncViewers(server, group);
		level.playSound(null, wisp.blockPosition(), SoundEvents.AMBIENT_CRIMSON_FOREST_MOOD.value(), SoundSource.AMBIENT, 2.0F, 0.6F);
		level.playSound(null, wisp.blockPosition(), com.dantemorrigan.gauntlet.ModSounds.STONE_OMINOUS, SoundSource.AMBIENT, 1.5F, 1.0F);
		return true;
	}

	static ServerPlayer nearestPlayer(RealityWisp wisp) {
		if (event == null || event.wisp() != wisp || !(wisp.level() instanceof ServerLevel level)) return null;
		return level.players().stream().filter(p -> eligible(p) && p.distanceToSqr(wisp) <= WISP_RANGE * WISP_RANGE)
				.min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(wisp))).orElse(null);
	}

	private static void syncViewers(MinecraftServer server, List<ServerPlayer> group) {
		Set<UUID> viewers = event.viewers();
		Set<UUID> current = new HashSet<>();
		Vec3 sum = Vec3.ZERO;
		for (ServerPlayer p : group) {
			current.add(p.getUUID());
			sum = sum.add(p.getEyePosition());
		}
		viewers.removeIf(id -> {
			if (current.contains(id)) return false;
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) ServerPlayNetworking.send(p, new ModNetwork.RealityFogFx(false, Vec3.ZERO));
			return true;
		});
		if (group.isEmpty()) return;
		var update = new ModNetwork.RealityFogFx(true, sum.scale(1.0 / group.size()));
		for (ServerPlayer p : group) {
			ServerPlayNetworking.send(p, update);
			if (viewers.add(p.getUUID())) ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.fog"));
		}
	}

	static boolean caught(RealityWisp wisp, ServerPlayer player, boolean shot) {
		if (event == null || event.wisp() != wisp || !eligible(player) || player.level() != wisp.level()) return false;
		ServerLevel level = player.level();
		if (!StoneRegistry.markReleased(level.getServer(), Stone.REALITY)) return false;
		level.sendParticles(ModParticles.QUEST_REALITY_ARROW_HIT, wisp.getX(), wisp.getY(), wisp.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, wisp.blockPosition(), shot ? SoundEvents.GLASS_BREAK : SoundEvents.AMETHYST_CLUSTER_BREAK,
				SoundSource.PLAYERS, 2.0F, 0.6F);
		OrbItem.giveIntoBackpack(player, new ItemStack(ModItems.REALITY_STONE));
		ModStatus.send(player, Component.translatable(shot ? "msg.infinity_gauntlet.reality.shot" : "msg.infinity_gauntlet.reality.caught"));
		end(level.getServer(), true);
		return true;
	}

	private static void end(MinecraftServer server, boolean caught) {
		Event ended = event;
		event = null;
		if (!ended.wisp().isRemoved()) ended.wisp().discard();
		for (UUID id : ended.viewers()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) continue;
			ServerPlayNetworking.send(p, new ModNetwork.RealityFogFx(false, Vec3.ZERO));
			if (caught) {
				p.level().sendParticles(ModParticles.QUEST_REALITY_FOG_DISPEL, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
			} else {
				ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.escaped"));
			}
		}
	}

	private RealityFog() {}
}
