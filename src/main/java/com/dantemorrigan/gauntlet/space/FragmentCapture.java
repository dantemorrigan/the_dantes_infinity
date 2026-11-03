package com.dantemorrigan.gauntlet.space;

import com.dantemorrigan.gauntlet.GauntletConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Takes a few SMALL pieces of the real world before the planet is "destroyed": a surface patch, a chunk of
 * rock, and a piece of a building if one is nearby. Only reads blocks; nothing in the world is changed.
 * Missing pieces fall back to prepared blueprints from the pack.
 */
public final class FragmentCapture {
	public static List<SpaceRecord.Fragment> capture(ServerLevel level, BlockPos origin) {
		int max = Math.max(4, GauntletConfig.get().fragmentMaxBlocks);
		List<SpaceRecord.Fragment> out = new ArrayList<>();
		Optional<ResourceKey<Biome>> biome = level.getBiome(origin).unwrapKey();

		// 1. Surface patch (3x3, top 3 layers) at the impact point.
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin).below();
		List<SpaceRecord.BlockEntry> ground = box(level, top.offset(-1, -2, -1), top.offset(1, 0, 1), top, max);
		out.add(ground.size() >= 4
				? new SpaceRecord.Fragment("ground", Optional.empty(), biome, ground)
				: new SpaceRecord.Fragment("ground", Optional.of("turf_fragment_01"), biome, List.of()));

		// 2. Rock below the surface.
		BlockPos rock = null;
		for (int dy = 1; dy <= 16 && rock == null; dy++) {
			BlockPos p = top.below(dy);
			if (level.getBlockState(p).is(BlockTags.BASE_STONE_OVERWORLD) || level.getBlockState(p).is(BlockTags.BASE_STONE_NETHER)) rock = p;
		}
		List<SpaceRecord.BlockEntry> rockBlocks = rock != null ? box(level, rock.offset(-1, -1, -1), rock.offset(1, 0, 1), rock, max) : List.of();
		out.add(rockBlocks.size() >= 4
				? new SpaceRecord.Fragment("rock", Optional.empty(), biome, rockBlocks)
				: new SpaceRecord.Fragment("rock", Optional.of("rock_fragment_01"), biome, List.of()));

		// 3. Nearest piece of a building (crafted blocks) within 16 blocks.
		BlockPos built = findBuilt(level, origin, 16);
		List<SpaceRecord.BlockEntry> house = built != null ? box(level, built.offset(-1, -1, -1), built.offset(1, 1, 1), built, max) : List.of();
		out.add(house.size() >= 3
				? new SpaceRecord.Fragment("building", Optional.empty(), level.getBiome(built).unwrapKey(), house)
				: new SpaceRecord.Fragment("building", Optional.of("ruined_house_wall"), biome, List.of()));
		return out;
	}

	private static List<SpaceRecord.BlockEntry> box(ServerLevel level, BlockPos from, BlockPos to, BlockPos center, int max) {
		List<SpaceRecord.BlockEntry> list = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			BlockState state = level.getBlockState(p);
			if (state.isAir() || !state.getFluidState().isEmpty() && state.is(Blocks.WATER) || state.hasBlockEntity()) continue;
			list.add(new SpaceRecord.BlockEntry(p.subtract(center).immutable(), state));
			if (list.size() >= max) break;
		}
		return list;
	}

	private static boolean isBuilt(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.WOODEN_STAIRS)
				|| s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES) || s.is(Blocks.BRICKS) || s.is(Blocks.GLASS)
				|| s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE_WALL) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SMOOTH_STONE);
	}

	private static BlockPos findBuilt(ServerLevel level, BlockPos origin, int r) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -4, -r), origin.offset(r, 8, r))) {
			if (!level.isLoaded(p)) continue;
			if (isBuilt(level.getBlockState(p))) {
				double d = p.distSqr(origin);
				if (d < bestDist) { bestDist = d; best = p.immutable(); }
			}
		}
		return best;
	}

	private FragmentCapture() {}
}
