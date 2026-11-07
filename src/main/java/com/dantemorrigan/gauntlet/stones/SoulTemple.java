package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Temple of Soul: a lone snowy mountain in a flat snow waste, a stairway winding to the top, ruins and fences on
 * the way, and on the summit a ruined temple with a chasm. The site is chosen once per world (far from spawn); the
 * terrain is reshaped chunk by chunk as chunks load (deterministic per column, so there are no seams), and the
 * stairway and temple are built once the mountain chunks are done.
 */
public final class SoulTemple {
	/** Mountain base radius, snow waste radius, blend-back radius, summit plateau radius, summit height above the waste. */
	static final int R_M = 50, R_D = 118, R_B = 140, R_TOP = 11, RISE = 64;
	private static final double TURNS = 2.5;

	/** Center of the site; y = height of the snow waste. */
	private static final AttachmentType<BlockPos> SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("soul_temple_site"));
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("soul_temple_built"));
	private static final ChunkShaper SHAPER = new ChunkShaper("soul_temple_shaped", SITE, R_B, 4, SoulTemple::shapeChunk);


	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(SoulTemple::site);
		ServerTickEvents.END_SERVER_TICK.register(SoulTemple::tick);
	}

	// ------------------------------------------------------------------ site

	/** The temple site, chosen on first start: 1000-1600 blocks from spawn, on dry land. */
	public static BlockPos site(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = level.getAttached(SITE);
		if (site != null) return site;
		RandomSource rnd = RandomSource.create(level.getSeed() ^ 0x50171E1L);
		BlockPos spawn = level.getRespawnData().pos();
		var gen = level.getChunkSource().getGenerator();
		var state = level.getChunkSource().randomState();
		BlockPos best = null;
		for (int i = 0; i < 24 && best == null; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 1000 + rnd.nextDouble() * 600;
			int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
			int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state);
			if (h > level.getSeaLevel() + 3 && h < 130) best = new BlockPos(x, Mth.clamp(h, 68, 110), z);
		}
		if (best == null) best = new BlockPos(spawn.getX() + 1200, 80, spawn.getZ());
		level.setAttached(SITE, best);
		return best;
	}

	/** Moves the site (debug command): the next chunks around it get reshaped. */
	public static void relocate(MinecraftServer server, BlockPos site) {
		ServerLevel level = server.overworld();
		level.setAttached(SITE, site);
		SHAPER.reset(level);
		level.removeAttached(BUILT);
	}

	public static int summitY(BlockPos site) { return site.getY() + RISE; }

	public static boolean built(MinecraftServer server) { return Boolean.TRUE.equals(server.overworld().getAttached(BUILT)); }

	/** The chasm's open column on the summit (where a sacrificed wolf falls). */
	public static AABB chasm(BlockPos site) {
		double[] dir = chasmDir();
		Vec3 a = new Vec3(site.getX() + 0.5 + dir[0] * 4, site.getY() - 20, site.getZ() + 0.5 + dir[1] * 4);
		Vec3 b = new Vec3(site.getX() + 0.5 + dir[0] * (R_TOP + 6), summitY(site) - 3, site.getZ() + 0.5 + dir[1] * (R_TOP + 6));
		return new AABB(a, b).inflate(3, 0, 3);
	}

	/** Where to stand on the summit (return point from the soul world). */
	public static Vec3 summitStand(BlockPos site) {
		double[] d = chasmDir();
		return new Vec3(site.getX() + 0.5 - d[0] * 4, summitY(site) + 1, site.getZ() + 0.5 - d[1] * 4);
	}

	/** Foot of the mountain stairway on the snow waste. */
	public static Vec3 stairwayStart(BlockPos site) {
		return new Vec3(site.getX() + R_M - 1.5, site.getY() + 1, site.getZ() - 2.5);
	}

	/** The chasm faces away from where the stairway arrives. */
	private static double[] chasmDir() {
		double end = TURNS * Math.PI * 2 + Math.PI;
		return new double[]{Math.cos(end), Math.sin(end)};
	}

	// ------------------------------------------------------------------ chunk shaping

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = level.getAttached(SITE);
		if (site == null) return;
		SHAPER.drain(level, site);
		if (level.getGameTime() % 40 == 0 && !built(server)) {
			SHAPER.rescan(level, site);
			boolean ready = SHAPER.allShaped(level, mountainChunks(site));
			if (ready) build(level, site);
		}
	}

	private static List<ChunkPos> mountainChunks(BlockPos site) {
		List<ChunkPos> out = new ArrayList<>();
		int minX = (site.getX() - R_M) >> 4, maxX = (site.getX() + R_M) >> 4;
		int minZ = (site.getZ() - R_M) >> 4, maxZ = (site.getZ() + R_M) >> 4;
		for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) out.add(new ChunkPos(x, z));
		return out;
	}

	/** Shapes the whole mountain now (debug command): loads and reshapes its chunks, then builds the temple. */
	public static void forceBuild(ServerLevel level) {
		BlockPos site = site(level.getServer());
		for (ChunkPos cp : mountainChunks(site)) {
			level.getChunk(cp.x(), cp.z());
			if (!SHAPER.isShaped(level, cp)) shapeChunk(level, site, cp);
		}
		build(level, site);
	}

	private static double noise(int x, int z) {
		return Math.sin(x * 0.21 + z * 0.07) * 1.2 + Math.cos(z * 0.17 - x * 0.11) * 1.0 + Math.sin((x + z) * 0.05) * 0.8;
	}

	/** Target surface height of a column. */
	static int target(BlockPos site, int x, int z, int ground) {
		double d = Math.sqrt(Math.pow(x - site.getX(), 2) + Math.pow(z - site.getZ(), 2));
		int base = site.getY();
		if (d <= R_TOP) return base + RISE;
		if (d < R_M) return (int) Math.round(base + RISE * (1 - (d - R_TOP) / (R_M - R_TOP)) + noise(x, z) * Math.min(1, (R_M - d) / 6));
		if (d <= R_D) return base;
		double t = Mth.clamp((d - R_D) / (R_B - R_D), 0, 1);
		t = t * t * (3 - 2 * t);
		return (int) Math.round(Mth.lerp(t, base, ground));
	}

	private static void shapeChunk(ServerLevel level, BlockPos site, ChunkPos cp) {
		BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), snow = Blocks.SNOW_BLOCK.defaultBlockState(),
				packed = Blocks.PACKED_ICE.defaultBlockState(), andesite = Blocks.ANDESITE.defaultBlockState(), powder = Blocks.SNOW.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
			for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
				double d = Math.sqrt(Math.pow(x - site.getX(), 2) + Math.pow(z - site.getZ(), 2));
				if (d > R_B) continue;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				int t = target(site, x, z, ground);
				boolean mountain = d < R_M, blendEdge = d > R_D + (R_B - R_D) * 0.6;
				int from = Math.min(t, ground) - (mountain ? 4 : 3);
				int to = Math.max(top, t) + 1;
				long h = (x * 341873128712L + z * 132897987541L);
				for (int y = from; y <= to; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s;
					if (y > t) s = air;
					else if (blendEdge && y <= ground) continue;
					else if (y == t) s = mountain ? ((h >> 3 & 7) == 0 ? packed : snow) : snow;
					else if (y > t - 3) s = mountain ? ((h >> 5 & 3) == 0 ? andesite : snow) : snow;
					else s = stone;
					if (level.getBlockState(p) != s) level.setBlock(p, s, flags);
				}
				// A thin crust of loose snow on the waste.
				if (!mountain && !blendEdge && (h >> 7 & 3) == 0) level.setBlock(new BlockPos(x, t + 1, z), powder.setValue(SnowLayerBlock.LAYERS, 1 + (int) (h >> 9 & 1)), flags);
			}
		}
		SHAPER.markShaped(level, cp);
	}

	// ------------------------------------------------------------------ stairway, ruins, temple

	private static void build(ServerLevel level, BlockPos site) {
		if (Boolean.TRUE.equals(level.getAttached(BUILT))) return;
		RandomSource rnd = RandomSource.create(site.asLong());
		buildStairway(level, site, rnd);
		buildSummit(level, site, rnd);
		level.setAttached(BUILT, true);
		InfinityGauntlet.LOGGER.info("Temple of Soul built at {}", site);
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, Block.UPDATE_ALL); }

	private static void buildStairway(ServerLevel level, BlockPos site, RandomSource rnd) {
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState(), cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), fence = Blocks.SPRUCE_FENCE.defaultBlockState(),
				lantern = Blocks.LANTERN.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		double r0 = R_M - 2, r1 = R_TOP - 0.5, total = TURNS * Math.PI * 2;
		double length = 0;
		for (double a = 0; a < total; a += 0.01) length += 0.01 * Mth.lerp(a / total, r0, r1);
		Map<Long, Integer> height = new HashMap<>();
		Map<Long, Direction> facing = new HashMap<>();
		List<long[]> order = new ArrayList<>();
		double steps = length * 2;
		for (int i = 0; i <= steps; i++) {
			double s = i / steps, a = s * total, r = Mth.lerp(s, r0, r1);
			double cx = site.getX() + 0.5 + Math.cos(a) * r, cz = site.getZ() + 0.5 + Math.sin(a) * r;
			double tx = -Math.sin(a), tz = Math.cos(a); // tangent (counter-clockwise)
			int y = (int) Math.floor(site.getY() + RISE * s);
			Direction dir = Direction.getApproximateNearest(tx, 0, tz);
			for (int o = -1; o <= 1; o++) {
				int x = (int) Math.floor(cx + Math.cos(a) * o), z = (int) Math.floor(cz + Math.sin(a) * o);
				long key = BlockPos.asLong(x, 0, z);
				if (!height.containsKey(key)) order.add(new long[]{key, i});
				height.merge(key, y, Math::max);
				facing.put(key, dir);
			}
			// Ruins on the inner side now and then.
			if (i % 70 == 35) {
				int px = (int) Math.floor(cx - Math.cos(a) * 2.6), pz = (int) Math.floor(cz - Math.sin(a) * 2.6);
				int hgt = 2 + rnd.nextInt(4);
				for (int k = 1; k <= hgt; k++) set(level, new BlockPos(px, y + k, pz), rnd.nextFloat() < 0.3f ? cracked : rnd.nextFloat() < 0.2f ? mossy : bricks);
			}
		}
		Map<Integer, Integer> lane = new HashMap<>();
		for (long[] e : order) {
			long key = e[0];
			int x = BlockPos.getX(key), z = BlockPos.getZ(key), y = height.get(key);
			Direction dir = facing.get(key);
			for (int k = 1; k <= 4; k++) {
				BlockPos p = new BlockPos(x, y + k, z);
				BlockState cur = level.getBlockState(p);
				if (!cur.is(Blocks.SPRUCE_FENCE) && !cur.is(Blocks.LANTERN)) set(level, p, air);
			}
			for (int k = 1; k <= 3; k++) {
				BlockPos p = new BlockPos(x, y - k, z);
				if (level.getBlockState(p).isAir()) set(level, p, bricks);
			}
			// Behind: if the cell before was lower, this cell is a stair.
			BlockPos behind = new BlockPos(x, 0, z).relative(dir.getOpposite());
			Integer hb = height.get(BlockPos.asLong(behind.getX(), 0, behind.getZ()));
			BlockState top = rnd.nextFloat() < 0.15f ? cracked : rnd.nextFloat() < 0.1f ? mossy : bricks;
			if (hb != null && hb < y) top = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dir);
			set(level, new BlockPos(x, y, z), top);
		}
		// Fence: a continuous line on the outer edge of the stairway, one block above each edge step.
		Map<Long, Integer> fenceAt = new HashMap<>();
		List<Long> fenceOrder = new ArrayList<>();
		for (long[] e : order) {
			long key = e[0];
			int x = BlockPos.getX(key), z = BlockPos.getZ(key), y = height.get(key);
			double d = Math.hypot(x + 0.5 - site.getX() - 0.5, z + 0.5 - site.getZ() - 0.5);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = x + side.getStepX(), nz = z + side.getStepZ();
				long nk = BlockPos.asLong(nx, 0, nz);
				if (height.containsKey(nk) || Math.hypot(nx + 0.5 - site.getX() - 0.5, nz + 0.5 - site.getZ() - 0.5) <= d) continue;
				if (!fenceAt.containsKey(nk)) fenceOrder.add(nk);
				fenceAt.merge(nk, y + 1, Math::max);
			}
		}
		int n = 0;
		for (long nk : fenceOrder) {
			int x = BlockPos.getX(nk), z = BlockPos.getZ(nk), y = fenceAt.get(nk);
			for (int k = 1; k <= 3; k++) {
				BlockPos below = new BlockPos(x, y - k, z);
				if (!level.getBlockState(below).isSolid()) set(level, below, bricks);
			}
			set(level, new BlockPos(x, y, z), fence);
			set(level, new BlockPos(x, y + 1, z), n++ % 14 == 0 ? lantern : air);
		}
	}

	private static void buildSummit(ServerLevel level, BlockPos site, RandomSource rnd) {
		int top = summitY(site);
		BlockState basalt = ModBlocks.QUEST_SOUL_RITUAL_BASALT.defaultBlockState(), black = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
				pillar = Blocks.POLISHED_BASALT.defaultBlockState(), cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
				air = Blocks.AIR.defaultBlockState(), fire = Blocks.CAMPFIRE.defaultBlockState(), chain = Blocks.IRON_CHAIN.defaultBlockState();
		int cx = site.getX(), cz = site.getZ();
		// Floor and headroom.
		for (int dx = -R_TOP - 1; dx <= R_TOP + 1; dx++) for (int dz = -R_TOP - 1; dz <= R_TOP + 1; dz++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > R_TOP + 0.5) continue;
			set(level, new BlockPos(cx + dx, top, cz + dz), (Math.abs(dx) + Math.abs(dz)) % 4 == 0 || d < 3 ? basalt : black);
			for (int k = 1; k <= 12; k++) set(level, new BlockPos(cx + dx, top + k, cz + dz), air);
		}
		// Ring of pillars, some broken, with a broken lintel ring.
		for (int i = 0; i < 10; i++) {
			double a = i * Math.PI * 2 / 10;
			int px = cx + (int) Math.round(Math.cos(a) * 9), pz = cz + (int) Math.round(Math.sin(a) * 9);
			int h = rnd.nextFloat() < 0.35f ? 2 + rnd.nextInt(3) : 7;
			for (int k = 1; k <= h; k++) set(level, new BlockPos(px, top + k, pz), k == h && h < 7 ? cracked : pillar);
			if (h == 7) set(level, new BlockPos(px, top + 8, pz), basalt);
			if (i % 2 == 0) set(level, new BlockPos(px, top + 1, pz).relative(Direction.getApproximateNearest(-Math.cos(a), 0, -Math.sin(a))), fire);
		}
		for (int i = 0; i < 64; i++) {
			double a = i * Math.PI * 2 / 64;
			if (rnd.nextFloat() < 0.45f) continue;
			set(level, new BlockPos(cx + (int) Math.round(Math.cos(a) * 9), top + 8, cz + (int) Math.round(Math.sin(a) * 9)), black);
		}
		// Altar in the middle.
		set(level, new BlockPos(cx, top + 1, cz), basalt);
		set(level, new BlockPos(cx, top + 2, cz), Blocks.SOUL_LANTERN.defaultBlockState());
		// The chasm: a cleft cut from the summit out through the mountain flank, deep below the waste.
		double[] d = chasmDir();
		double nx = -d[1], nz = d[0];
		for (double r = 3.5; r <= R_TOP + 7; r += 0.4) {
			for (double o = -2.2; o <= 2.2; o += 0.4) {
				int x = (int) Math.floor(cx + 0.5 + d[0] * r + nx * o), z = (int) Math.floor(cz + 0.5 + d[1] * r + nz * o);
				for (int y = site.getY() - 22; y <= top + 12; y++) set(level, new BlockPos(x, y, z), air);
				set(level, new BlockPos(x, site.getY() - 23, z), Blocks.BLACKSTONE.defaultBlockState());
			}
		}
		// Chains hanging over the edge.
		for (int k = 0; k < 4; k++) {
			int x = (int) Math.floor(cx + 0.5 + d[0] * 3.6 + nx * (k - 1.5) * 1.3), z = (int) Math.floor(cz + 0.5 + d[1] * 3.6 + nz * (k - 1.5) * 1.3);
			for (int y = top - 1; y >= top - 4 - k; y--) set(level, new BlockPos(x, y, z), chain);
		}
	}

	private SoulTemple() {}
}
