package com.dantemorrigan.gauntlet.doom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Latveria's terrain and buildings. Everything comes from {@link LatveriaPlan}, so a chunk is written once when it is
 * first generated and is an ordinary saved chunk from then on: what players build or break stays.
 */
public final class LatveriaGenerator extends ChunkGenerator {
	public static final MapCodec<LatveriaGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(instance, LatveriaGenerator::new));
	private static final BlockState STONE = Blocks.STONE.defaultBlockState(), DIRT = Blocks.DIRT.defaultBlockState(),
			GRASS = Blocks.GRASS_BLOCK.defaultBlockState(), WATER = Blocks.WATER.defaultBlockState(),
			SNOW = Blocks.SNOW_BLOCK.defaultBlockState(), GRAVEL = Blocks.GRAVEL.defaultBlockState(),
			DEEP = Blocks.DEEPSLATE.defaultBlockState(), BEDROCK = Blocks.BEDROCK.defaultBlockState(),
			AIR = Blocks.AIR.defaultBlockState();

	public LatveriaGenerator(BiomeSource biomes) {
		super(biomes);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
		ChunkPos pos = chunk.getPos();
		int bx = pos.getMinBlockX(), bz = pos.getMinBlockZ();
		int[] heights = new int[18 * 18];
		for (int dx = -1; dx <= 16; dx++) for (int dz = -1; dz <= 16; dz++) {
			heights[(dx + 1) * 18 + dz + 1] = LatveriaPlan.surface(bx + dx, bz + dz);
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int top = chunk.getMinY() + chunk.getHeight() - 1;
		for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
			int h = Math.min(top - 8, heights[(dx + 1) * 18 + dz + 1]);
			int slope = 0;
			for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				slope = Math.max(slope, Math.abs(heights[(dx + 1 + n[0]) * 18 + dz + 1 + n[1]] - h));
			}
			boolean castle = Math.hypot(bx + dx, bz + dz - LatveriaPlan.CASTLE_Z) < 70;
			for (int y = chunk.getMinY(); y <= Math.max(h, LatveriaPlan.SEA); y++) {
				BlockState state;
				if (y == chunk.getMinY()) state = BEDROCK;
				else if (y > h) state = WATER;
				else if (y < 16) state = DEEP;
				else if (y < h - 3) state = STONE;
				else if (h < LatveriaPlan.SEA) state = GRAVEL;
				else if (castle || slope > 2 || h > 128) state = h > 150 && y == h && slope <= 3 ? SNOW : STONE;
				else state = y == h ? GRASS : DIRT;
				chunk.setBlockState(at.set(bx + dx, y, bz + dz), state, 0);
			}
		}
		apply(chunk, LatveriaPlan.OPS, bx, bz);
		Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
		return CompletableFuture.completedFuture(chunk);
	}

	private static void apply(ChunkAccess chunk, List<LatveriaPlan.Op> ops, int bx, int bz) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (LatveriaPlan.Op op : ops) {
			if (op.x1() < bx || op.x0() > bx + 15 || op.z1() < bz || op.z0() > bz + 15) continue;
			for (int x = Math.max(op.x0(), bx); x <= Math.min(op.x1(), bx + 15); x++) {
				for (int z = Math.max(op.z0(), bz); z <= Math.min(op.z1(), bz + 15); z++) {
					for (int y = op.y0(); y <= op.y1(); y++) chunk.setBlockState(at.set(x, y, z), op.state(), 0);
				}
			}
		}
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk) {}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {}

	@Override
	public int getGenDepth() {
		return 320;
	}

	@Override
	public int getSeaLevel() {
		return LatveriaPlan.SEA;
	}

	@Override
	public int getMinY() {
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
		return Math.max(LatveriaPlan.surface(x, z), type == Heightmap.Types.OCEAN_FLOOR_WG ? 0 : LatveriaPlan.SEA) + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
		int h = LatveriaPlan.surface(x, z);
		BlockState[] column = new BlockState[level.getHeight()];
		for (int i = 0; i < column.length; i++) {
			int y = level.getMinY() + i;
			column[i] = y <= h ? STONE : y <= LatveriaPlan.SEA ? WATER : AIR;
		}
		return new NoiseColumn(level.getMinY(), column);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {}
}
