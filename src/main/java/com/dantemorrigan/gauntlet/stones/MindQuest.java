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

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The Ruined City releases its Mind Stone once per world, after Loki's defeat. */
public final class MindQuest {
	public static final int TOTAL_ZOMBIES = 10;
	private static final int ARRIVAL_TICKS = 36;
	private static final int REWARD_TICKS = 23;

	private record Arrival(Vec3 portal, float yaw, long startTick) {}

	private record Reward(Vec3 position, long revealTick, Optional<UUID> recipient) {
		static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.fieldOf("position").forGetter(Reward::position),
				Codec.LONG.fieldOf("reveal_tick").forGetter(Reward::revealTick),
				UUIDUtil.CODEC.optionalFieldOf("recipient").forGetter(Reward::recipient)
		).apply(i, Reward::new));
	}

	private static final AttachmentType<UUID> BOSS = AttachmentRegistry.<UUID>builder()
			.persistent(UUIDUtil.CODEC).buildAndRegister(InfinityGauntlet.id("mind_loki"));
	private static final AttachmentType<Boolean> LOKI_REPLAY = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("mind_loki_replay"));
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
	private static Arrival arrival;
	private static Reward displayedReward;

	public static void init() {
		LokiFx.init();
		MindArena.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { arrival = null; displayedReward = null; });
		ServerTickEvents.END_SERVER_TICK.register(MindQuest::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof MindZombie zombie && (!MindArena.active(level.getServer())
					|| level != level.getServer().overworld()
					|| !MindArena.inside(MindCity.site(level.getServer()), zombie.position()))) zombie.discard();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
			if (entity instanceof MindZombie zombie) untrackZombie(level.getServer().overworld(), zombie);
			if (entity instanceof Loki loki && isCurrentBoss(loki)) level.removeAttached(BOSS);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel level) || !MindArena.active(level.getServer())
					|| !(entity instanceof MindZombie || entity instanceof Loki loki && loki.isCityEncounter())) return true;
			return source.getEntity() instanceof ServerPlayer player && MindArena.fighter(player)
					&& level == level.getServer().overworld()
					&& MindArena.inside(MindCity.site(level.getServer()), entity.position());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof MindZombie zombie && entity.level() instanceof ServerLevel level) {
				onMindZombieKilled(level, zombie, source.getEntity() instanceof ServerPlayer player ? player : null);
			} else if (entity instanceof ServerPlayer player && MindArena.onDeath(player)) {
				restartAfterDefeat(player.level().getServer());
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
		if (!untrackZombie(level, zombie) || killer == null || !MindArena.fighter(killer)
				|| !MindArena.inside(MindCity.site(server), zombie.position())) return;

		int defeated = defeatedCount(server) + 1;
		level.setAttached(DEFEATED, defeated);
		StoneRegistry.markFound(server, Stone.MIND);
		level.sendParticles(ModParticles.QUEST_MIND_ESSENCE, zombie.getX(), zombie.getY() + 1, zombie.getZ(), 2, 0.25, 0.35, 0.25, 0);
		level.playSound(null, zombie.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5f, 1.1f);

		ModStatus.send(killer, Component.translatable("msg.infinity_gauntlet.mind_quest.progress", defeated, TOTAL_ZOMBIES));
		if (defeated == TOTAL_ZOMBIES) startArrival(level, MindCity.site(server), killer);
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
						if (distance < 3.5 || !MindArena.inside(site, portal) || !Loki.safeFooting(level, portal)) continue;
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
		boolean stoneReleased = StoneRegistry.released(server, Stone.MIND);
		boolean replay = Boolean.TRUE.equals(level.getAttached(LOKI_REPLAY));
		if (stoneReleased) {
			if (MindArena.sealed(server)) MindArena.open(server);
			level.removeAttached(REWARD);
			if (!replay) {
				clearLoki(level);
				return;
			}
		}
		if (!MindCity.built(server)) return;
		BlockPos site = MindCity.site(server);
		if (!stoneReleased && !MindArena.active(server) && level.getDifficulty() != Difficulty.PEACEFUL && MindArena.begin(level, site)) {
			level.removeAttached(ALIVE); // Adopt in-bounds zombies from saves that predate the barrier.
			if (remainingCount(server) > 0) ensureZombies(level, site);
			else if (level.getAttached(REWARD) == null) {
				level.players().stream().filter(MindArena::fighter).findFirst()
						.ifPresent(player -> startArrival(level, site, player));
			}
		}
		boolean sealed = MindArena.active(server);
		if (sealed) {
			MindArena.tick(level, site);
			keepInside(level, site);
		}
		Reward reward = level.getAttached(REWARD);
		if (reward != null) {
			revealReward(level, reward);
		} else if (arrival != null) {
			runArrival(level, site);
		} else if ((sealed || replay) && level.getGameTime() % 40 == 0 && level.hasChunkAt(site)) {
			ServerPlayer fighter = sealed ? level.players().stream().filter(MindArena::fighter).findFirst().orElse(null)
					: level.getNearestPlayer(site.getX(), site.getY(), site.getZ(), 64, false) instanceof ServerPlayer p ? p : null;
			if (fighter == null || fighter.isSpectator()) return;
			if (remainingCount(server) > 0) ensureZombies(level, site);
			else startArrival(level, site, fighter);
		}
	}

	private static void keepInside(ServerLevel level, BlockPos site) {
		for (String id : level.getAttachedOrElse(ALIVE, List.of())) {
			if (level.getEntity(UUID.fromString(id)) instanceof MindZombie zombie && !MindArena.inside(site, zombie.position())) {
				zombie.discard(); // No essence; a replacement spawns inside.
			}
		}
		UUID boss = level.getAttached(BOSS);
		if (boss != null && level.getEntity(boss) instanceof Loki loki && loki.isAlive()
				&& !MindArena.inside(site, loki.position())) {
			Vec3 stand = MindCity.plazaStand(site);
			loki.setPos(stand);
			loki.setDeltaMovement(Vec3.ZERO);
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
					boss.greet(level);
					level.playSound(null, boss.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.6f, 0.85f);
				}
			}
		}
		arrival = null;
	}

	public static void onLokiDefeated(ServerLevel level, Loki loki) {
		if (!isCurrentBoss(loki)) return;
		level.removeAttached(BOSS);
		level.removeAttached(LOKI_REPLAY);
		Vec3 at = loki.position();
		BlockPos city = MindCity.site(level.getServer());
		if (!MindArena.inside(city, at)) at = MindCity.plazaStand(city);
		if (!StoneRegistry.released(level.getServer(), Stone.MIND) && level.getAttached(REWARD) == null) {
			level.setAttached(REWARD, new Reward(at, level.getGameTime() + REWARD_TICKS, Optional.empty()));
			LokiFx.send(level, LokiFx.Effect.ESSENCE_COLLECT, at, 0);
			level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.1f, 1.05f);
		}
		ExperienceOrb.award(level, at.add(0, 0.4, 0), Loki.EXPERIENCE);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(at) <= 48 * 48) ModAdvancements.award(player, "defeat_loki");
		}
	}

	private static void revealReward(ServerLevel level, Reward reward) {
		if (level.getGameTime() < reward.revealTick()) return;
		MinecraftServer server = level.getServer();
		ServerPlayer recipient = reward.recipient().map(server.getPlayerList()::getPlayer).orElse(null);
		if (reward != displayedReward || !rewardEligible(level, recipient)) {
			recipient = level.players().stream().filter(player -> rewardEligible(level, player))
					.min(Comparator.comparingDouble(player -> player.distanceToSqr(reward.position()))).orElse(null);
			if (recipient == null) return;
			Vec3 eye = recipient.getEyePosition();
			Vec3 front = eye.add(recipient.getLookAngle().scale(2.3));
			var hit = level.clip(new ClipContext(eye, front, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, recipient));
			if (hit.getType() != HitResult.Type.MISS) front = eye.lerp(hit.getLocation(), .7);
			displayedReward = new Reward(reward.position(), level.getGameTime() + LokiFx.Effect.MIND_STONE_REVEAL.ticks,
					Optional.of(recipient.getUUID()));
			level.setAttached(REWARD, displayedReward);
			LokiFx.reward(level, front, recipient);
			level.playSound(null, recipient.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 1.35f);
			return;
		}

		ItemStack rewardStack = new ItemStack(ModItems.MIND_STONE);
		if (!recipient.getInventory().add(rewardStack)) {
			ItemStack held = recipient.getMainHandItem();
			recipient.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, rewardStack);
			if (!held.isEmpty()) OrbItem.giveIntoBackpack(recipient, held);
		}
		StoneRegistry.markReleased(server, Stone.MIND);
		level.removeAttached(REWARD);
		displayedReward = null;
		MindArena.open(server);
		level.playSound(null, recipient.blockPosition(), ModSounds.STONE_INSERT, SoundSource.PLAYERS, 2.2f, 1);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(reward.position()) < 48 * 48) ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.completed"));
		}
	}

	private static boolean rewardEligible(ServerLevel level, ServerPlayer player) {
		return player != null && player.level() == level && player.isAlive() && !player.isSpectator()
				&& (!MindArena.active(level.getServer()) || MindArena.fighter(player));
	}

	/** Ensures the remaining undefeated Mind Zombies exist: only those lost without being defeated are ever replaced. */
	private static void ensureZombies(ServerLevel level, BlockPos site) {
		MinecraftServer server = level.getServer();
		if (StoneRegistry.released(server, Stone.MIND) || level.getDifficulty() == Difficulty.PEACEFUL) return;
		int needed = remainingCount(server);
		if (needed <= 0) return;

		List<String> ids = level.getAttached(ALIVE);
		if (ids == null) {
			// First time (or a world from before): the zombies that already stand around the city are the quest's zombies.
			ids = new ArrayList<>();
			for (MindZombie z : level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_FLAT + 1), z -> z.isAlive() && MindArena.inside(site, z.position()))) {
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

	/** Restarts the city encounter while preserving zombie progress and the world's unique Mind Stone. */
	public static void resetLoki(MinecraftServer server) {
		ServerLevel level = server.overworld();
		clearLoki(level);
		if (StoneRegistry.released(server, Stone.MIND) || level.getAttached(REWARD) != null) {
			level.setAttached(LOKI_REPLAY, true);
		}
	}

	private static void clearLoki(ServerLevel level) {
		arrival = null;
		UUID boss = level.getAttached(BOSS);
		if (boss == null) return;
		level.removeAttached(BOSS);
		if (level.getEntity(boss) instanceof Loki loki) loki.discard();
	}

	private static void restartAfterDefeat(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = MindCity.site(server);
		MindArena.open(server);
		UUID boss = level.getAttached(BOSS);
		if (boss != null && level.getEntity(boss) instanceof Loki loki && loki.isDeadOrDying()) return;
		clearLoki(level);
		if (defeatedCount(server) < TOTAL_ZOMBIES) level.removeAttached(DEFEATED);
		level.removeAttached(ALIVE);
		AABB city = new AABB(site).inflate(MindCity.R_FLAT + 2);
		for (MindZombie zombie : level.getEntitiesOfClass(MindZombie.class, city)) zombie.discard();
		for (Loki copy : level.getEntitiesOfClass(Loki.class, city, e -> e.isCityEncounter() && e.isIllusion())) copy.discard();
	}

	/** Resets the Mind Stone quest when the Mind Stone is forgotten or stones are reset. */
	public static void reset(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		overworld.removeAttached(DEFEATED);
		overworld.removeAttached(ALIVE);
		MindArena.open(server);
		clearLoki(overworld);
		overworld.removeAttached(REWARD);
		overworld.removeAttached(LOKI_REPLAY);
		if (!MindCity.built(server)) return;
		BlockPos site = MindCity.site(server);
		if (overworld.hasChunkAt(site)) {
			for (MindZombie zombie : overworld.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 64.0))) {
				zombie.discard();
			}
		}
	}

	private MindQuest() {}
}
