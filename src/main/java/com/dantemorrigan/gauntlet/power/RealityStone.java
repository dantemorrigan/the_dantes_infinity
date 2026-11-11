package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import com.dantemorrigan.gauntlet.uru.UruArmor;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reality stone — Reality Inversion ("Изнанка реальности"). Can be performed while moving: the distortion vortex
 * follows the caster. Affected mobs and players turn into red butterflies, Thanos takes damage, and flying projectiles
 * become petals. When the caster finishes using the ability, all twisted ground, buildings and water remain
 * physically twisted in the world forever.
 */
public final class RealityStone {
	public static final String TAG = "infinity_gauntlet_reality";
	public static final int T_CRACKS = 12, T_FOLD = 20, FOLD_TICKS = 20;
	public static final float INNER = 2.5f;

	private static final float THANOS_BASE_DAMAGE = 4f;

	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

	public enum Kind { GROUND, BUILT, WATER }

	public static final class BlockEntry {
		public final BlockPos pos;
		public final BlockState state;
		public final Kind kind;
		public final float seed;
		public Vec3 anchorCenter;
		public float anchorYaw;

		public BlockEntry(BlockPos pos, BlockState state, Kind kind, float seed, Vec3 anchorCenter, float anchorYaw) {
			this.pos = pos;
			this.state = state;
			this.kind = kind;
			this.seed = seed;
			this.anchorCenter = anchorCenter;
			this.anchorYaw = anchorYaw;
		}
	}

	private static final class Session {
		final ServerLevel level;
		Vec3 center;
		float yaw;
		int tick;
		final Map<BlockPos, BlockEntry> entries = new LinkedHashMap<>();
		final Set<Long> capturedColumns = new HashSet<>();
		final Set<UUID> damagedTitans = new HashSet<>();

		Session(ServerLevel level, Vec3 center, float yaw) {
			this.level = level;
			this.center = center;
			this.yaw = yaw;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RealityStone::tick);
		// Runtime state belongs to one server: a singleplayer client that opens another world must start clean.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SESSIONS.clear(); COOLDOWN.clear(); });
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
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.need.reality"));
			return;
		}
		if (SpaceManager.inSpace(player)) return;
		long now = player.level().getGameTime();
		if (now < COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
		Vec3 center = Vec3.atBottomCenterOf(player.blockPosition());
		SESSIONS.put(player.getUUID(), new Session(player.level(), center, player.getYRot()));
		GauntletState.setInUse(g, EnumSet.of(Stone.REALITY));
		broadcast(player.level(), player, true, center, 0);
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	public static void stop(ServerPlayer player) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) return;
		if (s.tick >= T_FOLD && GauntletConfig.get().abilityBlockChanges) {
			commitTwistedBlocks(player, s);
		}
		COOLDOWN.put(player.getUUID(), player.level().getGameTime() + GauntletConfig.get().realityCooldownTicks);
		ItemStack g = GauntletState.activeGauntlet(player);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
		broadcast(s.level, player, false, s.center, s.tick);
		s.level.playSound(null, BlockPos.containing(s.center), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	public static boolean isBuilt(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.WOODEN_STAIRS)
				|| s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.BRICKS) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.SMOOTH_STONE)
				|| s.is(BlockTags.WALLS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.CRAFTING_TABLE);
	}

	public static float seedFor(BlockPos pos) {
		int h = Mth.murmurHash3Mixer(Long.hashCode(pos.asLong()));
		return (h & 0xFFFF) / 655.35f;
	}

	public static float foldProgress(float age) {
		float t = Mth.clamp((age - T_FOLD) / FOLD_TICKS, 0, 1);
		return t * t * (3 - 2 * t);
	}

	public static boolean isDisplaced(BlockPos blockPos, Kind kind, Vec3 anchorCenter, float yawDeg) {
		if (kind != Kind.GROUND) return true;
		float yawRad = yawDeg * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
		Vec3 pos = Vec3.atCenterOf(blockPos);
		Vec3 relXZ = pos.subtract(anchorCenter.x, pos.y, anchorCenter.z);
		return Math.abs(relXZ.dot(right)) > INNER;
	}

	public static Vec3 transformPos(BlockPos blockPos, Kind kind, float seed, float p, Vec3 anchorCenter, float yawDeg, float time, boolean ripple) {
		Vec3 pos = Vec3.atCenterOf(blockPos);
		if (p <= 0) return pos;
		double groundY = anchorCenter.y - 0.5;
		float yawRad = yawDeg * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);

		if (kind == Kind.WATER) {
			double wave = ripple ? Math.sin(time * 0.08 + seed) * 0.6 : Math.sin(seed) * 0.6;
			double lift = p * (2.5 + wave);
			Vec3 rel = pos.subtract(anchorCenter.x, 0, anchorCenter.z);
			float twist = (float) (p * Math.toRadians(28) * Mth.clamp(Math.sqrt(rel.x * rel.x + rel.z * rel.z) / 10.0, 0.15, 1.0));
			Vec3 turned = new Vec3(rel.x * Math.cos(twist) - rel.z * Math.sin(twist), rel.y + lift, rel.x * Math.sin(twist) + rel.z * Math.cos(twist));
			pos = turned.add(anchorCenter.x, 0, anchorCenter.z);
		} else if (kind == Kind.BUILT) {
			double h = Mth.clamp((pos.y - groundY) / 6.0, 0, 1.6);
			float twist = (float) (p * Math.toRadians(40) * h);
			Vec3 rel = pos.subtract(anchorCenter.x, 0, anchorCenter.z);
			Vec3 turned = new Vec3(rel.x * Math.cos(twist) - rel.z * Math.sin(twist), rel.y, rel.x * Math.sin(twist) + rel.z * Math.cos(twist));
			pos = turned.add(anchorCenter.x, 0, anchorCenter.z);
		}
		Vec3 relXZ = pos.subtract(anchorCenter.x, pos.y, anchorCenter.z);
		double a = relXZ.dot(right);
		double s = Math.signum(a);
		double over = Math.abs(a) - INNER;
		if (over > 0 && s != 0) {
			float k = (float) Mth.clamp(over / 7.0, 0, 1);
			float theta = (float) (-s * p * Math.toRadians(88) * (k * k * (3 - 2 * k)));
			Vec3 hinge = new Vec3(anchorCenter.x, groundY, anchorCenter.z).add(right.scale(s * INNER));
			Vector3f v = new Vector3f((float) (pos.x - hinge.x), (float) (pos.y - hinge.y), (float) (pos.z - hinge.z));
			Quaternionf fold = new Quaternionf().rotationAxis(theta, (float) fwd.x, 0, (float) fwd.z);
			fold.transform(v);
			pos = new Vec3(hinge.x + v.x, hinge.y + v.y, hinge.z + v.z);
		}
		if (ripple) {
			pos = pos.add(0, Math.sin(time * 0.1 + seed) * 0.05 * p, 0);
		}
		return pos;
	}

	private static void captureAround(ServerPlayer player, Session s) {
		GauntletConfig cfg = GauntletConfig.get();
		int r = cfg.realityRadius;
		int maxBlocks = cfg.realityMaxBlocks * 3;
		BlockPos c = BlockPos.containing(s.center);
		double rSq = r * r;
		for (BlockEntry entry : s.entries.values()) {
			double dx = entry.pos.getX() + 0.5 - s.center.x;
			double dz = entry.pos.getZ() + 0.5 - s.center.z;
			if (dx * dx + dz * dz <= rSq) {
				entry.anchorCenter = s.center;
				entry.anchorYaw = s.yaw;
			}
		}
		if (s.entries.size() >= maxBlocks) return;
		List<BlockPos> columns = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r || d < INNER) continue;
				BlockPos col = new BlockPos(c.getX() + dx, 0, c.getZ() + dz);
				if (s.capturedColumns.add(BlockPos.asLong(col.getX(), 0, col.getZ()))) {
					columns.add(col);
				}
			}
		}
		columns.sort((a, b) -> Double.compare(
				(a.getX() - c.getX()) * (a.getX() - c.getX()) + (a.getZ() - c.getZ()) * (a.getZ() - c.getZ()),
				(b.getX() - c.getX()) * (b.getX() - c.getX()) + (b.getZ() - c.getZ()) * (b.getZ() - c.getZ())));
		for (BlockPos col : columns) {
			int skin = 0;
			for (int y = c.getY() + 14; y >= c.getY() - 8 && skin < 3; y--) {
				BlockPos p = new BlockPos(col.getX(), y, col.getZ());
				if (!s.level.hasChunkAt(p) || !CoopRules.canEdit(player, s.level, p)) continue;
				BlockState state = s.level.getBlockState(p);
				if (state.isAir()) {
					if (skin > 0) skin++;
					continue;
				}
				if (state.hasBlockEntity() || state.getDestroySpeed(s.level, p) < 0) continue;
				boolean water = state.getFluidState().is(FluidTags.WATER) && state.is(Blocks.WATER);
				Kind kind = water ? Kind.WATER : isBuilt(state) ? Kind.BUILT : Kind.GROUND;
				if (kind == Kind.GROUND && state.isSolidRender() || water) skin++;
				BlockPos imm = p.immutable();
				s.entries.putIfAbsent(imm, new BlockEntry(imm, state, kind, seedFor(imm), s.center, s.yaw));
				if (s.entries.size() >= maxBlocks) return;
			}
		}
	}

	private static void commitTwistedBlocks(ServerPlayer player, Session s) {
		float p = foldProgress(s.tick);
		if (p <= 0.01f) return;
		Map<BlockPos, BlockState> targets = new LinkedHashMap<>();
		Set<BlockPos> cleared = new HashSet<>();
		for (BlockEntry e : s.entries.values()) {
			if (!isDisplaced(e.pos, e.kind, e.anchorCenter, e.anchorYaw)) continue;
			Vec3 transformed = transformPos(e.pos, e.kind, e.seed, p, e.anchorCenter, e.anchorYaw, 0, false);
			BlockPos dest = BlockPos.containing(transformed);
			if (dest.equals(e.pos)) continue;
			if (!s.level.hasChunkAt(e.pos) || !s.level.hasChunkAt(dest)) continue;
			if (!CoopRules.canEdit(player, s.level, e.pos) || !CoopRules.canEdit(player, s.level, dest)) continue;
			cleared.add(e.pos);
			BlockState prev = targets.get(dest);
			if (prev == null || (!prev.isSolidRender() && (e.state.isSolidRender() || e.kind == Kind.WATER))) {
				targets.put(dest, e.state);
			}
		}
		for (BlockPos orig : cleared) {
			if (!targets.containsKey(orig)) {
				s.level.setBlock(orig, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
			}
		}
		for (Map.Entry<BlockPos, BlockState> entry : targets.entrySet()) {
			BlockPos dest = entry.getKey();
			BlockState existing = s.level.getBlockState(dest);
			if (!cleared.contains(dest) && (existing.hasBlockEntity() || existing.getDestroySpeed(s.level, dest) < 0)) continue;
			s.level.setBlock(dest, entry.getValue(), PLACE_FLAGS);
		}
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
				SESSIONS.remove(id);
				continue;
			}
			ItemStack g = GauntletState.activeGauntlet(player);
			if (!player.isAlive() || player.level() != s.level || g.isEmpty() || !GauntletState.inserted(g).contains(Stone.REALITY)
					|| ++s.tick > cfg.realityMaxHoldTicks) {
				stop(player);
				continue;
			}
			Vec3 newCenter = Vec3.atBottomCenterOf(player.blockPosition());
			float newYaw = player.getYRot();
			boolean moved = newCenter.distanceToSqr(s.center) > 0.01 || Math.abs(Mth.wrapDegrees(newYaw - s.yaw)) > 1.5f;
			s.center = newCenter;
			s.yaw = newYaw;
			if (s.tick == T_FOLD || s.tick % 10 == 0 || (moved && s.tick % 2 == 0)) {
				broadcast(s.level, player, true, s.center, s.tick);
			}
			if (s.tick < T_FOLD) continue; // charge + fractures first (ability_timeline.json)
			if (cfg.abilityBlockChanges) {
				captureAround(player, s);
			}
			double r = cfg.realityRadius;
			AABB box = new AABB(s.center, s.center).inflate(r, r * 0.75, r);
			// Scan the vortex for new targets while the caster moves.
			if (s.tick % 3 == 0) {
				for (LivingEntity entity : s.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()
						&& !MindStone.holdsEntity(e) && !SoulStone.isExtracting(e)
						&& !(e instanceof EnderDragon) && !(e instanceof WitherBoss)
						&& !(e instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerReference() != null ? o.getOwnerReference().getUUID() : null))
						&& CoopRules.canAffect(player, e) && e.position().distanceTo(s.center) <= r)) {
					if (entity instanceof Thanos || entity instanceof com.dantemorrigan.gauntlet.boss.Loki || entity instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal) {
						if (!s.damagedTitans.contains(entity.getUUID()) && GauntletDamage.hurt(s.level, entity, player,
								player.damageSources().playerAttack(player), THANOS_BASE_DAMAGE)) {
							s.damagedTitans.add(entity.getUUID());
						}
						continue;
					}
					if (entity instanceof MindZombie zombie && !zombie.defeatedByReality(s.level, player)) continue;
					Vec3 at = entity.position().add(0, entity.getBbHeight() / 2, 0);
					send(s.level, at, new ModNetwork.RealityFx(3, at, new Vec3(entity.getBbWidth(), entity.getBbHeight(), 0)));
					send(s.level, at, new ModNetwork.RealityFx(0, at, Vec3.ZERO));
					s.level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
					if (entity instanceof ServerPlayer victim) {
						GauntletDamage.hurt(s.level, victim, player, player.damageSources().playerAttack(player), Float.MAX_VALUE);
						if (victim.isAlive() && !UruArmor.fullSet(victim)) {
							victim.hurtServer(s.level, s.level.damageSources().genericKill(), Float.MAX_VALUE);
						}
					} else {
						entity.discard();
					}
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
