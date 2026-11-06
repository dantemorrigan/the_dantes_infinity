package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.Loki;
import com.dantemorrigan.gauntlet.boss.LokiFx;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Ruined City releases its Mind Stone once per world, after Loki's defeat. */
public final class MindQuest {
	public static final int TOTAL_ZOMBIES = 10;
	private static final int ARRIVAL_TICKS = 36;
	private static final int REWARD_TICKS = 23;

	private record Arrival(Vec3 portal, float yaw, long startTick) {}

	private record Reward(Vec3 position, long revealTick) {
		static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.fieldOf("position").forGetter(Reward::position),
				Codec.LONG.fieldOf("reveal_tick").forGetter(Reward::revealTick)
		).apply(i, Reward::new));
	}

	private static final AttachmentType<UUID> BOSS = AttachmentRegistry.<UUID>builder()
			.persistent(UUIDUtil.CODEC).buildAndRegister(InfinityGauntlet.id("mind_loki"));
	private static final AttachmentType<Reward> REWARD = AttachmentRegistry.<Reward>builder()
			.persistent(Reward.CODEC).buildAndRegister(InfinityGauntlet.id("mind_loki_reward"));

	private static final AttachmentType<Integer> DEFEATED = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT).buildAndRegister(InfinityGauntlet.id("mind_zombies_defeated"));

	private static final int[][] SPAWN_OFFSETS = {
			{0, 5}, {0, -5}, {5, 0}, {-5, 0},
			{-4, -4}, {4, -4}, {-4, 4}, {4, 4},
			{0, 11}, {0, -11}
	};

	/**
	 * The zombies of the city that are still to be defeated, by UUID. Respawning used to depend on how many zombies stood
	 * near the city, so any that wandered off or vanished (reality strike, discarded) were replaced: they came back.
	 * Now only a zombie lost without being counted as defeated is ever replaced.
	 */
	private static final AttachmentType<List<String>> ALIVE = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("mind_zombies_alive"));
	/** True while the quest itself clears its zombies (reset): that is not a defeat. */
	private static boolean clearing;

	private static Arrival arrival;

	public static void init() {
		LokiFx.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> arrival = null);
		ServerTickEvents.END_SERVER_TICK.register(MindQuest::tick);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
			if (!clearing && entity instanceof MindZombie zombie && !zombie.isDeadOrDying()) {
				if (level.getDifficulty() == Difficulty.PEACEFUL) untrackZombie(level, zombie);
				else onMindZombieKilled(level, zombie, null);
			}
			if (entity instanceof Loki loki && isCurrentBoss(loki)) level.removeAttached(BOSS);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof MindZombie zombie && entity.level() instanceof ServerLevel level) {
				onMindZombieKilled(level, zombie, source.getEntity() instanceof ServerPlayer player ? player : null);
			}
		});
	}

	public static boolean isCurrentBoss(Loki loki) {
		return loki.level() instanceof ServerLevel level && loki.getUUID().equals(level.getAttached(BOSS));
	}

	public static int defeatedCount(MinecraftServer server) {
		Integer v = server.overworld().getAttached(DEFEATED);
		return v == null ? 0 : Math.clamp(v, 0, TOTAL_ZOMBIES);
	}

	public static int remainingCount(MinecraftServer server) {
		if (StoneRegistry.released(server, Stone.MIND)) return 0;
		return Math.max(0, TOTAL_ZOMBIES - defeatedCount(server));
	}

	private static void onMindZombieKilled(ServerLevel level, MindZombie zombie, ServerPlayer killer) {
		MinecraftServer server = level.getServer();
		if (level != server.overworld() || !MindCity.built(server) || StoneRegistry.released(server, Stone.MIND)
				|| defeatedCount(server) >= TOTAL_ZOMBIES) return;
		if (level.getAttached(ALIVE) != null) {
			if (!untrackZombie(level, zombie)) return;
		} else if (zombie.blockPosition().distSqr(MindCity.site(server)) > 96 * 96) {
			return;
		}

		ServerPlayer player = killer;
		if (player == null && zombie.getLastHurtByMob() instanceof ServerPlayer hurtBy) player = hurtBy;
		if (player == null && zombie.getRemovalReason() == Entity.RemovalReason.DISCARDED
				&& level.getNearestPlayer(zombie, 64) instanceof ServerPlayer nearest) player = nearest;
		if (player == null) return;

		int defeated = defeatedCount(server) + 1;
		level.setAttached(DEFEATED, defeated);
		StoneRegistry.markFound(server, Stone.MIND);
		level.sendParticles(ModParticles.QUEST_MIND_ESSENCE, zombie.getX(), zombie.getY() + 1, zombie.getZ(), 2, 0.25, 0.35, 0.25, 0);
		level.playSound(null, zombie.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5f, 1.1f);

		ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.progress", defeated, TOTAL_ZOMBIES));
		if (defeated == TOTAL_ZOMBIES) startArrival(level, MindCity.site(server), player);
	}

	private static boolean untrackZombie(ServerLevel level, MindZombie zombie) {
		List<String> ids = level.getAttached(ALIVE);
		if (ids == null || !ids.contains(zombie.getStringUUID())) return false;
		List<String> remaining = new ArrayList<>(ids);
		remaining.remove(zombie.getStringUUID());
		level.setAttached(ALIVE, remaining);
		return true;
	}

	/** Operator shortcut to the encounter. The normal reward still requires defeating Loki. */
	public static boolean summonLoki(ServerPlayer player) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (level != server.overworld() || !MindCity.built(server) || StoneRegistry.released(server, Stone.MIND)
				|| level.getAttached(REWARD) != null) return false;
		BlockPos site = MindCity.site(server);
		if (player.distanceToSqr(Vec3.atCenterOf(site)) > 64 * 64 || !startArrival(level, site, player)) return false;
		level.setAttached(DEFEATED, TOTAL_ZOMBIES);
		StoneRegistry.markFound(server, Stone.MIND);
		List<String> alive = level.getAttached(ALIVE);
		level.removeAttached(ALIVE);
		if (alive != null) {
			for (String id : alive) {
				if (level.getEntity(UUID.fromString(id)) instanceof MindZombie zombie) zombie.discard();
			}
		}
		return true;
	}

	private static boolean startArrival(ServerLevel level, BlockPos site, ServerPlayer player) {
		if (arrival != null || level.getAttached(BOSS) != null || level.getDifficulty() == Difficulty.PEACEFUL) return false;
		arrival = findArrival(level, site, player);
		if (arrival == null) return false;
		Vec3 portal = arrival.portal();
		LokiFx.send(level, LokiFx.Effect.GOLD_WORLD_WASH, portal, arrival.yaw());
		level.playSound(null, BlockPos.containing(portal), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.1f, 0.77f);
		for (ServerPlayer witness : level.players()) {
			if (witness.distanceToSqr(portal) < 64 * 64) {
				ModStatus.send(witness, Component.translatable("msg.infinity_gauntlet.mind_quest.portal"));
			}
		}
		return true;
	}

	private static Arrival findArrival(ServerLevel level, BlockPos site, ServerPlayer player) {
		// Use the open avenues around the monument and prefer a spot the player can see.
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Arrival best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int side : new int[]{-15, -11, -7, 7, 11, 15}) {
			for (int lane : new int[]{0, -2, 2}) {
				for (boolean eastWest : new boolean[]{false, true}) {
					BlockPos base = site.offset(eastWest ? side : lane, 1, eastWest ? lane : side);
					if (!level.hasChunkAt(base)) continue;
					for (int y : new int[]{base.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ())}) {
						Vec3 portal = Vec3.atBottomCenterOf(new BlockPos(base.getX(), y, base.getZ()));
						Vec3 center = portal.add(0, 1.5, 0);
						Vec3 offset = center.subtract(eye);
						double distance = offset.length();
						if (distance < 3.5 || !Loki.safeFooting(level, portal)) continue;
						float yaw = Loki.yawTo(portal, player.position());
						if (!Loki.safeFooting(level, portal.add(Vec3.directionFromRotation(0, yaw).scale(1.3)))) continue;
						double score = Math.abs(distance - 9) + Math.abs(y - base.getY()) * 2 - offset.normalize().dot(look) * 6;
						if (score >= bestScore) continue;
						if (level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
								!= HitResult.Type.MISS) score += 22;
						if (score >= bestScore) continue;
						bestScore = score;
						best = new Arrival(portal, yaw, level.getGameTime());
					}
				}
			}
		}
		return best;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (StoneRegistry.released(server, Stone.MIND)) {
			arrival = null;
			level.removeAttached(REWARD);
			UUID id = level.getAttached(BOSS);
			if (id != null) {
				level.removeAttached(BOSS);
				if (level.getEntity(id) instanceof Loki loki && loki.isAlive()) loki.discard();
			}
			return;
		}
		Reward reward = level.getAttached(REWARD);
		if (reward != null) {
			revealReward(level, reward);
			return;
		}
		if (!MindCity.built(server)) return;
		BlockPos site = MindCity.site(server);
		if (arrival != null) {
			runArrival(level, site);
		} else if (level.getGameTime() % 40 == 0 && level.hasChunkAt(site)) {
			ServerPlayer nearby = level.getNearestPlayer(site.getX(), site.getY(), site.getZ(), 64, false) instanceof ServerPlayer player ? player : null;
			if (nearby == null || nearby.isSpectator()) return;
			if (remainingCount(server) > 0) ensureZombies(level, site);
			else startArrival(level, site, nearby);
		}
	}

	private static void runArrival(ServerLevel level, BlockPos site) {
		Vec3 portal = arrival.portal();
		long elapsed = level.getGameTime() - arrival.startTick();
		if (!level.isPositionEntityTicking(BlockPos.containing(portal)) || level.getDifficulty() == Difficulty.PEACEFUL) {
			arrival = null;
			return;
		}
		if (elapsed == 14) {
			LokiFx.send(level, LokiFx.Effect.PORTAL_OPEN, portal, arrival.yaw());
			level.playSound(null, BlockPos.containing(portal), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.4f, 0.85f);
		}
		if (elapsed < ARRIVAL_TICKS) return;
		if (Loki.safeFooting(level, portal)) {
			Loki boss = ModEntities.LOKI.create(level, EntitySpawnReason.STRUCTURE);
			if (boss != null) {
				boss.startEncounter(site);
				boss.snapTo(portal.x, portal.y, portal.z, arrival.yaw(), 0);
				boss.setYBodyRot(arrival.yaw());
				if (level.addFreshEntity(boss)) {
					level.setAttached(BOSS, boss.getUUID());
					LokiFx.send(level, LokiFx.Effect.PORTAL_LOOP, portal, arrival.yaw());
					level.playSound(null, boss.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.6f, 0.85f);
				}
			}
		}
		arrival = null;
	}

	public static void onLokiDefeated(ServerLevel level, Loki loki) {
		if (!isCurrentBoss(loki)) return;
		level.removeAttached(BOSS);
		if (StoneRegistry.released(level.getServer(), Stone.MIND) || level.getAttached(REWARD) != null) return;
		Vec3 at = loki.position();
		BlockPos city = MindCity.site(level.getServer());
		if (at.y < city.getY() - 4) at = MindCity.plazaStand(city);
		level.setAttached(REWARD, new Reward(at, level.getGameTime() + REWARD_TICKS));
		LokiFx.send(level, LokiFx.Effect.ESSENCE_COLLECT, at, 0);
		level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.1f, 1.05f);
		ExperienceOrb.award(level, at.add(0, 0.4, 0), Loki.EXPERIENCE);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(at) <= 48 * 48) ModAdvancements.award(player, "defeat_loki");
		}
	}

	private static void revealReward(ServerLevel level, Reward reward) {
		Vec3 at = reward.position();
		BlockPos pos = BlockPos.containing(at);
		if (level.getGameTime() < reward.revealTick() || !level.isPositionEntityTicking(pos)) return;
		ItemEntity stone = new ItemEntity(level, at.x, at.y + 0.45, at.z, new ItemStack(ModItems.MIND_STONE), 0, 0.16, 0);
		stone.setDefaultPickUpDelay();
		stone.setUnlimitedLifetime();
		stone.setInvulnerable(true);
		if (!level.addFreshEntity(stone)) return;
		StoneRegistry.markReleased(level.getServer(), Stone.MIND);
		level.removeAttached(REWARD);
		LokiFx.send(level, LokiFx.Effect.MIND_STONE_REVEAL, at, 0);
		level.playSound(null, pos, ModSounds.STONE_INSERT, SoundSource.PLAYERS, 2.2f, 1.0f);
		level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 1.3f);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) < 48 * 48) ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.completed"));
		}
	}

	/** Ensures the remaining undefeated Mind Zombies exist: only those lost without being defeated are ever replaced. */
	public static void ensureZombies(ServerLevel level, BlockPos site) {
		MinecraftServer server = level.getServer();
		if (StoneRegistry.released(server, Stone.MIND) || level.getDifficulty() == Difficulty.PEACEFUL) return;
		int needed = remainingCount(server);
		if (needed <= 0) return;

		List<String> ids = level.getAttached(ALIVE);
		if (ids == null) {
			// First time (or a world from before): the zombies that already stand around the city are the quest's zombies.
			ids = new ArrayList<>();
			for (MindZombie z : level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 64.0), MindZombie::isAlive)) {
				if (ids.size() < needed) ids.add(z.getUUID().toString());
			}
		} else {
			ids = new ArrayList<>(ids);
			// A tracked zombie that is loaded and gone (dead or removed some other way) is no longer tracked.
			ids.removeIf(s -> {
				var e = level.getEntity(UUID.fromString(s));
				return e != null && (!e.isAlive() || e.isRemoved());
			});
		}
		int toSpawn = needed - ids.size();
		for (int i = 0; i < toSpawn; i++) {
			int idx = (ids.size()) % SPAWN_OFFSETS.length;
			int sx = site.getX() + SPAWN_OFFSETS[idx][0];
			int sz = site.getZ() + SPAWN_OFFSETS[idx][1];
			int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
			BlockPos spawnPos = new BlockPos(sx, Math.max(site.getY() + 1, sy), sz);
			MindZombie zombie = ModEntities.MIND_ZOMBIE.create(level, EntitySpawnReason.STRUCTURE);
			if (zombie == null) continue;
			zombie.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, (idx * 36.0f) % 360.0f, 0.0f);
			zombie.setHomeTo(site, MindCity.R_FLAT - 2);
			if (level.addFreshEntity(zombie)) ids.add(zombie.getStringUUID());
		}
		level.setAttached(ALIVE, ids);
	}

	/** Resets the Mind Stone quest when the Mind Stone is forgotten or stones are reset. */
	public static void reset(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		overworld.removeAttached(DEFEATED);
		overworld.removeAttached(ALIVE);
		arrival = null;
		overworld.removeAttached(REWARD);
		UUID boss = overworld.getAttached(BOSS);
		overworld.removeAttached(BOSS);
		if (boss != null && overworld.getEntity(boss) instanceof Loki loki) loki.discard();
		if (!MindCity.built(server)) return;
		BlockPos site = MindCity.site(server);
		if (overworld.hasChunkAt(site)) {
			clearing = true;
			try {
				for (MindZombie z : overworld.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 64.0), e -> true)) {
					z.discard();
				}
			} finally {
				clearing = false;
			}
			ensureZombies(overworld, site);
		}
	}

	private MindQuest() {}
}
