package com.dantemorrigan.gauntlet.meteor;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Meteor Stream (Power stone): lock the gauntlet on the sun or the moon, then aim an artillery-style circle at the
 * ground and release: for {@link GauntletConfig#streamSeconds} seconds pieces chipped off that body rain on the circle
 * (the body itself is not destroyed). Nothing is trusted from the client: lock, ground point, rules and damage are all
 * decided here. In multiplayer every player near the circle sees it (a danger zone), players are only hurt where
 * {@link CoopRules} allows it, blocks only change where the caster may edit them, and the caster is immune to the stream.
 */
public final class MeteorStream {
	private static final int ID_BASE = 1_000_000;

	private static final class Aim {
		int phase; // 0 = locking on the body, 1 = choosing the ground circle
		String body = "";
		int lock;
		Vec3 center;
	}

	private static final class Stream {
		final UUID owner;
		final String body;
		final Vec3 center;
		final float radius;
		final long end;
		float accumulator;

		Stream(UUID owner, String body, Vec3 center, float radius, long end) {
			this.owner = owner;
			this.body = body;
			this.center = center;
			this.radius = radius;
			this.end = end;
		}
	}

	private record Meteor(int id, Vec3 to, long arrive, int size, boolean solar, UUID owner) {}

	private static final Map<UUID, Aim> AIMS = new HashMap<>();
	private static final List<Stream> STREAMS = new ArrayList<>();
	private static final List<Meteor> METEORS = new ArrayList<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static int nextId = ID_BASE;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MeteorStream::tick);
		// A player who joins (or changes dimension back) while a stream runs sees its circle too.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> announceTo(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			AIMS.remove(p.getUUID());
			// A stream belongs to its caster: it stops when they leave.
			for (Iterator<Stream> it = STREAMS.iterator(); it.hasNext(); ) {
				Stream s = it.next();
				if (s.owner.equals(p.getUUID())) {
					it.remove();
					broadcastEnd(server, s);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {AIMS.clear(); STREAMS.clear(); METEORS.clear(); COOLDOWN.clear();});
	}

	// ------------------------------------------------------------------ public API (AbilityController)

	public static boolean aiming(ServerPlayer p) {
		return AIMS.containsKey(p.getUUID());
	}

	public static int cooldown(ServerPlayer p) {
		Long until = COOLDOWN.get(p.getUUID());
		if (until == null) return 0;
		return (int) Math.max(0, until - p.level().getServer().overworld().getGameTime());
	}

	/** Starts the lock-on. The ability was already validated by the controller. */
	public static void begin(ServerPlayer p) {
		if (AIMS.containsKey(p.getUUID())) return;
		if (STREAMS.size() >= GauntletConfig.get().streamMaxConcurrent) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.too_many"));
			return;
		}
		AIMS.put(p.getUUID(), new Aim());
		ItemStack gauntlet = GauntletState.activeGauntlet(p);
		if (!gauntlet.isEmpty()) GauntletState.setInUse(gauntlet, EnumSet.of(Stone.POWER));
		ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.aim_body"));
	}

	/** Button released: fire at the chosen circle, or drop the aim if nothing was locked. */
	public static void release(ServerPlayer p) {
		Aim aim = AIMS.remove(p.getUUID());
		if (aim == null) return;
		clearGlow(p);
		if (aim.phase != 1) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.lock_lost"));
			return;
		}
		if (aim.center == null) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.no_ground"));
			return;
		}
		fire(p, aim.body, aim.center, false);
	}

	/** Drops the aim without firing (ability changed, menu opened, gauntlet lost...). */
	public static void cancel(ServerPlayer p) {
		if (AIMS.remove(p.getUUID()) != null) {
			clearGlow(p);
			sendAim(p, false, false, Vec3.ZERO);
		}
	}

	/** Debug / command entry: starts a stream at a point without the aiming steps (still obeys the server rules). */
	public static boolean startAt(ServerPlayer caster, String body, Vec3 center) {
		return fire(caster, body, center, true);
	}

	// ------------------------------------------------------------------ firing

	private static boolean fire(ServerPlayer p, String body, Vec3 center, boolean debug) {
		GauntletConfig cfg = GauntletConfig.get();
		MinecraftServer server = p.level().getServer();
		ServerLevel level = server.overworld();
		if (!cfg.allowMeteorStream || !p.level().dimension().equals(Level.OVERWORLD)) return false;
		if (STREAMS.size() >= cfg.streamMaxConcurrent) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.too_many"));
			return false;
		}
		BlockPos at = BlockPos.containing(center);
		if (!level.hasChunkAt(at) || !level.getWorldBorder().isWithinBounds(at)) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.no_ground"));
			return false;
		}
		if (cfg.abilityBlockChanges && !CoopRules.canEdit(p, level, at)) {
			ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.protected"));
			return false;
		}
		long now = level.getGameTime();
		if (!debug && !p.isCreative()) COOLDOWN.put(p.getUUID(), now + cfg.streamCooldownSeconds * 20L);
		Stream s = new Stream(p.getUUID(), body, center, cfg.streamRadius, now + cfg.streamSeconds * 20L);
		STREAMS.add(s);
		ItemStack gauntlet = GauntletState.activeGauntlet(p);
		if (!gauntlet.isEmpty()) GauntletState.setInUse(gauntlet, EnumSet.of(Stone.POWER)); // glows (and can't be extracted) while it rains
		level.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.5F, 0.7F);
		level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 5.0F, 0.5F);
		announce(server, s, cfg.streamSeconds * 20);
		ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.fired", cfg.streamSeconds));
		return true;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		GauntletConfig cfg = GauntletConfig.get();

		for (Iterator<Map.Entry<UUID, Aim>> it = AIMS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Aim> entry = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
			if (p == null || !p.isAlive() || p.isSpectator() || !p.level().dimension().equals(Level.OVERWORLD)
					|| GauntletState.activeGauntlet(p).isEmpty() || !GauntletState.inserted(GauntletState.activeGauntlet(p)).contains(Stone.POWER)) {
				it.remove();
				if (p != null) {
					clearGlow(p);
					sendAim(p, false, false, Vec3.ZERO);
				}
				continue;
			}
			aimTick(p, entry.getValue(), cfg);
		}

		for (Iterator<Stream> it = STREAMS.iterator(); it.hasNext(); ) {
			Stream s = it.next();
			if (now >= s.end) {
				it.remove();
				ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
				if (owner != null && !AIMS.containsKey(owner.getUUID())) clearGlow(owner);
				broadcastEnd(server, s);
				continue;
			}
			if (now % 100 == 0) announce(server, s, (int) (s.end - now)); // players who came close later learn about the circle
			s.accumulator += cfg.streamMeteorsPerSecond / 20f;
			while (s.accumulator >= 1f) {
				s.accumulator -= 1f;
				if (METEORS.size() < cfg.streamMaxActiveMeteors) launch(level, s);
			}
		}

		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor m = it.next();
			if (now >= m.arrive()) {
				it.remove();
				impact(server, level, m);
			}
		}
	}

	private static void aimTick(ServerPlayer p, Aim aim, GauntletConfig cfg) {
		if (aim.phase == 0) {
			String body = CelestialApocalypse.aimedBody(p);
			if (body == null) {
				aim.lock = Math.max(0, aim.lock - 2);
				if (p.tickCount % 20 == 0) ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.aim_body"));
				return;
			}
			aim.lock++;
			aim.body = body;
			if (aim.lock % 5 == 0) {
				ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.locking", Component.translatable("meteor.infinity_gauntlet." + body),
						Math.min(100, aim.lock * 100 / Math.max(1, cfg.streamLockTicks))));
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.6F + aim.lock / (float) cfg.streamLockTicks);
			}
			if (aim.lock >= cfg.streamLockTicks) {
				aim.phase = 1;
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
				ModStatus.send(p, Component.translatable("meteor.infinity_gauntlet.aim_ground", Component.translatable("meteor.infinity_gauntlet." + body)));
			}
			return;
		}
		// Phase 1: the circle follows where the player looks on the ground.
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(cfg.streamMaxRange));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 center = null;
		if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP && hit.getLocation().distanceTo(p.position()) >= cfg.streamMinRange
				&& p.level().getWorldBorder().isWithinBounds(hit.getBlockPos()) && (!cfg.abilityBlockChanges || CoopRules.canEdit(p, p.level(), hit.getBlockPos()))) {
			center = hit.getLocation();
		}
		aim.center = center;
		if (p.tickCount % 4 == 0) sendAim(p, true, center != null, center == null ? Vec3.ZERO : center);
	}

	private static void launch(ServerLevel level, Stream s) {
		RandomSource rnd = level.getRandom();
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(rnd.nextDouble()) * s.radius;
			BlockPos column = BlockPos.containing(s.center.x + Math.cos(a) * r, s.center.y, s.center.z + Math.sin(a) * r);
			if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) continue;
			Vec3 to = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
			// They come down the line from the body: its direction right now, kept steep enough to hit the ground.
			Vec3 dir = CelestialApocalypse.bodyDirection(level, s.body);
			if (dir.y < 0.3) dir = new Vec3(dir.x, 0.3, dir.z).normalize();
			Vec3 from = to.add(dir.scale(260 + rnd.nextDouble() * 60)).add(rnd.nextGaussian() * 5, rnd.nextGaussian() * 5, rnd.nextGaussian() * 5);
			int flight = (int) Math.max(20, from.distanceTo(to) / 4.5);
			int size = rnd.nextFloat() < 0.1f ? 2 : rnd.nextFloat() < 0.45f ? 1 : 0;
			boolean solar = s.body.equals("sun");
			Meteor m = new Meteor(nextId++, to, level.getGameTime() + flight, size, solar, s.owner);
			METEORS.add(m);
			ModNetwork.MeteorSpawn packet = new ModNetwork.MeteorSpawn(m.id(), from, to, flight, size, solar);
			for (ServerPlayer p : PlayerLookup.around(level, to, 400)) ServerPlayNetworking.send(p, packet);
			return;
		}
	}

	private static void impact(MinecraftServer server, ServerLevel level, Meteor m) {
		GauntletConfig cfg = GauntletConfig.get();
		BlockPos at = BlockPos.containing(m.to());
		if (!level.hasChunkAt(at)) return;
		RandomSource rnd = level.getRandom();
		ServerPlayer owner = server.getPlayerList().getPlayer(m.owner());
		BlockState ground = level.getBlockState(at.below());
		float power = Mth.lerp((m.size() + rnd.nextFloat()) / 3f, cfg.streamCraterPowerMin, cfg.streamCraterPowerMax);
		boolean blocks = cfg.abilityBlockChanges && owner != null;
		ExplosionDamageCalculator rules = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state, float power) {
				return blocks && CoopRules.canEdit(owner, level, pos);
			}

            @Override
            public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
                float damage = super.getEntityDamageAmount(explosion, entity, exposure);
                if (damage > 0 && entity instanceof ServerPlayer player
                        && com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guarded(player)) {
                    com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guardFlash(player, owner);
                    return damage * .36f;
                }
                return damage;
            }

			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				if (owner != null && entity == owner) return false; // the caster is immune to their own stream
				if (entity instanceof Player) return owner != null && entity instanceof ServerPlayer victim && CoopRules.canAffect(owner, victim);
				return true;
			}
		};
		level.explode(null, level.damageSources().explosion(null, owner), rules, m.to().x, m.to().y + 0.5, m.to().z, power, false,
				blocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
		// A few fires on the rim, only where the caster may edit.
		int radius = Math.round(power) + 2;
		for (int i = 0; blocks && i < 3 + m.size() * 2; i++) {
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
					BlockPos.containing(m.to().x + rnd.nextGaussian() * radius, m.to().y, m.to().z + rnd.nextGaussian() * radius));
			if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, Direction.UP) && CoopRules.canEdit(owner, level, p)) {
				level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
			}
		}
		ModNetwork.MeteorImpact packet = new ModNetwork.MeteorImpact(m.id(), m.to(), m.size(), Block.getId(ground));
		for (ServerPlayer p : PlayerLookup.around(level, m.to(), 200)) ServerPlayNetworking.send(p, packet);
	}

	// ------------------------------------------------------------------ sync

	private static void sendAim(ServerPlayer p, boolean active, boolean valid, Vec3 center) {
		ServerPlayNetworking.send(p, new ModNetwork.MeteorStreamFx(active ? 0 : 3, p.getId(), false, center, GauntletConfig.get().streamRadius, 0, valid));
	}

	private static void announce(MinecraftServer server, Stream s, int ticksLeft) {
		ServerLevel level = server.overworld();
		ModNetwork.MeteorStreamFx fx = new ModNetwork.MeteorStreamFx(1, -1, s.body.equals("sun"), s.center, s.radius, ticksLeft, true);
		for (ServerPlayer p : PlayerLookup.around(level, s.center, 600)) ServerPlayNetworking.send(p, fx);
		ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
		if (owner != null) ServerPlayNetworking.send(owner, fx);
	}

	private static void announceTo(ServerPlayer p) {
		ServerLevel level = p.level().getServer().overworld();
		long now = level.getGameTime();
		for (Stream s : STREAMS) {
			if (p.level() != level) continue;
			ServerPlayNetworking.send(p, new ModNetwork.MeteorStreamFx(1, -1, s.body.equals("sun"), s.center, s.radius, (int) Math.max(1, s.end - now), true));
		}
	}

	private static void broadcastEnd(MinecraftServer server, Stream s) {
		ModNetwork.MeteorStreamFx fx = new ModNetwork.MeteorStreamFx(2, -1, s.body.equals("sun"), s.center, s.radius, 0, false);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(p, fx);
	}

	private static void clearGlow(ServerPlayer p) {
		ItemStack g = GauntletState.activeGauntlet(p);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
	}

	private MeteorStream() {}
}
