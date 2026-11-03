package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reality stone — short tap: the fist hits the ground. A red wave rolls out: every mob it reaches turns into red
 * butterflies that dissolve in the air (the mob is gone, no drops), and the ground becomes redstone blocks with
 * fire. Unlike the Inversion (hold), this one is real and permanent.
 */
public final class RealityStrike {
	private static final double WAVE_SPEED = 0.9; // blocks per tick

	private static final double LOOSE_RADIUS = 4;
	private static final float LOOSE_DAMAGE = 4;

	private record Column(BlockPos ground, double dist, boolean fire) {}

	private static final class Wave {
		final ServerLevel level;
		final Vec3 center;
		final UUID caster;
		final List<Column> columns;
		final List<Mob> mobs;
		final boolean loose;
		int tick;

		Wave(ServerLevel level, Vec3 center, UUID caster, List<Column> columns, List<Mob> mobs, boolean loose) {
			this.level = level;
			this.center = center;
			this.caster = caster;
			this.columns = columns;
			this.mobs = mobs;
			this.loose = loose;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RealityStrike::tick);
	}

	public static void strike(ServerPlayer player) {
		ItemStack g = GauntletState.activeGauntlet(player);
		if (g.isEmpty() || !GauntletState.inserted(g).contains(Stone.REALITY)) {
			ModStatus.send(player, Component.literal("Нужна перчатка с Камнем Реальности в левой руке"));
			return;
		}
		if (SpaceManager.inSpace(player) || RealityStone.active(player)) return;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
		GauntletConfig cfg = GauntletConfig.get();
		COOLDOWN.put(player.getUUID(), now + cfg.realityStrikeCooldownTicks);

		double r = cfg.realityStrikeRadius;
		Vec3 center = player.position();
		BlockPos c = player.blockPosition();
		List<Column> columns = groundColumns(level, c, r, true);

		WAVES.add(new Wave(level, center, player.getUUID(), columns, targets(player, center, r, 1), false));
		player.swing(InteractionHand.OFF_HAND, true);
		GauntletState.setInUse(g, EnumSet.of(Stone.REALITY));
		send(level, center, new ModNetwork.RealityFx(4, center, new Vec3(r, 0, 0)));
		level.playSound(null, c, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.55F);
		level.playSound(null, c, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	public static boolean strikeLoose(ServerPlayer player) {
		if (SpaceManager.inSpace(player) || player.isSpectator()) return false;
		ServerLevel level = player.level();
		Vec3 center = player.position();
		WAVES.add(new Wave(level, center, player.getUUID(), groundColumns(level, player.blockPosition(), LOOSE_RADIUS, false),
				targets(player, center, LOOSE_RADIUS, 0), true));
		send(level, center, new ModNetwork.RealityFx(4, center, new Vec3(LOOSE_RADIUS, 0, 0)));
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.85F);
		return true;
	}

	private static List<Column> groundColumns(ServerLevel level, BlockPos c, double r, boolean fire) {
		var rnd = level.getRandom();

		// Ground: the top solid block of every column in an uneven circle (no block entities, nothing unbreakable).
		List<Column> columns = new ArrayList<>();
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r - rnd.nextDouble() * 2.0) continue; // ragged edge
				int x = c.getX() + dx, z = c.getZ() + dz;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				if (Math.abs(top - c.getY()) > 7) continue;
				BlockPos ground = new BlockPos(x, top, z);
				BlockState state = level.getBlockState(ground);
				if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, ground) < 0
						|| !state.isCollisionShapeFullBlock(level, ground) || state.is(Blocks.REDSTONE_BLOCK)) continue;
				columns.add(new Column(ground, d, fire && d > 2.2 && rnd.nextFloat() < 0.22f));
			}
		}
		columns.sort(Comparator.comparingDouble(Column::dist));
		return columns;
	}

	private static List<Mob> targets(ServerPlayer player, Vec3 center, double r, double reachPadding) {
		ServerLevel level = player.level();
		// Everyone around (bosses are too real; your own pets and named mobs are spared).
		List<Mob> mobs = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(r, r * 0.6, r), m -> m.isAlive()
				&& !(m instanceof EnderDragon) && !(m instanceof WitherBoss) && !m.hasCustomName()
				&& !(m instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerReference() != null ? o.getOwnerReference().getUUID() : null))
				&& m.position().distanceTo(center) <= r + reachPadding));
		mobs.sort(Comparator.comparingDouble(m -> m.position().distanceToSqr(center)));

		return mobs;
	}

	private static void send(ServerLevel level, Vec3 at, ModNetwork.RealityFx fx) {
		for (ServerPlayer p : PlayerLookup.around(level, at, 160)) ServerPlayNetworking.send(p, fx);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(w.caster);
			if (owner == null || !owner.isAlive() || owner.level() != w.level) {
				it.remove();
				continue;
			}
			double front = ++w.tick * WAVE_SPEED;
			// Ground turns red as the wave passes.
			int changed = 0;
			while (!w.columns.isEmpty() && w.columns.getFirst().dist <= front) {
				Column col = w.columns.removeFirst();
				if (!CoopRules.canEdit(owner, w.level, col.ground)) continue;
				BlockState now = w.level.getBlockState(col.ground);
				if (now.isAir() || now.hasBlockEntity() || now.getDestroySpeed(w.level, col.ground) < 0) continue;
				w.level.setBlock(col.ground, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
				BlockPos above = col.ground.above();
				if (col.fire && CoopRules.canEdit(owner, w.level, above) && w.level.getBlockState(above).canBeReplaced() && w.level.getFluidState(above).isEmpty()) {
					w.level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
				}
				if (changed++ % 3 == 0) {
					w.level.sendParticles(ParticleTypes.FLAME, col.ground.getX() + 0.5, col.ground.getY() + 1.05, col.ground.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.02);
				}
				if (changed % 7 == 0) w.level.sendParticles(ParticleTypes.LAVA, col.ground.getX() + 0.5, col.ground.getY() + 1.0, col.ground.getZ() + 0.5, 1, 0.2, 0, 0.2, 0);
			}
			if (changed > 0 && w.tick % 3 == 0) {
				w.level.playSound(null, BlockPos.containing(w.center), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.6F + w.tick * 0.03F);
			}
			if (w.loose) {
				ServerPlayer caster = server.getPlayerList().getPlayer(w.caster);
				if (caster == null || caster.level() != w.level || front > LOOSE_RADIUS + WAVE_SPEED) {
					it.remove();
					continue;
				}
				w.mobs.removeIf(mob -> {
					double distance = mob.position().distanceTo(w.center);
					if (!mob.isAlive() || mob.level() != w.level || distance > LOOSE_RADIUS) return true;
					if (distance > front) return false;
					com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(w.level, mob, caster, caster.damageSources().playerAttack(caster), LOOSE_DAMAGE);
					return true;
				});
				if (w.mobs.isEmpty() && w.columns.isEmpty()) it.remove();
				continue;
			}
			// Mobs the wave reaches become red butterflies and dissolve.
			while (!w.mobs.isEmpty() && w.mobs.getFirst().position().distanceTo(w.center) <= front + 0.5) {
				Mob mob = w.mobs.removeFirst();
				if (!mob.isAlive() || mob.level() != w.level) continue;
				Vec3 at = mob.position().add(0, mob.getBbHeight() / 2, 0);
				send(w.level, at, new ModNetwork.RealityFx(3, at, new Vec3(mob.getBbWidth(), mob.getBbHeight(), 0)));
				w.level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
				mob.discard();
			}
			if (w.columns.isEmpty() && w.mobs.isEmpty()) {
				it.remove();
				ServerPlayer caster = server.getPlayerList().getPlayer(w.caster);
				if (caster != null && !RealityStone.active(caster)) {
					ItemStack g = GauntletState.activeGauntlet(caster);
					if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
				}
			}
		}
	}

	private RealityStrike() {}
    public static int cooldown(ServerPlayer p) { return (int)Math.max(0, COOLDOWN.getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }

}
