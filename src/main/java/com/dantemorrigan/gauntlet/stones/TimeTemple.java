package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Temple of Time: a single ancient clock tower per world, standing on a stone-paved sanctuary terrace.
 * Inside its inner sanctum stands the broken clock mechanism ({@link ModBlocks#QUEST_TIME_CLOCK}).
 */
public final class TimeTemple {
	static final int R_FLAT = 20, R_BLEND = 36, TOWER_R = 6, HALL_R = 8, TOWER_H = 24;

	private static final AttachmentType<BlockPos> SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("time_temple_site"));
	private static final AttachmentType<List<Long>> SHAPED = AttachmentRegistry.<List<Long>>builder()
			.persistent(Codec.LONG.listOf()).buildAndRegister(InfinityGauntlet.id("time_temple_shaped"));
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("time_temple_built"));

	private static final Deque<Long> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();
	private static Set<Long> shapedCache;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(TimeTemple::site);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { QUEUE.clear(); QUEUED.clear(); shapedCache = null; });
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, fresh) -> {
			if (level != level.getServer().overworld()) return;
			BlockPos site = level.getAttached(SITE);
			if (site == null) return;
			ChunkPos cp = chunk.getPos();
			if (!inBlendRange(site, cp)) return;
			long key = cp.pack();
			if (!shaped(level).contains(key) && QUEUED.add(key)) QUEUE.add(key);
		});
		ServerTickEvents.END_SERVER_TICK.register(TimeTemple::tick);
	}

	/** Chosen once per world: 900-1500 blocks from spawn on dry land, away from the Temple of Soul. */
	public static BlockPos site(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = level.getAttached(SITE);
		if (site != null) return site;
		BlockPos soulSite = SoulTemple.site(server);
		RandomSource rnd = RandomSource.create(level.getSeed() ^ 0x713E7E31L);
		BlockPos spawn = level.getRespawnData().pos();
		var gen = level.getChunkSource().getGenerator();
		var state = level.getChunkSource().randomState();
		BlockPos best = null;
		for (int i = 0; i < 32 && best == null; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 900 + rnd.nextDouble() * 600;
			int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
			if (soulSite != null && soulSite.distToCenterSqr(x, soulSite.getY(), z) < 600 * 600) continue;
			int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state);
			if (h > level.getSeaLevel() + 3 && h < 125) best = new BlockPos(x, Mth.clamp(h, 66, 108), z);
		}
		if (best == null) best = new BlockPos(spawn.getX() - 1100, 76, spawn.getZ() + 400);
		level.setAttached(SITE, best);
		return best;
	}

	/** Relocates the Temple of Time (debug command). */
	public static void relocate(MinecraftServer server, BlockPos site) {
		ServerLevel level = server.overworld();
		level.setAttached(SITE, site);
		level.removeAttached(SHAPED);
		level.removeAttached(BUILT);
		shapedCache = null;
		QUEUE.clear();
		QUEUED.clear();
	}

	public static boolean built(MinecraftServer server) {
		return Boolean.TRUE.equals(server.overworld().getAttached(BUILT));
	}

	/** Position of the clock mechanism block inside the temple sanctum. */
	public static BlockPos clockPos(BlockPos site) {
		return new BlockPos(site.getX(), site.getY() + 3, site.getZ() - 4);
	}

	/** Standing position inside the sanctum in front of the clock mechanism. */
	public static Vec3 sanctumStand(BlockPos site) {
		return new Vec3(site.getX() + 0.5, site.getY() + 2, site.getZ() - 1.5);
	}

	private static Set<Long> shaped(ServerLevel level) {
		if (shapedCache == null) {
			List<Long> list = level.getAttached(SHAPED);
			shapedCache = new HashSet<>(list == null ? List.of() : list);
		}
		return shapedCache;
	}

	private static boolean inBlendRange(BlockPos site, ChunkPos cp) {
		int minX = (site.getX() - R_BLEND) >> 4, maxX = (site.getX() + R_BLEND) >> 4;
		int minZ = (site.getZ() - R_BLEND) >> 4, maxZ = (site.getZ() + R_BLEND) >> 4;
		return cp.x() >= minX && cp.x() <= maxX && cp.z() >= minZ && cp.z() <= maxZ;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = level.getAttached(SITE);
		if (site == null) return;
		for (int n = 0; n < 6 && !QUEUE.isEmpty(); n++) {
			long key = QUEUE.poll();
			QUEUED.remove(key);
			ChunkPos cp = ChunkPos.unpack(key);
			if (!level.hasChunk(cp.x(), cp.z()) || shaped(level).contains(key)) continue;
			shapeChunk(level, site, cp);
		}
		if (level.getGameTime() % 20 == 0 && !built(server)) {
			int minX = (site.getX() - R_BLEND) >> 4, maxX = (site.getX() + R_BLEND) >> 4;
			int minZ = (site.getZ() - R_BLEND) >> 4, maxZ = (site.getZ() + R_BLEND) >> 4;
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (!level.hasChunk(x, z)) continue;
					long key = new ChunkPos(x, z).pack();
					if (!shaped(level).contains(key) && QUEUED.add(key)) QUEUE.add(key);
				}
			}
			boolean ready = true;
			for (ChunkPos cp : templeChunks(site)) {
				if (!level.hasChunk(cp.x(), cp.z()) || !shaped(level).contains(cp.pack())) { ready = false; break; }
			}
			if (ready) build(level, site);
		}
	}

	private static List<ChunkPos> templeChunks(BlockPos site) {
		List<ChunkPos> out = new ArrayList<>();
		int minX = (site.getX() - R_FLAT) >> 4, maxX = (site.getX() + R_FLAT) >> 4;
		int minZ = (site.getZ() - R_FLAT) >> 4, maxZ = (site.getZ() + R_FLAT) >> 4;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) out.add(new ChunkPos(x, z));
		}
		return out;
	}

	/** Forces chunk shaping and builds the Temple of Time immediately. */
	public static void forceBuild(ServerLevel level) {
		BlockPos site = site(level.getServer());
		for (ChunkPos cp : templeChunks(site)) {
			level.getChunk(cp.x(), cp.z());
			if (!shaped(level).contains(cp.pack())) shapeChunk(level, site, cp);
		}
		build(level, site);
	}

	private static void shapeChunk(ServerLevel level, BlockPos site, ChunkPos cp) {
		BlockState air = Blocks.AIR.defaultBlockState(),
				stone = Blocks.STONE.defaultBlockState(),
				dirt = Blocks.DIRT.defaultBlockState(),
				grass = Blocks.GRASS_BLOCK.defaultBlockState(),
				bricks = Blocks.STONE_BRICKS.defaultBlockState(),
				mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		int base = site.getY();
		for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
			for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
				double d = Math.hypot(x - site.getX(), z - site.getZ());
				if (d > R_BLEND) continue;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				int t;
				if (d <= R_FLAT) {
					t = base;
				} else {
					double k = Mth.clamp((d - R_FLAT) / (R_BLEND - R_FLAT), 0, 1);
					k = k * k * (3 - 2 * k);
					t = (int) Math.round(Mth.lerp(k, base, ground));
				}
				boolean plaza = d <= R_FLAT - 2;
				int from = Math.min(t, ground) - 4;
				int to = Math.max(top, t) + 12;
				long h = (x * 341873128712L + z * 132897987541L);
				for (int y = from; y <= to; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s;
					if (y > t) {
						s = air;
					} else if (y == t) {
						if (plaza) {
							int pick = (int) ((h >>> 4) & 7);
							s = pick == 0 ? mossy : pick == 1 ? cracked : bricks;
						} else {
							s = grass;
						}
					} else if (y > t - 3) {
						s = plaza ? stone : dirt;
					} else {
						s = stone;
					}
					if (level.getBlockState(p) != s) level.setBlock(p, s, flags);
				}
			}
		}
		shaped(level).add(cp.pack());
		level.setAttached(SHAPED, new ArrayList<>(shaped(level)));
	}

	private static void set(ServerLevel level, BlockPos p, BlockState s) {
		level.setBlock(p, s, Block.UPDATE_ALL);
	}

	private static void build(ServerLevel level, BlockPos site) {
		if (Boolean.TRUE.equals(level.getAttached(BUILT))) return;
		RandomSource rnd = RandomSource.create(site.asLong() ^ 0x713EL);
		int cx = site.getX(), y0 = site.getY(), cz = site.getZ();
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState(),
				mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
				deepslate = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
				tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(),
				carved = ModBlocks.QUEST_TIME_CARVED_STONE.defaultBlockState(),
				copper = Blocks.OXIDIZED_CUT_COPPER.defaultBlockState(),
				waxedCopper = Blocks.WAXED_COPPER_BLOCK.defaultBlockState(),
				gold = Blocks.GOLD_BLOCK.defaultBlockState(),
				chain = Blocks.IRON_CHAIN.defaultBlockState(),
				lantern = Blocks.LANTERN.defaultBlockState(),
				soulLantern = Blocks.SOUL_LANTERN.defaultBlockState(),
				air = Blocks.AIR.defaultBlockState();

		// Clear headroom over the whole courtyard and tower.
		for (int dx = -R_FLAT + 2; dx <= R_FLAT - 2; dx++) {
			for (int dz = -R_FLAT + 2; dz <= R_FLAT - 2; dz++) {
				if (dx * dx + dz * dz > (R_FLAT - 2) * (R_FLAT - 2)) continue;
				for (int dy = 1; dy <= TOWER_H + 10; dy++) set(level, new BlockPos(cx + dx, y0 + dy, cz + dz), air);
			}
		}

		// Raised temple platform (y0 + 1) and sanctum floor.
		for (int dx = -HALL_R - 2; dx <= HALL_R + 2; dx++) {
			for (int dz = -HALL_R - 2; dz <= HALL_R + 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > HALL_R * 2 - 1) continue;
				BlockState floor = (Math.abs(dx) == Math.abs(dz) || (dx == 0 && Math.abs(dz) <= 5) || (dz == 0 && Math.abs(dx) <= 5))
						? carved
						: ((dx + dz) & 1) == 0 ? bricks : deepslate;
				set(level, new BlockPos(cx + dx, y0 + 1, cz + dz), floor);
			}
		}

		// Cardinal entrance stairs up to y0 + 1.
		for (int w = -2; w <= 2; w++) {
			set(level, new BlockPos(cx + w, y0 + 1, cz + HALL_R + 3),
					Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
			set(level, new BlockPos(cx + w, y0 + 1, cz - HALL_R - 3),
					Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			set(level, new BlockPos(cx + HALL_R + 3, y0 + 1, cz + w),
					Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
			set(level, new BlockPos(cx - HALL_R - 3, y0 + 1, cz + w),
					Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		}

		// Courtyard perimeter pillars and lanterns.
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI * 2 / 12 + Math.PI / 12;
			int px = cx + (int) Math.round(Math.cos(a) * (R_FLAT - 4));
			int pz = cz + (int) Math.round(Math.sin(a) * (R_FLAT - 4));
			int h = rnd.nextFloat() < 0.25f ? 3 : 5;
			for (int dy = 1; dy <= h; dy++) {
				set(level, new BlockPos(px, y0 + dy, pz), dy == 1 || dy == h ? carved : bricks);
			}
			if (h == 5) set(level, new BlockPos(px, y0 + 6, pz), lantern);
		}

		// Lower sanctum walls (dy = 2..9).
		for (int dy = 2; dy <= 9; dy++) {
			for (int dx = -HALL_R; dx <= HALL_R; dx++) {
				for (int dz = -HALL_R; dz <= HALL_R; dz++) {
					int manhattan = Math.abs(dx) + Math.abs(dz);
					boolean outer = (Math.abs(dx) == HALL_R && Math.abs(dz) <= HALL_R - 2)
							|| (Math.abs(dz) == HALL_R && Math.abs(dx) <= HALL_R - 2)
							|| (manhattan == HALL_R * 2 - 2 && Math.abs(dx) >= HALL_R - 2 && Math.abs(dz) >= HALL_R - 2);
					if (!outer) continue;
					// South main entrance archway, plus side narrow windows.
					boolean southDoor = dz == HALL_R && Math.abs(dx) <= 2 && dy <= 6;
					boolean sideWindow = (Math.abs(dx) == HALL_R && Math.abs(dz) <= 1 && dy >= 4 && dy <= 7);
					if (southDoor || sideWindow) continue;
					boolean corner = Math.abs(dx) >= HALL_R - 2 && Math.abs(dz) >= HALL_R - 2;
					boolean band = dy == 2 || dy == 6 || dy == 9;
					BlockState wall = corner ? deepslate : band ? carved : (rnd.nextFloat() < 0.2f ? mossy : rnd.nextFloat() < 0.15f ? cracked : bricks);
					set(level, new BlockPos(cx + dx, y0 + dy, cz + dz), wall);
				}
			}
		}

		// Sanctum roof cornice at dy = 10 around the clock tower shaft.
		for (int dx = -HALL_R - 1; dx <= HALL_R + 1; dx++) {
			for (int dz = -HALL_R - 1; dz <= HALL_R + 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > HALL_R * 2 - 1) continue;
				if (Math.abs(dx) < TOWER_R - 1 && Math.abs(dz) < TOWER_R - 1) continue;
				set(level, new BlockPos(cx + dx, y0 + 10, cz + dz),
						Math.abs(dx) >= HALL_R || Math.abs(dz) >= HALL_R ? carved : tiles);
			}
		}

		// Upper Clock Tower shaft (dy = 10..TOWER_H).
		for (int dy = 10; dy <= TOWER_H; dy++) {
			for (int dx = -TOWER_R; dx <= TOWER_R; dx++) {
				for (int dz = -TOWER_R; dz <= TOWER_R; dz++) {
					boolean edge = Math.abs(dx) == TOWER_R || Math.abs(dz) == TOWER_R;
					if (!edge) continue;
					if (Math.abs(dx) == TOWER_R && Math.abs(dz) == TOWER_R) {
						set(level, new BlockPos(cx + dx, y0 + dy, cz + dz), deepslate);
						continue;
					}
					boolean band = dy == 10 || dy == 14 || dy == 20 || dy == TOWER_H;
					BlockState s = band ? carved : bricks;
					set(level, new BlockPos(cx + dx, y0 + dy, cz + dz), s);
				}
			}
		}

		// Four grand exterior clock dials on the tower (dy = 15..19).
		int dialY = y0 + 17;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int sx = side.getStepX(), sz = side.getStepZ();
			int rx = -sz, rz = sx;
			for (int u = -2; u <= 2; u++) {
				for (int v = -2; v <= 2; v++) {
					BlockPos p = new BlockPos(cx + sx * TOWER_R + rx * u, dialY + v, cz + sz * TOWER_R + rz * u);
					BlockState dial;
					if (u == 0 && v == 0) dial = gold;
					else if ((u == 0 && v >= 0) || (v == 0 && u >= 0)) dial = waxedCopper;
					else if (Math.abs(u) == 2 || Math.abs(v) == 2) dial = carved;
					else dial = Blocks.SEA_LANTERN.defaultBlockState();
					set(level, p, dial);
				}
			}
		}

		// Stepped copper and stone-brick clock tower spire (dy = TOWER_H + 1 .. TOWER_H + 7).
		for (int step = 0; step <= 6; step++) {
			int r = TOWER_R - step;
			int y = y0 + TOWER_H + 1 + step;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r || Math.abs(dz) == r || step >= 5) {
						set(level, new BlockPos(cx + dx, y, cz + dz), step % 2 == 0 ? copper : carved);
					}
				}
			}
		}
		for (int dy = 1; dy <= 3; dy++) {
			set(level, new BlockPos(cx, y0 + TOWER_H + 7 + dy, cz), dy == 3 ? Blocks.LIGHTNING_ROD.defaultBlockState() : gold);
		}

		// Hanging pendulum in the center of the tower.
		for (int dy = 7; dy <= TOWER_H; dy++) {
			set(level, new BlockPos(cx, y0 + dy, cz), chain);
		}
		set(level, new BlockPos(cx, y0 + 6, cz), gold);
		set(level, new BlockPos(cx, y0 + 5, cz), soulLantern);

		// Inner Sanctum Altar & the Clock Mechanism on the north side (facing SOUTH).
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -5; dz <= -2; dz++) {
				set(level, new BlockPos(cx + dx, y0 + 2, cz + dz), Math.abs(dx) == 3 || dz == -2 ? carved : deepslate);
			}
		}
		for (int w = -1; w <= 1; w++) {
			set(level, new BlockPos(cx + w, y0 + 2, cz - 1),
					Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		}
		// Clock housing frame around clockPos(site) = (cx, y0 + 3, cz - 4).
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 2; dy <= 5; dy++) {
				BlockPos p = new BlockPos(cx + dx, y0 + dy, cz - 4);
				if (dx == 0 && dy == 3) continue;
				BlockState frame = (Math.abs(dx) == 1 && dy >= 2 && dy <= 4) || (dx == 0 && (dy == 2 || dy == 4))
						? carved : chiseled;
				set(level, p, frame);
			}
		}
		set(level, new BlockPos(cx - 2, y0 + 5, cz - 3), lantern);
		set(level, new BlockPos(cx + 2, y0 + 5, cz - 3), lantern);
		set(level, new BlockPos(cx - 4, y0 + 3, cz + 3), lantern);
		set(level, new BlockPos(cx + 4, y0 + 3, cz + 3), lantern);

		int inserted = TimeQuest.insertedCount(level.getServer());
		set(level, clockPos(site), ModBlocks.QUEST_TIME_CLOCK.defaultBlockState()
				.setValue(TimeClockBlock.FACING, Direction.SOUTH)
				.setValue(TimeClockBlock.GEARS, inserted));

		level.setAttached(BUILT, true);
		InfinityGauntlet.LOGGER.info("Temple of Time built at {}", site);
	}

	private TimeTemple() {}
}
