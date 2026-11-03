package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reality stone — Reality Inversion ("Изнанка реальности"). While the fist is closed (key held) the world around
 * the caster turns inside out: the terrain folds into walls and buildings twist (client-side, real BlockStates,
 * nothing in the world changes), mobs become red butterflies, flying projectiles become petals. Opening the fist
 * puts everything back. The server owns the mob/projectile part and always restores it (release, death,
 * disconnect, dimension change, server restart via an entity tag).
 */
public final class RealityStone {
	public static final String TAG = "infinity_gauntlet_reality";
	private static final String ORIGINAL_FLAGS = TAG + "_flags_";

	private static final class Session {
		final Vec3 center;
		final ServerLevel level;
		int tick;
		final List<UUID> mobs = new ArrayList<>();

		Session(ServerLevel level, Vec3 center) {
			this.level = level;
			this.center = center;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RealityStone::tick);
		// Safety net: a mob left transformed by a crash/restart is restored as soon as it loads.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.entityTags().contains(TAG) && SESSIONS.values().stream().noneMatch(s -> s.mobs.contains(mob.getUUID()))) {
				restoreMob(mob);
			}
		});
	}

	public static boolean active(ServerPlayer p) {
		return SESSIONS.containsKey(p.getUUID());
	}

	public static void hold(ServerPlayer player, boolean closed) {
		if (closed) start(player);
		else stop(player);
	}

	private static void start(ServerPlayer player) {
		if (active(player)) return;
		ItemStack g = GauntletState.activeGauntlet(player);
		if (g.isEmpty() || !GauntletState.inserted(g).contains(Stone.REALITY)) {
			ModStatus.send(player, Component.literal("Нужна перчатка с Камнем Реальности в левой руке"));
			return;
		}
		if (SpaceManager.inSpace(player)) return;
		long now = player.level().getGameTime();
		if (now < COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
		Vec3 center = Vec3.atBottomCenterOf(player.blockPosition());
		SESSIONS.put(player.getUUID(), new Session(player.level(), center));
		GauntletState.setInUse(g, EnumSet.of(Stone.REALITY));
		broadcast(player.level(), player, true, center, 0);
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	public static void stop(ServerPlayer player) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) return;
		for (UUID id : s.mobs) {
			Entity e = s.level.getEntity(id);
			if (e instanceof Mob mob) {
				restoreMob(mob);
				send(s.level, s.center, new ModNetwork.RealityFx(2, mob.position().add(0, mob.getBbHeight() / 2, 0), Vec3.ZERO));
			}
		}
		COOLDOWN.put(player.getUUID(), player.level().getGameTime() + GauntletConfig.get().realityCooldownTicks);
		ItemStack g = GauntletState.activeGauntlet(player);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
		broadcast(s.level, player, false, s.center, s.tick);
		s.level.playSound(null, BlockPos.containing(s.center), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	private static void restoreMob(Mob mob) {
		mob.removeTag(TAG);
		int flags = 0;
		for (int mask = 0; mask < 16; mask++) {
			if (mob.removeTag(ORIGINAL_FLAGS + mask)) flags = mask;
		}
		mob.setInvisible((flags & 1) != 0);
		mob.setNoAi((flags & 2) != 0);
		mob.setInvulnerable((flags & 4) != 0);
		mob.setNoGravity((flags & 8) != 0);
	}

	private static void broadcast(ServerLevel level, ServerPlayer caster, boolean active, Vec3 center, int age) {
		ModNetwork.RealityState state = new ModNetwork.RealityState(caster.getId(), active, center, age, caster.getYRot());
		for (ServerPlayer p : PlayerLookup.around(level, center, 128)) ServerPlayNetworking.send(p, state);
	}

	private static void send(ServerLevel level, Vec3 at, ModNetwork.RealityFx fx) {
		for (ServerPlayer p : PlayerLookup.around(level, at, 128)) ServerPlayNetworking.send(p, fx);
	}

	private static void tick(MinecraftServer server) {
		GauntletConfig cfg = GauntletConfig.get();
		for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
			Session s = SESSIONS.get(id);
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				// Disconnected: restore without a player.
				SESSIONS.remove(id);
				for (UUID m : s.mobs) if (s.level.getEntity(m) instanceof Mob mob) restoreMob(mob);
				continue;
			}
			ItemStack g = GauntletState.activeGauntlet(player);
			if (!player.isAlive() || player.level() != s.level || g.isEmpty() || !GauntletState.inserted(g).contains(Stone.REALITY)
					|| ++s.tick > cfg.realityMaxHoldTicks) {
				stop(player);
				continue;
			}
			if (s.tick % 20 == 0) broadcast(s.level, player, true, s.center, s.tick);
			if (s.tick < 20) continue; // charge + fractures first (ability_timeline.json)
			double r = cfg.realityRadius;
			AABB box = new AABB(s.center, s.center).inflate(r, r * 0.75, r);
			// Mobs -> butterflies (bosses are too real to rewrite).
			if (s.tick % 5 == 0) {
				for (Mob mob : s.level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !m.entityTags().contains(TAG)
						&& !MindStone.holdsEntity(m) && !SoulStone.isExtracting(m)
						&& !(m instanceof EnderDragon) && !(m instanceof WitherBoss) && m.position().distanceTo(s.center) <= r)) {
					int flags = (mob.isInvisible() ? 1 : 0) | (mob.isNoAi() ? 2 : 0)
							| (mob.isInvulnerable() ? 4 : 0) | (mob.isNoGravity() ? 8 : 0);
					mob.addTag(ORIGINAL_FLAGS + flags);
					mob.addTag(TAG);
					mob.setInvisible(true);
					mob.setNoAi(true);
					mob.setInvulnerable(true);
					mob.setNoGravity(true);
					mob.setDeltaMovement(Vec3.ZERO);
					s.mobs.add(mob.getUUID());
					send(s.level, s.center, new ModNetwork.RealityFx(0, mob.position().add(0, mob.getBbHeight() / 2, 0), Vec3.ZERO));
				}
			}
			// Keep the butterflies alive over their hidden mobs.
			if (s.tick % 40 == 0) {
				for (UUID m : s.mobs) {
					if (s.level.getEntity(m) instanceof Mob mob) send(s.level, s.center, new ModNetwork.RealityFx(0, mob.position().add(0, mob.getBbHeight() / 2, 0), Vec3.ZERO));
				}
			}
			// Projectiles in flight -> petals.
			for (Projectile proj : s.level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && p.position().distanceTo(s.center) <= r)) {
				Vec3 v = proj.getDeltaMovement();
				if (v.lengthSqr() < 0.01) continue; // already stuck in the ground
				send(s.level, s.center, new ModNetwork.RealityFx(1, proj.position(), v));
				proj.discard();
			}
		}
	}

	private RealityStone() {}
    public static int cooldown(ServerPlayer p) { return (int)Math.max(0, COOLDOWN.getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }

}
