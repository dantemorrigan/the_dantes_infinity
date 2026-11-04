package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Mind Stone quest: in the Ruined City roam 10 smart Mind Zombies glowing with yellow Mind Stone essence.
 * Each defeated zombie releases its essence; when the 10th zombie falls, all 10 essence streams converge
 * in front of the player in a glowing animation and the Mind Stone appears directly in the player's hands.
 */
public final class MindQuest {
	public static final int TOTAL_ZOMBIES = 10;
	public static final int CONVERGENCE_TICKS = 34;

	private static final AttachmentType<Integer> DEFEATED = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT).buildAndRegister(InfinityGauntlet.id("mind_zombies_defeated"));

	private static final int[][] SPAWN_OFFSETS = {
			{0, 5}, {0, -5}, {5, 0}, {-5, 0},
			{-4, -4}, {4, -4}, {-4, 4}, {4, 4},
			{0, 11}, {0, -11}
	};

	private record Convergence(UUID playerId, long startTick) {}

	private static Convergence convergence;

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> convergence = null);
		ServerTickEvents.END_SERVER_TICK.register(MindQuest::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof MindZombie zombie) || !(entity.level() instanceof ServerLevel level)) return;
			onMindZombieKilled(level, zombie, source.getEntity() instanceof ServerPlayer sp ? sp : null);
		});
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
		// Always show yellow essence rising from the defeated zombie.
		level.sendParticles(ModParticles.QUEST_MIND_ESSENCE, zombie.getX(), zombie.getY() + 1.0, zombie.getZ(), 2, 0.25, 0.35, 0.25, 0);
		level.playSound(null, zombie.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5F, 1.1F);

		if (StoneRegistry.released(server, Stone.MIND) || convergence != null) return;
		ServerPlayer player = killer;
		if (player == null && zombie.getLastHurtByMob() instanceof ServerPlayer hurtBy) player = hurtBy;
		if (player == null && level.getNearestPlayer(zombie, 64) instanceof ServerPlayer nearest) player = nearest;
		if (player == null) return;

		int defeated = Math.min(TOTAL_ZOMBIES, defeatedCount(server) + 1);
		server.overworld().setAttached(DEFEATED, defeated);
		StoneRegistry.markFound(server, Stone.MIND);

		if (defeated < TOTAL_ZOMBIES) {
			level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 0.85F + defeated * 0.04F);
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.progress", defeated, TOTAL_ZOMBIES));
			return;
		}

		startConvergence(player);
	}

	/** Starts the 10-stream essence convergence finale in front of the player. */
	public static void startConvergence(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		convergence = new Convergence(player.getUUID(), level.getGameTime());
		Vec3 focal = focalPoint(player);
		level.sendParticles(ModParticles.QUEST_MIND_CONVERGENCE, focal.x, focal.y, focal.z, 1, 0, 0, 0, 0);
		level.playSound(null, BlockPos.containing(focal), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.25F);
		level.playSound(null, BlockPos.containing(focal), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.2F, 1.0F);
		ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.converging"));
	}

	private static Vec3 focalPoint(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flatLook = new Vec3(look.x, 0, look.z);
		if (flatLook.lengthSqr() < 1e-4) flatLook = new Vec3(0, 0, 1);
		else flatLook = flatLook.normalize();
		return player.position().add(0, player.getEyeHeight() - 0.2, 0).add(flatLook.scale(2.0));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld.getGameTime() % 40 == 0 && MindCity.built(server) && convergence == null && !StoneRegistry.released(server, Stone.MIND)) {
			BlockPos site = MindCity.site(server);
			if (overworld.hasChunkAt(site) && overworld.getNearestPlayer(site.getX(), site.getY(), site.getZ(), 96, false) != null) {
				ensureZombies(overworld, site);
			}
		}

		if (convergence != null) {
			runConvergence(server);
		}
	}

	private static void runConvergence(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayer(convergence.playerId());
		if (player == null) {
			convergence = null;
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long elapsed = level.getGameTime() - convergence.startTick();
		Vec3 focal = focalPoint(player);

		// 10 golden streams of Mind Stone essence spiral inward toward the focal point in front of the player.
		if (elapsed <= 26 && elapsed % 2 == 0) {
			double k = elapsed / 26.0;
			double radius = (1.0 - k) * 3.0;
			for (int i = 0; i < TOTAL_ZOMBIES; i++) {
				double angle = i * (Math.PI * 2.0 / TOTAL_ZOMBIES) + k * Math.PI * 1.6;
				double yOff = Math.sin(k * Math.PI + i * 0.6) * (1.0 - k) * 0.85;
				double sx = focal.x + Math.cos(angle) * radius;
				double sy = focal.y + yOff;
				double sz = focal.z + Math.sin(angle) * radius;
				level.sendParticles(ModParticles.QUEST_MIND_ESSENCE, sx, sy, sz, 1, 0, 0, 0, 0);
			}
		}

		if (elapsed == 12 || elapsed == 24) {
			level.sendParticles(ModParticles.QUEST_MIND_CONVERGENCE, focal.x, focal.y, focal.z, 1, 0, 0, 0, 0);
			level.playSound(null, BlockPos.containing(focal), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.0F + elapsed * 0.02F);
		}

		if (elapsed >= CONVERGENCE_TICKS) {
			convergence = null;
			level.sendParticles(ModParticles.QUEST_MIND_REWARD_BURST, focal.x, focal.y, focal.z, 1, 0, 0, 0, 0);
			level.sendParticles(ModParticles.QUEST_MIND_REWARD_BURST, player.getX(), player.getY() + 1.1, player.getZ(), 1, 0, 0, 0, 0);
			level.playSound(null, player.blockPosition(), ModSounds.STONE_INSERT, SoundSource.PLAYERS, 2.2F, 1.0F);
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 1.3F);
			if (StoneRegistry.markReleased(server, Stone.MIND)) {
				giveIntoHands(player, new ItemStack(ModItems.MIND_STONE));
			}
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.mind_quest.completed"));
		}
	}

	/** Places the Mind Stone directly into the player's main hand, moving any held item safely into the backpack. */
	public static void giveIntoHands(ServerPlayer player, ItemStack stone) {
		ItemStack current = player.getMainHandItem();
		if (!current.isEmpty()) {
			OrbItem.giveIntoBackpack(player, current.copy());
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, stone);
	}

	/** Ensures the remaining undefeated Mind Zombies are present in the Ruined City. */
	public static void ensureZombies(ServerLevel level, BlockPos site) {
		MinecraftServer server = level.getServer();
		if (StoneRegistry.released(server, Stone.MIND) || convergence != null) return;
		int needed = remainingCount(server);
		if (needed <= 0) return;

		List<MindZombie> existing = level.getEntitiesOfClass(MindZombie.class,
				new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
		int toSpawn = needed - existing.size();
		for (int i = 0; i < toSpawn; i++) {
			int idx = (existing.size() + i) % SPAWN_OFFSETS.length;
			int sx = site.getX() + SPAWN_OFFSETS[idx][0];
			int sz = site.getZ() + SPAWN_OFFSETS[idx][1];
			int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
			BlockPos spawnPos = new BlockPos(sx, Math.max(site.getY() + 1, sy), sz);
			MindZombie zombie = ModEntities.MIND_ZOMBIE.create(level, EntitySpawnReason.STRUCTURE);
			if (zombie == null) continue;
			zombie.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, (idx * 36.0f) % 360.0f, 0.0f);
			zombie.setHomeTo(site, MindCity.R_FLAT - 2);
			level.addFreshEntity(zombie);
		}
	}

	/** Resets the Mind Stone quest when the Mind Stone is forgotten or stones are reset. */
	public static void reset(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		overworld.removeAttached(DEFEATED);
		convergence = null;
		if (!MindCity.built(server)) return;
		BlockPos site = MindCity.site(server);
		if (overworld.hasChunkAt(site)) {
			for (MindZombie z : overworld.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 32.0), e -> true)) {
				z.discard();
			}
			ensureZombies(overworld, site);
		}
	}

	private MindQuest() {}
}
