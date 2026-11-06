package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Space stone: portals into the Nether / End / Overworld (step through to travel), and teleport to where you look.
 * Portals are server objects (no blocks placed); clients draw them from {@link ModNetwork.PortalOpen}.
 */
public final class SpaceStone {
	public static final String[] DESTINATIONS = {"nether", "end", "overworld"};
	private static final List<ResourceKey<Level>> DIMS = List.of(Level.NETHER, Level.END, Level.OVERWORLD);
	public static final float PORTAL_SIZE = 4f; // world quad width (square), cf. portal_presets.json
	private static final int OPEN_TICKS = 8, CLOSE_TICKS = 8;

	private static final class Portal {
		final int id;
		final ServerLevel level;
		final Vec3 center;
		final float yaw;
		final int dest; // index in DESTINATIONS, -1 = exit portal (visual only)
		final UUID owner;
		int age;
		int life;

		Portal(int id, ServerLevel level, Vec3 center, float yaw, int dest, UUID owner, int life) {
			this.id = id;
			this.level = level;
			this.center = center;
			this.yaw = yaw;
			this.dest = dest;
			this.owner = owner;
			this.life = life;
		}

		ModNetwork.PortalOpen packet() {
			return new ModNetwork.PortalOpen(id, center, yaw, dest >= 0 ? DESTINATIONS[dest] : "exit", age, life - age);
		}
	}

	private static final List<Portal> PORTALS = new ArrayList<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static final Map<UUID, Long> BLINK_COOLDOWN = new HashMap<>();
	private static final Map<UUID, Integer> TRAVEL_GRACE = new HashMap<>();
	private static final Map<UUID, ServerLevel> VIEW_LEVELS = new HashMap<>();
	private static int nextId = 1;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpaceStone::tick);
		ServerPlayConnectionEvents.JOIN.register((h, s, server) -> syncPortals(h.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> VIEW_LEVELS.remove(h.getPlayer().getUUID()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VIEW_LEVELS.clear();
			PORTALS.clear();
			TRAVEL_GRACE.clear();
			COOLDOWN.clear();
			BLINK_COOLDOWN.clear();
			CLEAR_GLOW.clear();
		});
	}

	private static void syncPortals(ServerPlayer player) {
		VIEW_LEVELS.put(player.getUUID(), player.level());
		for (Portal portal : PORTALS) {
			if (portal.level == player.level()) ServerPlayNetworking.send(player, portal.packet());
		}
	}

	private static ItemStack gauntletWithSpace(ServerPlayer player) {
		ItemStack g = GauntletState.activeGauntlet(player);
		if (g.isEmpty() || !GauntletState.inserted(g).contains(Stone.SPACE)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.need.space"));
			return ItemStack.EMPTY;
		}
		return g;
	}

	public static int destination(ItemStack g, ServerLevel current) {
		Integer d = g.get(ModComponents.PORTAL_DESTINATION);
		int i = d == null ? 0 : Math.floorMod(d, DESTINATIONS.length);
		if (DIMS.get(i).equals(current.dimension())) i = (i + 1) % DESTINATIONS.length; // never into the same world
		return i;
	}

	public static void action(ServerPlayer player, int action) {
		ItemStack g = gauntletWithSpace(player);
		if (g.isEmpty() || SpaceManager.inSpace(player)) return;
		if(com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)) {if(action==2) blink(player,g,40);return;}
		switch (action) {
			case 0 -> togglePortal(player, g);
			case 1 -> {
				int next = (destination(g, player.level()) + 1) % DESTINATIONS.length;
				if (DIMS.get(next).equals(player.level().dimension())) next = (next + 1) % DESTINATIONS.length;
				g.set(ModComponents.PORTAL_DESTINATION, next);
				com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.portal.selected." + name(next)));
				player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f + next * 0.2f);
			}
			case 2 -> blink(player, g, GauntletConfig.get().blinkRange);
			default -> {}
		}
	}

	private static String name(int dest) {
		return switch (DESTINATIONS[dest]) {
			case "nether" -> "nether";
			case "end" -> "end";
			default -> "overworld";
		};
	}

	// ------------------------------------------------------------------ portals

	private static void togglePortal(ServerPlayer player, ItemStack g) {
		for (Portal p : PORTALS) {
			if (player.getUUID().equals(p.owner) && p.dest >= 0 && p.level == player.level()) {
				p.life = Math.min(p.life, p.age + CLOSE_TICKS); // close mine
				return;
			}
		}
		long now = player.level().getGameTime();
		if (now < COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
		COOLDOWN.put(player.getUUID(), now + GauntletConfig.get().portalCooldownTicks);
		int dest = destination(g, player.level());
		float yaw = player.getYRot();
		Vec3 forward = Vec3.directionFromRotation(0, yaw);
		Vec3 center = player.position().add(forward.scale(2.5)).add(0, PORTAL_SIZE / 2 - 0.1, 0);
		open(player.level(), center, yaw + 180, dest, player.getUUID(), GauntletConfig.get().portalLifetimeSeconds * 20);
		GauntletState.setInUse(g, EnumSet.of(Stone.SPACE));
		com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.portal.open." + name(dest)));
		player.level().playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	private static Portal open(ServerLevel level, Vec3 center, float yaw, int dest, UUID owner, int life) {
		Portal p = new Portal(nextId++, level, center, yaw, dest, owner, life);
		PORTALS.add(p);
		for (ServerPlayer sp : level.players()) ServerPlayNetworking.send(sp, p.packet());
		return p;
	}

	/** Cancel portals anchored to the destroyed world's geometry. */
	public static void cancelLevel(ServerLevel level) {
		for (Portal portal : new ArrayList<>(PORTALS)) if (portal.level == level
				|| portal.dest >= 0 && DIMS.get(portal.dest).equals(level.dimension())) {
			PORTALS.remove(portal);
			for (ServerPlayer p : portal.level.players()) ServerPlayNetworking.send(p, new ModNetwork.PortalClose(portal.id));
			if (portal.owner != null) {
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(portal.owner);
				if (owner != null) {
					ItemStack gauntlet = GauntletState.activeGauntlet(owner);
					if (!gauntlet.isEmpty()) GauntletState.setInUse(gauntlet, EnumSet.noneOf(Stone.class));
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (VIEW_LEVELS.get(player.getUUID()) != player.level()) syncPortals(player);
		}
		TRAVEL_GRACE.replaceAll((k, v) -> v - 1);
		TRAVEL_GRACE.values().removeIf(v -> v <= 0);
		// Iterate over a copy: travelling opens an exit portal (adds to the list).
		for (Portal p : new ArrayList<>(PORTALS)) {
			p.age++;
			if (p.age >= p.life) {
				PORTALS.remove(p);
				for (ServerPlayer sp : p.level.players()) ServerPlayNetworking.send(sp, new ModNetwork.PortalClose(p.id));
				if (p.dest >= 0) {
					ServerPlayer owner = server.getPlayerList().getPlayer(p.owner);
					if (owner != null) {
						ItemStack g = GauntletState.activeGauntlet(owner);
						if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
					}
				}
				continue;
			}
			if (p.dest < 0 || p.age < OPEN_TICKS || p.age > p.life - CLOSE_TICKS) continue;
			// Step through: the player's body crosses the portal plane inside the aperture.
			Vec3 normal = Vec3.directionFromRotation(0, p.yaw);
			Vec3 right = new Vec3(-normal.z, 0, normal.x);
			for (ServerPlayer player : new ArrayList<>(p.level.players())) {
				if (TRAVEL_GRACE.containsKey(player.getUUID()) || player.isSpectator()) continue;
				Vec3 rel = player.position().add(0, 0.9, 0).subtract(p.center);
				double d = rel.dot(normal), side = rel.dot(right), up = rel.y;
				if (Math.abs(d) < 0.45 && Math.abs(side) < PORTAL_SIZE * 0.3 && Math.abs(up) < PORTAL_SIZE * 0.4) travel(player, p);
			}
		}
	}

	private static void travel(ServerPlayer player, Portal p) {
		MinecraftServer server = player.level().getServer();
		ServerLevel target = server.getLevel(DIMS.get(p.dest));
		if (target == null) return;
		Vec3 from = player.position();
		Vec3 arrival = arrivalPoint(player, target);
		if (arrival == null) {
			TRAVEL_GRACE.put(player.getUUID(), 20);
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("ability.infinity_gauntlet.reason.portal_unsafe"));
			return;
		}
		TRAVEL_GRACE.put(player.getUUID(), 40);
		sendFx(player.level(), from, from);
		player.teleport(new TeleportTransition(target, arrival, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		player.fallDistance = 0;
		target.playSound(null, BlockPos.containing(arrival), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 0.7f);
		// A short exit tear behind the traveller.
		Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-1.2);
		open(target, arrival.add(back).add(0, PORTAL_SIZE / 2 - 0.1, 0), player.getYRot(), -1, player.getUUID(), 50); // visual only
		sendFx(target, arrival, arrival);
	}

	/** Safe arrival: Nether scales coordinates (1/8), End goes to the main island, Overworld to the surface. */
	private static Vec3 arrivalPoint(ServerPlayer player, ServerLevel to) {
		ServerLevel from = player.level();
		Vec3 pos = player.position();
		double scale = from.dimensionType().coordinateScale() / to.dimensionType().coordinateScale();
		if (to.dimension().equals(Level.END)) {
			BlockPos spawn = ServerLevel.END_SPAWN_POINT;
			if (!to.getWorldBorder().isWithinBounds(spawn)) return null;
			to.getChunk(spawn);
			BlockPos top = to.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
			if (top.getY() > spawn.getY() + 2 && safeFeet(to, top)) return Vec3.atBottomCenterOf(top); // spawn point is buried: arrive on the island
			if (safeFeet(to, spawn)) return Vec3.atBottomCenterOf(spawn);
			if (!GauntletConfig.get().abilityBlockChanges) return null;
			for (BlockPos block : BlockPos.betweenClosed(spawn.offset(-2, -1, -2), spawn.offset(2, 3, 2))) {
				if (!to.getWorldBorder().isWithinBounds(block) || !to.mayInteract(player, block)
						|| to.getBlockState(block).hasBlockEntity() || to.getBlockState(block).getDestroySpeed(to, block) < 0) return null;
			}
			// Same obsidian platform (with cleared air) as a vanilla End portal.
			net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(to, spawn.below(), true);
			return Vec3.atBottomCenterOf(spawn);
		}
		BlockPos column = BlockPos.containing(pos.x * scale, 64, pos.z * scale);
		if (from.dimension().equals(Level.END)) column = BlockPos.containing(pos.x, 64, pos.z);
		if (!to.getWorldBorder().isWithinBounds(column)) return null;
		to.getChunk(column); // load
		if (to.dimension().equals(Level.NETHER)) {
			for (int r = 0; r <= 12; r += 2) {
				for (int dx = -r; dx <= r; dx += Math.max(1, r)) {
					for (int dz = -r; dz <= r; dz += Math.max(1, r)) {
						for (int y = 100; y > 32; y--) {
							BlockPos p = column.offset(dx, y - 64, dz);
							if (safeFeet(to, p)) return Vec3.atBottomCenterOf(p);
						}
					}
				}
			}
			BlockPos p = column.atY(70);
			if (!GauntletConfig.get().abilityBlockChanges || !clearFeet(to, p)
					|| !to.mayInteract(player, p.below()) || !to.getBlockState(p.below()).isAir()) return null;
			to.setBlockAndUpdate(p.below(), Blocks.OBSIDIAN.defaultBlockState());
			return safeFeet(to, p) ? Vec3.atBottomCenterOf(p) : null;
		}
		BlockPos top = to.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		return safeFeet(to, top) ? Vec3.atBottomCenterOf(top) : null;
	}

	private static boolean clearFeet(ServerLevel level, BlockPos feet) {
		if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) return false;
		for (BlockPos pos : List.of(feet, feet.above())) {
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()
					|| state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW)) return false;
		}
		return true;
	}

	private static boolean safeFeet(ServerLevel level, BlockPos feet) {
		if (!clearFeet(level, feet)) return false;
		BlockPos floor = feet.below();
		BlockState state = level.getBlockState(floor);
		return state.isFaceSturdy(level, floor, Direction.UP) && state.getFluidState().isEmpty()
				&& !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
	}

	// ------------------------------------------------------------------ teleport where you look

	public static boolean blinkLoose(ServerPlayer player) {
		return blink(player, ItemStack.EMPTY, GauntletConfig.get().blinkRange);
	}

	private static boolean blink(ServerPlayer player, ItemStack g, double range) {
		GauntletConfig cfg = GauntletConfig.get();
		long now = player.level().getGameTime();
		if (now < BLINK_COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return false;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK && !com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.blink.too_far", String.valueOf(range)));
			return false;
		}
		Vec3 target = com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player) ? player.position().add(player.getLookAngle().scale(CosmicGauntlet.reach(player,range))) : standingSpot(player.level(), hit);
		if (target == null) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.blink.no_space"));
			return false;
		}
		BLINK_COOLDOWN.put(player.getUUID(), now + (g.isEmpty() ? 60 : cfg.blinkCooldownTicks));
		Vec3 from = player.position();
		player.teleport(new TeleportTransition(player.level(), target, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		player.fallDistance = 0;
		player.level().playSound(null, BlockPos.containing(from), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.4f);
		player.level().playSound(null, BlockPos.containing(target), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
		sendFx(player.level(), from, target);
		if (!g.isEmpty()) {
			GauntletState.setInUse(g, EnumSet.of(Stone.SPACE));
			CLEAR_GLOW.put(player.getUUID(), now + 20);
		}
		return true;
	}

	private static final Map<UUID, Long> CLEAR_GLOW = new HashMap<>();

	static {
		ServerTickEvents.END_SERVER_TICK.register(server -> CLEAR_GLOW.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) return true;
			if (p.level().getGameTime() < e.getValue()) return false;
			boolean ownPortal = PORTALS.stream().anyMatch(po -> po.dest >= 0 && e.getKey().equals(po.owner));
			ItemStack g = GauntletState.activeGauntlet(p);
			if (!g.isEmpty() && !ownPortal) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
			return true;
		}));
	}

	/** Feet position on top of the looked-at surface (or next to a wall), with 2 blocks of headroom. */
	private static Vec3 standingSpot(ServerLevel level, BlockHitResult hit) {
		BlockPos base = hit.getDirection() == Direction.UP ? hit.getBlockPos().above() : hit.getBlockPos().relative(hit.getDirection());
		for (int dy = 0; dy <= 3; dy++) {
			BlockPos p = base.above(dy);
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
				// Drop to the ground below if we're next to a wall.
				BlockPos ground = p;
				for (int i = 0; i < 6 && level.getBlockState(ground.below()).getCollisionShape(level, ground.below()).isEmpty(); i++) ground = ground.below();
				if (safeFeet(level, ground)) return Vec3.atBottomCenterOf(ground);
			}
		}
		return null;
	}

	private static void sendFx(ServerLevel level, Vec3 from, Vec3 to) {
		ModNetwork.SpaceFx fx = new ModNetwork.SpaceFx(from, to);
		for (ServerPlayer p : PlayerLookup.around(level, to, 128)) ServerPlayNetworking.send(p, fx);
	}

	private SpaceStone() {}
    public static int cooldown(ServerPlayer p, boolean blink) { return (int)Math.max(0,(blink?BLINK_COOLDOWN:COOLDOWN).getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }
    public static boolean selectDestination(ServerPlayer p,int index) {
        if(index<0 || index>=DESTINATIONS.length || DIMS.get(index).equals(p.level().dimension())) return false;
        var g=GauntletState.activeGauntlet(p);
        if(g.isEmpty() || !GauntletState.inserted(g).contains(Stone.SPACE)) return false;
        g.set(ModComponents.PORTAL_DESTINATION,index);return true;
    }

}

