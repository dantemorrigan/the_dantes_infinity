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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Ruined City: a single overgrown, vine-draped ancient city per world.
 * Filled with crumbling stone ruins, moss, vines, treasure chests with varied loot,
 * and roamed by 10 smart Mind Zombies glowing with Mind Stone essence.
 */
public final class MindCity {
	public static final int R_FLAT = 26, R_BLEND = 42;

	private static final AttachmentType<BlockPos> SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("mind_city_site"));
	private static final AttachmentType<List<Long>> SHAPED = AttachmentRegistry.<List<Long>>builder()
			.persistent(Codec.LONG.listOf()).buildAndRegister(InfinityGauntlet.id("mind_city_shaped"));
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("mind_city_built"));

	@SuppressWarnings("unchecked")
	private static final ResourceKey<LootTable>[] CHEST_LOOT = new ResourceKey[]{
			BuiltInLootTables.SIMPLE_DUNGEON,
			BuiltInLootTables.STRONGHOLD_CORRIDOR,
			BuiltInLootTables.STRONGHOLD_LIBRARY,
			BuiltInLootTables.ANCIENT_CITY,
			BuiltInLootTables.DESERT_PYRAMID,
			BuiltInLootTables.JUNGLE_TEMPLE,
			BuiltInLootTables.RUINED_PORTAL,
			BuiltInLootTables.ABANDONED_MINESHAFT,
			BuiltInLootTables.PILLAGER_OUTPOST,
			BuiltInLootTables.WOODLAND_MANSION
	};

	private static final Deque<Long> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();
	private static Set<Long> shapedCache;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(MindCity::site);
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
		ServerTickEvents.END_SERVER_TICK.register(MindCity::tick);
	}

	/** Chosen once per world: 900-1500 blocks from spawn on dry land, away from the Temples of Soul and Time. */
	public static BlockPos site(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = level.getAttached(SITE);
		if (site != null) return site;
		BlockPos soulSite = SoulTemple.site(server);
		BlockPos timeSite = TimeTemple.site(server);
		RandomSource rnd = RandomSource.create(level.getSeed() ^ 0x414E4453L);
		BlockPos spawn = level.getRespawnData().pos();
		var gen = level.getChunkSource().getGenerator();
		var state = level.getChunkSource().randomState();
		BlockPos best = null;
		for (int i = 0; i < 40 && best == null; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 900 + rnd.nextDouble() * 600;
			int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
			if (soulSite != null && soulSite.distToCenterSqr(x, soulSite.getY(), z) < 600 * 600) continue;
			if (timeSite != null && timeSite.distToCenterSqr(x, timeSite.getY(), z) < 600 * 600) continue;
			int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state);
			if (h > level.getSeaLevel() + 3 && h < 125) best = new BlockPos(x, Mth.clamp(h, 66, 108), z);
		}
		if (best == null) best = new BlockPos(spawn.getX() + 450, 74, spawn.getZ() - 1150);
		level.setAttached(SITE, best);
		return best;
	}

	/** Relocates the Ruined City (debug command). */
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

	/** Standing position on the main avenue near the central plaza of the Ruined City. */
	public static Vec3 plazaStand(BlockPos site) {
		return new Vec3(site.getX() + 0.5, site.getY() + 1, site.getZ() + 6.5);
	}

	/** Standing position at the south entrance of the Ruined City looking along the main avenue. */
	public static Vec3 entranceStand(BlockPos site) {
		return new Vec3(site.getX() + 0.5, site.getY() + 1, site.getZ() + 18.5);
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
			for (ChunkPos cp : cityChunks(site)) {
				if (!level.hasChunk(cp.x(), cp.z()) || !shaped(level).contains(cp.pack())) { ready = false; break; }
			}
			if (ready) build(level, site);
		}
	}

	private static List<ChunkPos> cityChunks(BlockPos site) {
		List<ChunkPos> out = new ArrayList<>();
		int minX = (site.getX() - R_FLAT) >> 4, maxX = (site.getX() + R_FLAT) >> 4;
		int minZ = (site.getZ() - R_FLAT) >> 4, maxZ = (site.getZ() + R_FLAT) >> 4;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) out.add(new ChunkPos(x, z));
		}
		return out;
	}

	/** Forces chunk shaping and builds the Ruined City immediately. */
	public static void forceBuild(ServerLevel level) {
		BlockPos site = site(level.getServer());
		for (ChunkPos cp : cityChunks(site)) {
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
				cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				mossBlock = Blocks.MOSS_BLOCK.defaultBlockState(),
				mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
				relief = ModBlocks.QUEST_MIND_MOSSY_RELIEF.defaultBlockState();
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
				boolean cityFloor = d <= R_FLAT - 2;
				int dx = x - site.getX(), dz = z - site.getZ();
				boolean avenue = Math.abs(dx) <= 2 || Math.abs(dz) <= 2 || (Math.abs(dx) <= 5 && Math.abs(dz) <= 5);
				int from = Math.min(t, ground) - 4;
				int to = Math.max(top, t) + 14;
				long h = (x * 341873128712L + z * 132897987541L);
				int pick = (int) ((h >>> 4) & 15);
				for (int y = from; y <= to; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s;
					if (y > t) {
						s = air;
					} else if (y == t) {
						if (cityFloor) {
							if (avenue) {
								s = (dx % 4 == 0 && dz % 4 == 0) ? relief
										: pick < 5 ? mossy : pick < 9 ? cracked : pick < 12 ? bricks : mossyCobble;
							} else {
								s = pick < 5 ? mossBlock : pick < 9 ? mossy : pick < 12 ? mossyCobble : pick < 14 ? cracked : grass;
							}
						} else {
							s = pick < 4 ? mossBlock : grass;
						}
					} else if (y > t - 3) {
						s = cityFloor ? stone : dirt;
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
		RandomSource rnd = RandomSource.create(site.asLong() ^ 0x4D494E44L);
		int cx = site.getX(), y0 = site.getY(), cz = site.getZ();
		BlockState air = Blocks.AIR.defaultBlockState(),
				bricks = Blocks.STONE_BRICKS.defaultBlockState(),
				mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
				mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
				mossBlock = Blocks.MOSS_BLOCK.defaultBlockState(),
				mossCarpet = Blocks.MOSS_CARPET.defaultBlockState(),
				relief = ModBlocks.QUEST_MIND_MOSSY_RELIEF.defaultBlockState(),
				mossyWall = Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(),
				lantern = Blocks.LANTERN.defaultBlockState();

		// Clear headroom over the city.
		for (int dx = -R_FLAT + 1; dx <= R_FLAT - 1; dx++) {
			for (int dz = -R_FLAT + 1; dz <= R_FLAT - 1; dz++) {
				if (dx * dx + dz * dz > (R_FLAT - 1) * (R_FLAT - 1)) continue;
				for (int dy = 1; dy <= 16; dy++) set(level, new BlockPos(cx + dx, y0 + dy, cz + dz), air);
			}
		}

		// Central Ruined Plaza & Overgrown Obelisk Monument at (cx, y0, cz).
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
				boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				set(level, new BlockPos(cx + dx, y0, cz + dz), rim ? relief : mossy);
				if (rim && (dx == 0 || dz == 0)) {
					Direction dir = dx < 0 ? Direction.EAST : dx > 0 ? Direction.WEST : dz < 0 ? Direction.SOUTH : Direction.NORTH;
					set(level, new BlockPos(cx + dx, y0 + 1, cz + dz),
							Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dir));
				}
			}
		}
		for (int dy = 1; dy <= 7; dy++) {
			set(level, new BlockPos(cx, y0 + dy, cz), dy == 1 || dy == 4 || dy == 7 ? relief : dy % 2 == 0 ? mossy : cracked);
		}
		set(level, new BlockPos(cx, y0 + 8, cz), mossCarpet);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos base = new BlockPos(cx + d.getStepX(), y0 + 1, cz + d.getStepZ());
			set(level, base, relief);
			set(level, base.above(), mossyWall);
		}

		// Six ruined structures around the city avenues, each with jagged walls, rubble, moss, vines, and chests.
		int[][] ruins = {
				{-12, -12, 8, 8, 9, 1}, // NW collapsed watchtower
				{11, -12, 9, 7, 7, 1},  // NE overgrown archive hall
				{-12, 11, 8, 8, 7, 2},  // SW ruined barracks
				{11, 11, 9, 9, 8, 0},   // SE crumbling sanctum
				{-17, 0, 7, 7, 6, 3},   // W merchant ruin
				{17, 0, 7, 7, 6, 2}     // E colonnade ruin
		};
		for (int idx = 0; idx < ruins.length; idx++) {
			int[] r = ruins[idx];
			buildRuin(level, new BlockPos(cx + r[0], y0, cz + r[1]), r[2], r[3], r[4], r[5], idx, rnd);
		}

		// Broken city perimeter wall segments & overgrown street pillars.
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI * 2 / 16 + 0.18;
			int px = cx + (int) Math.round(Math.cos(a) * (R_FLAT - 3));
			int pz = cz + (int) Math.round(Math.sin(a) * (R_FLAT - 3));
			if (Math.abs(px - cx) <= 3 || Math.abs(pz - cz) <= 3) continue;
			int h = 2 + rnd.nextInt(4);
			for (int dy = 1; dy <= h; dy++) {
				BlockState s = dy == h && rnd.nextBoolean() ? relief : rnd.nextBoolean() ? mossy : cracked;
				set(level, new BlockPos(px, y0 + dy, pz), s);
			}
			if (rnd.nextBoolean()) set(level, new BlockPos(px, y0 + h + 1, pz), mossCarpet);
		}

		// Street lamps and rubble along the main avenues.
		int[][] lamps = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}, {-10, -4}, {10, -4}, {-10, 4}, {10, 4}};
		for (int[] l : lamps) {
			BlockPos lp = new BlockPos(cx + l[0], y0 + 1, cz + l[1]);
			set(level, lp, relief);
			set(level, lp.above(1), mossyWall);
			set(level, lp.above(2), mossyWall);
			set(level, lp.above(3), lantern);
		}

		// Place 10 treasure chests with varied loot across the ruined structures and alcoves.
		BlockPos[] chestSpots = {
				new BlockPos(cx - 12, y0 + 1, cz - 14),
				new BlockPos(cx - 14, y0 + 1, cz - 12),
				new BlockPos(cx + 11, y0 + 1, cz - 14),
				new BlockPos(cx + 13, y0 + 1, cz - 12),
				new BlockPos(cx - 12, y0 + 1, cz + 13),
				new BlockPos(cx - 14, y0 + 1, cz + 11),
				new BlockPos(cx + 11, y0 + 1, cz + 13),
				new BlockPos(cx + 13, y0 + 1, cz + 11),
				new BlockPos(cx - 17, y0 + 1, cz),
				new BlockPos(cx + 17, y0 + 1, cz)
		};
		for (int i = 0; i < chestSpots.length; i++) {
			placeLootChest(level, chestSpots[i], Direction.Plane.HORIZONTAL.shuffledCopy(rnd).getFirst(),
					CHEST_LOOT[i % CHEST_LOOT.length], rnd.nextLong());
		}

		// Drape vines on solid ruin walls and scatter moss carpets, ferns, and azaleas across the city.
		overgrowCity(level, site, rnd);

		level.setAttached(BUILT, true);
		InfinityGauntlet.LOGGER.info("Ruined City built at {}", site);
	}

	private static void buildRuin(ServerLevel level, BlockPos center, int w, int d, int maxH, int doorSide, int style, RandomSource rnd) {
		int rx = w / 2, rz = d / 2;
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState(),
				mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				chiseled = Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
				mossBlock = Blocks.MOSS_BLOCK.defaultBlockState(),
				mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
				relief = ModBlocks.QUEST_MIND_MOSSY_RELIEF.defaultBlockState();

		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				BlockPos floorPos = center.offset(dx, 0, dz);
				set(level, floorPos, (dx == 0 || dz == 0) ? relief : rnd.nextFloat() < 0.45f ? mossy : rnd.nextFloat() < 0.3f ? mossBlock : cracked);

				boolean edgeX = Math.abs(dx) == rx;
				boolean edgeZ = Math.abs(dz) == rz;
				if (!edgeX && !edgeZ) continue;

				// Doorway opening facing the avenue.
				boolean door = (doorSide == 0 && dz == -rz && Math.abs(dx) <= 1)
						|| (doorSide == 1 && dz == rz && Math.abs(dx) <= 1)
						|| (doorSide == 2 && dx == -rx && Math.abs(dz) <= 1)
						|| (doorSide == 3 && dx == rx && Math.abs(dz) <= 1);

				// Jagged broken wall height profile so every building looks like a collapsed ruin.
				boolean corner = edgeX && edgeZ;
				int wallH = corner ? maxH : Math.max(2, maxH - 1 - rnd.nextInt(Math.max(2, maxH / 2)));
				for (int dy = 1; dy <= wallH; dy++) {
					if (door && dy <= 3) continue;
					boolean window = !corner && dy >= 3 && dy <= 4 && ((edgeX && dz == 0) || (edgeZ && dx == 0));
					if (window) continue;
					BlockState wall;
					if (dy == 2 || (corner && dy == wallH)) wall = relief;
					else if (rnd.nextFloat() < 0.45f) wall = mossy;
					else if (rnd.nextFloat() < 0.25f) wall = cracked;
					else if (rnd.nextFloat() < 0.12f) wall = mossBlock;
					else if (rnd.nextFloat() < 0.1f) wall = chiseled;
					else wall = bricks;
					set(level, center.offset(dx, dy, dz), wall);
				}
			}
		}

		// Interior details & collapsed roof rubble.
		if (style == 1) {
			// Overgrown archive bookshelves.
			for (int dz = -rz + 1; dz <= rz - 1; dz += 2) {
				set(level, center.offset(-rx + 1, 1, dz), Blocks.BOOKSHELF.defaultBlockState());
				set(level, center.offset(-rx + 1, 2, dz), mossBlock);
			}
		}
		for (int r = 0; r < 6; r++) {
			int rbx = Mth.nextInt(rnd, -rx + 1, rx - 1);
			int rbz = Mth.nextInt(rnd, -rz + 1, rz - 1);
			if (Math.abs(rbx) <= 1 && Math.abs(rbz) <= 1) continue;
			BlockPos rp = center.offset(rbx, 1, rbz);
			if (level.getBlockState(rp).isAir()) {
				set(level, rp, rnd.nextBoolean() ? mossyCobble : Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState());
			}
		}
	}

	private static void placeLootChest(ServerLevel level, BlockPos pos, Direction facing, ResourceKey<LootTable> loot, long seed) {
		set(level, pos.below(), ModBlocks.QUEST_MIND_MOSSY_RELIEF.defaultBlockState());
		set(level, pos.above(), Blocks.AIR.defaultBlockState());
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		set(level, pos, chest);
		if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
			container.setLootTable(loot, seed);
		}
	}

	private static void overgrowCity(ServerLevel level, BlockPos site, RandomSource rnd) {
		int cx = site.getX(), y0 = site.getY(), cz = site.getZ();
		BlockState mossCarpet = Blocks.MOSS_CARPET.defaultBlockState(),
				fern = Blocks.FERN.defaultBlockState(),
				azalea = Blocks.AZALEA.defaultBlockState(),
				floweringAzalea = Blocks.FLOWERING_AZALEA.defaultBlockState();

		for (int dx = -R_FLAT + 2; dx <= R_FLAT - 2; dx++) {
			for (int dz = -R_FLAT + 2; dz <= R_FLAT - 2; dz++) {
				if (dx * dx + dz * dz > (R_FLAT - 2) * (R_FLAT - 2)) continue;
				// Ground greenery and moss carpets.
				BlockPos ground = new BlockPos(cx + dx, y0, cz + dz);
				BlockPos above = ground.above();
				if (level.getBlockState(above).isAir() && level.getBlockState(ground).isSolidRender()) {
					float f = rnd.nextFloat();
					if (f < 0.14f) set(level, above, mossCarpet);
					else if (f < 0.18f && ( level.getBlockState(ground).is(Blocks.MOSS_BLOCK) || level.getBlockState(ground).is(Blocks.GRASS_BLOCK))) {
						set(level, above, rnd.nextInt(4) == 0 ? floweringAzalea : rnd.nextInt(3) == 0 ? azalea : fern);
					}
				}
				// Drape vines along vertical ruin walls.
				for (int dy = 1; dy <= 9; dy++) {
					BlockPos p = new BlockPos(cx + dx, y0 + dy, cz + dz);
					if (!level.getBlockState(p).isAir()) continue;
					BlockState vine = Blocks.VINE.defaultBlockState();
					boolean any = false;
					for (Direction dir : Direction.Plane.HORIZONTAL) {
						BlockPos neighbor = p.relative(dir);
						BlockState ns = level.getBlockState(neighbor);
						if (!ns.isAir() && !ns.is(Blocks.VINE) && !ns.is(Blocks.CHEST) && VineBlock.isAcceptableNeighbour(level, neighbor, dir)) {
							if (rnd.nextFloat() < 0.42f) {
								vine = vine.setValue(VineBlock.getPropertyForFace(dir), true);
								any = true;
							}
						}
					}
					if (any) set(level, p, vine);
				}
			}
		}
	}

	private MindCity() {}
}
