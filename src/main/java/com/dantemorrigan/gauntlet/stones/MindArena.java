package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.boss.Loki;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The city barrier is enforced server-side. Its roster persists through logout and server restarts. */
public final class MindArena {
	private static final AttachmentType<List<String>> FIGHTERS = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("mind_arena_fighters"));
	private static final AttachmentType<BlockPos> RESPAWN_SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("mind_arena_respawn"));
	private static final int RADIUS = MindCity.R_FLAT, DEPTH = 4, HEIGHT = 24;
	private static final DustParticleOptions BARRIER = new DustParticleOptions(0xFFCD54, 1.4f);
	private static final Map<UUID, Vec3> LAST_INSIDE = new HashMap<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_INSIDE.clear());
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(level instanceof ServerLevel serverLevel) || player.isCreative() || !protects(serverLevel, pos));
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				level instanceof ServerLevel serverLevel && !player.isCreative() && (protects(serverLevel, hit.getBlockPos()) || protects(serverLevel, hit.getBlockPos().relative(hit.getDirection())))
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer p) || p.isCreative() || !active(p.level().getServer())
					|| !(p.getItemInHand(hand).getItem() instanceof BucketItem)) return InteractionResult.PASS;
			Vec3 eye = p.getEyePosition();
			var hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(p.blockInteractionRange())),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
			if (hit.getType() == HitResult.Type.BLOCK && (protects(p.level(), hit.getBlockPos())
					|| protects(p.level(), hit.getBlockPos().relative(hit.getDirection())))) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});
	}

	public static boolean active(MinecraftServer server) {
		return sealed(server) && !StoneRegistry.released(server, Stone.MIND);
	}

	static boolean sealed(MinecraftServer server) {
		return server.overworld().hasAttached(FIGHTERS);
	}

	private static boolean registered(ServerPlayer player) {
		return player.level().getServer().overworld().getAttachedOrElse(FIGHTERS, List.of()).contains(player.getStringUUID());
	}

	static boolean fighter(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		// A registered fighter may switch to creative for testing without wiping the active encounter.
        return active(server) && registered(player) && player.isAlive() && !player.isSpectator()
				&& player.level() == server.overworld() && inside(MindCity.site(server), player.position());
	}

	static boolean begin(ServerLevel level, BlockPos site) {
		List<String> entrants = level.players().stream().filter(p -> eligible(p) && inside(site, p.position()))
				.map(ServerPlayer::getStringUUID).toList();
		if (entrants.isEmpty()) return false;
		level.setAttached(FIGHTERS, entrants);
		for (ServerPlayer p : level.players()) {
			if (entrants.contains(p.getStringUUID())) ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.mind_quest.sealed"));
		}
		return true;
	}

	/** Removes a defeated participant and reports whether there is anyone left to fight. */
	static boolean onDeath(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!active(server) || !registered(player)) return false;
		ServerLevel level = server.overworld();
		player.setAttached(RESPAWN_SITE, MindCity.site(server));
		LAST_INSIDE.remove(player.getUUID());
		level.setAttached(FIGHTERS, level.getAttachedOrElse(FIGHTERS, List.of()).stream()
				.filter(id -> !id.equals(player.getStringUUID())).toList());
		return level.players().stream().noneMatch(MindArena::fighter);
	}

	public static TeleportTransition takeRespawn(ServerPlayer player, TeleportTransition.PostTeleportTransition post) {
		BlockPos site = player.removeAttached(RESPAWN_SITE);
		if (site == null) return null;
		ServerLevel level = player.level().getServer().overworld();
		Vec3 at = outsideRespawn(level, site);
		return new TeleportTransition(level, at, Vec3.ZERO, Loki.yawTo(at, Vec3.atCenterOf(site)), 0, post);
	}

	private static Vec3 outsideRespawn(ServerLevel level, BlockPos site) {
		for (int radius = RADIUS + 4; radius <= RADIUS + 14; radius += 2) {
			for (int angle : new int[]{0, 45, -45, 90, -90, 135, -135, 180}) {
				Vec3 direction = Vec3.directionFromRotation(0, angle);
				BlockPos column = BlockPos.containing(Vec3.atBottomCenterOf(site).add(direction.scale(radius)));
				Vec3 at = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
				if (Loki.safeFooting(level, at)) return at;
			}
		}
		BlockPos column = site.south(RADIUS + 4);
		return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column));
	}

	static void open(MinecraftServer server) {
		server.overworld().removeAttached(FIGHTERS);
		LAST_INSIDE.clear();
	}

	private static boolean eligible(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}

	public static boolean inside(BlockPos site, Vec3 pos) {
		double dx = pos.x - site.getX() - .5, dz = pos.z - site.getZ() - .5;
		return dx * dx + dz * dz <= RADIUS * RADIUS && pos.y >= site.getY() - DEPTH && pos.y < site.getY() + HEIGHT;
	}

	/** Leave room for Thanos's landing search outside Loki's arena. */
	public static boolean excludesThanos(ServerLevel level, Vec3 pos) {
		MinecraftServer server = level.getServer();
		if (level != server.overworld() || !MindCity.built(server)
				|| (StoneRegistry.released(server, Stone.MIND) && !MindQuest.lokiEncounterRunning(server))) return false;
		BlockPos site = MindCity.site(server);
		double dx = pos.x - site.getX() - .5, dz = pos.z - site.getZ() - .5;
		int clearance = RADIUS + 16;
		return dx * dx + dz * dz <= clearance * clearance;
	}

	public static boolean protects(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		return level == server.overworld() && active(server) && inside(MindCity.site(server), Vec3.atCenterOf(pos));
	}

	public static boolean blocksTeleport(ServerPlayer player, TeleportTransition transition) {
		return blocksTeleport(player, transition.newLevel(), transition.position(), transition.relatives());
	}

	public static boolean blocksTeleport(ServerPlayer player, ServerLevel destination, Vec3 at, Set<Relative> relative) {
		MinecraftServer server = player.level().getServer();
		if (!eligible(player) || !active(server)) return false;
		Vec3 old = player.position();
		at = at.add(relative.contains(Relative.X) ? old.x : 0, relative.contains(Relative.Y) ? old.y : 0,
				relative.contains(Relative.Z) ? old.z : 0);
		boolean entering = destination == server.overworld() && inside(MindCity.site(server), at);
		return registered(player) != entering;
	}

	static void tick(ServerLevel level, BlockPos site) {
		MinecraftServer server = level.getServer();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) enforce(p, level, site);
		if (level.getGameTime() % 8 == 0) showBarrier(level, site);
	}

	public static void enforce(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (active(server)) enforce(player, server.overworld(), MindCity.site(server));
	}

	private static void enforce(ServerPlayer p, ServerLevel level, BlockPos site) {
		if (!eligible(p)) return;
		boolean inside = p.level() == level && inside(site, p.position());
		if (registered(p)) {
			if (inside) {
				LAST_INSIDE.put(p.getUUID(), p.position());
			} else {
				boolean crossedSide = p.level() == level && p.getY() >= site.getY() - DEPTH && p.getY() < site.getY() + HEIGHT;
				Vec3 safe = MindCity.plazaStand(site);
				move(p, level, crossedSide ? LAST_INSIDE.getOrDefault(p.getUUID(), safe) : safe);
			}
		} else if (inside) {
			Vec3 flat = new Vec3(p.getX() - site.getX() - .5, 0, p.getZ() - site.getZ() - .5);
			if (flat.horizontalDistanceSqr() < .01) flat = new Vec3(0, 0, 1);
			Vec3 edge = Vec3.atBottomCenterOf(site).add(flat.normalize().scale(RADIUS + 2));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(edge));
			move(p, level, Vec3.atBottomCenterOf(ground));
		}
	}

	private static void move(ServerPlayer player, ServerLevel level, Vec3 pos) {
		player.stopRiding();
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		player.fallDistance = 0;
	}

	private static void showBarrier(ServerLevel level, BlockPos site) {
		for (int i = 0; i < 48; i++) {
			double a = i * Math.TAU / 48;
			double x = site.getX() + .5 + Math.cos(a) * RADIUS, z = site.getZ() + .5 + Math.sin(a) * RADIUS;
			level.sendParticles(BARRIER, x, site.getY() + 8, z, 7, .05, 5.3, .05, 0);
		}
	}

	private MindArena() {}
}
