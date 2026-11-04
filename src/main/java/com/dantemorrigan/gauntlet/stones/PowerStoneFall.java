package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * How the Power Stone enters a world. Now and then (while it has not been found) a short meteor shower falls
 * some way from a player in the Overworld; nearby players get a notice with the direction. The last meteor is
 * purple: it leaves a purple meteorite with the sealed Orb in its core. Taking the Orb out marks the stone as
 * found and the showers stop for good.
 */
public final class PowerStoneFall {
	private static final int ID_BASE = 2_000_000;
	private static final int PURPLE = 3;

	private record Meteor(int id, Vec3 to, long arrive, int size) {}

	private static final class Shower {
		final BlockPos center;
		final Vec3 sky;
		final long end;
		float accumulator;
		boolean purpleLaunched;

		Shower(BlockPos center, Vec3 sky, long end) { this.center = center; this.sky = sky; this.end = end; }
	}

	private static Shower shower;
	private static final List<Meteor> METEORS = new ArrayList<>();
	private static int nextId = ID_BASE;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PowerStoneFall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { shower = null; METEORS.clear(); });
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!state.is(ModBlocks.POWER_ORB) || !(player instanceof ServerPlayer sp)) return;
			MinecraftServer server = sp.level().getServer();
			StoneRegistry.markFound(server, Stone.POWER);
			server.overworld().removeAttached(StoneRegistry.POWER_SITE);
			// In creative the loot table does not run.
			if (sp.isCreative()) OrbItem.giveIntoBackpack(sp, new ItemStack(ModItems.POWER_ORB));
			ModStatus.send(sp, Component.translatable("msg.infinity_gauntlet.orb.taken"));
		});
	}

	public static boolean active() { return shower != null; }

	/** Center of the running shower, or null. */
	public static BlockPos showerCenter() { return shower == null ? null : shower.center; }

	/** Where the meteorite with the unclaimed Orb lies, or null. */
	public static BlockPos site(MinecraftServer server) { return server.overworld().getAttached(StoneRegistry.POWER_SITE); }

	// ------------------------------------------------------------------ scheduling

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (shower != null || !METEORS.isEmpty()) {
			run(level, now);
			return;
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (!cfg.powerShowerEnabled || StoneRegistry.found(server, Stone.POWER) || level.getAttached(StoneRegistry.POWER_SITE) != null) return;
		Long next = level.getAttached(StoneRegistry.NEXT_SHOWER);
		if (next == null) {
			level.setAttached(StoneRegistry.NEXT_SHOWER, now + cfg.powerShowerFirstMinutes * 1200L);
			return;
		}
		if (now < next) return;
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isSpectator()) candidates.add(p);
		RandomSource rnd = level.getRandom();
		boolean started = !candidates.isEmpty() && start(level, candidates.get(rnd.nextInt(candidates.size())));
		long delay = started ? Mth.nextInt(rnd, cfg.powerShowerMinMinutes, Math.max(cfg.powerShowerMinMinutes, cfg.powerShowerMaxMinutes)) * 1200L : 1200L;
		level.setAttached(StoneRegistry.NEXT_SHOWER, now + delay);
	}

	/** Starts a shower some distance from the player. Returns false when no dry, loaded ground was found. */
	public static boolean start(ServerLevel level, ServerPlayer near) {
		MinecraftServer server = level.getServer();
		if (shower != null || !near.level().dimension().equals(Level.OVERWORLD) || StoneRegistry.found(server, Stone.POWER)
				|| level.getAttached(StoneRegistry.POWER_SITE) != null) return false;
		GauntletConfig cfg = GauntletConfig.get();
		RandomSource rnd = level.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double d = Mth.nextDouble(rnd, cfg.powerShowerMinDistance, cfg.powerShowerMaxDistance);
			BlockPos column = BlockPos.containing(near.getX() + Math.cos(a) * d, 0, near.getZ() + Math.sin(a) * d);
			if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) continue;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getBlockState(top.below()).getFluidState().isEmpty() || top.getY() <= level.getMinY() + 1) continue;
			double azimuth = rnd.nextDouble() * Math.PI * 2, elevation = Math.toRadians(48 + rnd.nextInt(18));
			Vec3 sky = new Vec3(Math.cos(elevation) * Math.cos(azimuth), Math.sin(elevation), Math.cos(elevation) * Math.sin(azimuth));
			shower = new Shower(top, sky, level.getGameTime() + cfg.powerShowerSeconds * 20L);
			level.playSound(null, top, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0F, 0.6F);
			level.playSound(null, near.blockPosition(), com.dantemorrigan.gauntlet.ModSounds.STONE_OMINOUS, SoundSource.AMBIENT, 1.5F, 1.0F);
			for (ServerPlayer p : PlayerLookup.around(level, Vec3.atCenterOf(top), 600)) {
				Component dir = direction(p, top);
				ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.shower.start", dir));
				p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.shower.start_chat", dir));
			}
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the shower

	private static void run(ServerLevel level, long now) {
		GauntletConfig cfg = GauntletConfig.get();
		Shower s = shower;
		if (s != null) {
			long purpleAt = s.end - 80;
			if (now < purpleAt) {
				s.accumulator += cfg.powerShowerMeteorsPerSecond / 20f;
				while (s.accumulator >= 1f) {
					s.accumulator -= 1f;
					launchSmall(level, s);
				}
			} else if (!s.purpleLaunched) {
				s.purpleLaunched = true;
				launch(level, s, Vec3.atBottomCenterOf(s.center), PURPLE, 70);
			}
		}
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor m = it.next();
			if (now < m.arrive()) continue;
			it.remove();
			if (m.size() == PURPLE) landPurple(level, m);
			else impact(level, m);
		}
		if (s != null && s.purpleLaunched && METEORS.isEmpty()) shower = null;
	}

	private static void launchSmall(ServerLevel level, Shower s) {
		RandomSource rnd = level.getRandom();
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2, r = 6 + Math.sqrt(rnd.nextDouble()) * 30;
			BlockPos column = BlockPos.containing(s.center.getX() + Math.cos(a) * r, 0, s.center.getZ() + Math.sin(a) * r);
			if (!level.hasChunkAt(column)) continue;
			Vec3 to = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
			if (tooCloseToPlayer(level, to, 12)) continue;
			launch(level, s, to, rnd.nextFloat() < 0.2f ? 1 : 0, 0);
			return;
		}
	}

	private static void launch(ServerLevel level, Shower s, Vec3 to, int size, int extraFlight) {
		RandomSource rnd = level.getRandom();
		Vec3 from = to.add(s.sky.scale(240 + rnd.nextDouble() * 40)).add(rnd.nextGaussian() * 6, rnd.nextGaussian() * 6, rnd.nextGaussian() * 6);
		int flight = (int) Math.max(24, from.distanceTo(to) / 4.5) + extraFlight;
		Meteor m = new Meteor(nextId++, to, level.getGameTime() + flight, size);
		METEORS.add(m);
		ModNetwork.MeteorSpawn packet = new ModNetwork.MeteorSpawn(m.id(), from, to, flight, size, false);
		for (ServerPlayer p : PlayerLookup.around(level, to, 500)) ServerPlayNetworking.send(p, packet);
	}

	private static boolean tooCloseToPlayer(ServerLevel level, Vec3 pos, double radius) {
		for (Player p : level.players()) if (p.position().distanceToSqr(pos) < radius * radius) return true;
		return false;
	}

	private static void impact(ServerLevel level, Meteor m) {
		BlockPos at = BlockPos.containing(m.to());
		if (!level.hasChunkAt(at)) return;
		BlockState ground = level.getBlockState(at.below());
		float power = 1.3f + m.size() * 0.8f + level.getRandom().nextFloat() * 0.6f;
		level.explode(null, m.to().x, m.to().y + 0.3, m.to().z, power, false, Level.ExplosionInteraction.TNT);
		ModNetwork.MeteorImpact packet = new ModNetwork.MeteorImpact(m.id(), m.to(), m.size(), Block.getId(ground));
		for (ServerPlayer p : PlayerLookup.around(level, m.to(), 250)) ServerPlayNetworking.send(p, packet);
	}

	// ------------------------------------------------------------------ the purple meteorite

	private static void landPurple(ServerLevel level, Meteor m) {
		BlockPos at = BlockPos.containing(m.to());
		if (!level.hasChunkAt(at)) return;
		BlockState ground = level.getBlockState(at.below());
		level.explode(null, m.to().x, m.to().y + 0.5, m.to().z, 4.5f, false, Level.ExplosionInteraction.TNT);
		BlockPos floor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		BlockPos core = buildMeteorite(level, floor);
		level.setAttached(StoneRegistry.POWER_SITE, core);
		level.playSound(null, core, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 8.0F, 0.4F);
		ModNetwork.MeteorImpact packet = new ModNetwork.MeteorImpact(m.id(), m.to(), PURPLE, Block.getId(ground));
		for (ServerPlayer p : PlayerLookup.around(level, m.to(), 300)) ServerPlayNetworking.send(p, packet);
		for (ServerPlayer p : PlayerLookup.around(level, Vec3.atCenterOf(core), 600)) {
			int dist = (int) Math.round(Math.sqrt(p.blockPosition().distSqr(core)));
			ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.shower.purple", dist, direction(p, core)));
			p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.shower.purple_chat", dist, direction(p, core)));
		}
	}

	/** A half-buried lump of purple rock, the Orb in its heart. Returns the Orb position. */
	static BlockPos buildMeteorite(ServerLevel level, BlockPos floor) {
		RandomSource rnd = level.getRandom();
		BlockPos core = floor;
		BlockState rock = ModBlocks.PURPLE_METEORITE.defaultBlockState();
		BlockState hot = ModBlocks.PURPLE_METEORITE_CORE.defaultBlockState();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					double d = dx * dx / 6.2 + dy * dy / 4.4 + dz * dz / 6.2 + (rnd.nextDouble() - 0.5) * 0.35;
					if (d > 1) continue;
					BlockPos p = core.offset(dx, dy, dz);
					if (!replaceable(level, p)) continue;
					level.setBlock(p, d < 0.35 ? hot : rock, Block.UPDATE_ALL);
				}
			}
		}
		level.setBlock(core, ModBlocks.POWER_ORB.defaultBlockState(), Block.UPDATE_ALL);
		// Smouldering rim.
		for (int i = 0; i < 4; i++) {
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
					core.offset((int) Math.round(rnd.nextGaussian() * 5), 0, (int) Math.round(rnd.nextGaussian() * 5)));
			if (level.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
		}
		return core;
	}

	private static boolean replaceable(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return level.hasChunkAt(p) && !s.is(Blocks.BEDROCK) && !s.hasBlockEntity() && s.getDestroySpeed(level, p) >= 0;
	}

	// ------------------------------------------------------------------ text

	/** "north-east" etc. from the player to the target. */
	static Component direction(ServerPlayer p, BlockPos target) {
		double dx = target.getX() + 0.5 - p.getX(), dz = target.getZ() + 0.5 - p.getZ();
		double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360; // 0 = north (-Z), 90 = east (+X)
		String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return Component.translatable("msg.infinity_gauntlet.dir." + names[(int) Math.round(deg / 45) % 8]);
	}

	private PowerStoneFall() {}
}
