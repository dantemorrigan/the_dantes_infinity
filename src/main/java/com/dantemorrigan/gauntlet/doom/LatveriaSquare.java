package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * The great square: paved in rings and stars around a square moat, with the great statue of Doom on its island and
 * bridges to it on four sides; market stalls with wool awnings, benches, planted corners, flagpoles and lamps round the
 * edge. The roads cross it and stay open.
 */
final class LatveriaSquare {
	static final int CX = LatveriaStatue.CX, CZ = LatveriaStatue.CZ, HALF = 24;

	private LatveriaSquare() {}

	static void build() {
		pave();
		moat();
		LatveriaStatue.build();
		corners();
		stalls();
		benches();
		flagpoles();
	}

	private static BlockState brick(int x, int z) {
		int dx = Math.abs(x - CX), dz = Math.abs(z - CZ), d = Math.max(dx, dz);
		if (d >= 23) return Blocks.STONE_BRICKS.defaultBlockState();
		if (d <= 16) return d >= 15 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		// A star of green clay on the diagonals and the axes, a chequer of stone and andesite between.
		if (dx == dz || dx <= 0 || dz <= 0) return Blocks.GREEN_TERRACOTTA.defaultBlockState();
		return ((x + z) & 1) == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	private static void pave() {
		for (int x = CX - HALF; x <= CX + HALF; x++) {
			for (int z = CZ - HALF; z <= CZ + HALF; z++) set(x, G, z, brick(x, z));
		}
		reserve(CX - HALF, CZ - HALF, CX + HALF, CZ + HALF);
	}

	/** The moat: a ring of water five wide round the pedestal, bridged on all four sides; its floor glows. */
	private static void moat() {
		int in = 9, out = 14;
		for (int x = -out; x <= out; x++) for (int z = -out; z <= out; z++) {
			int ax = Math.abs(x), az = Math.abs(z);
			if (Math.max(ax, az) <= in || Math.max(ax, az) > out || ax + az > 22) continue;
			// Bridges: three wide on each axis, over the water.
			boolean bridge = ax <= 2 || az <= 2;
			int wx = CX + x, wz = CZ + z;
			if (bridge) continue;
			set(wx, G, wz, Blocks.AIR);
			set(wx, G - 1, wz, Blocks.WATER);
			set(wx, G - 2, wz, ((ax + az) % 4 == 0) ? Blocks.SEA_LANTERN : Blocks.DEEPSLATE_TILES);
		}
		// Bridges: stone bricks with a low wall along each side, on the water's own level.
		for (int s : new int[]{-1, 1}) {
			fill(CX + s * 10, G, CZ - 2, CX + s * 14, G, CZ + 2, Blocks.POLISHED_DEEPSLATE);
			fill(CX - 2, G, CZ + s * 10, CX + 2, G, CZ + s * 14, Blocks.POLISHED_DEEPSLATE);
			for (int i = 10; i <= 14; i++) {
				for (int e : new int[]{-2, 2}) {
					set(CX + s * i, G + 1, CZ + e, Blocks.POLISHED_BLACKSTONE_WALL);
					set(CX + e, G + 1, CZ + s * i, Blocks.POLISHED_BLACKSTONE_WALL);
				}
			}
			for (int e : new int[]{-2, 2}) {
				set(CX + s * 14, G + 2, CZ + e, Blocks.LANTERN);
				set(CX + e, G + 2, CZ + s * 14, Blocks.LANTERN);
			}
		}
		// A low kerb round the moat's outer edge, so nobody strolls in by accident.
		for (int x = -out - 1; x <= out + 1; x++) for (int z = -out - 1; z <= out + 1; z++) {
			int ax = Math.abs(x), az = Math.abs(z);
			if (Math.max(ax, az) != out + 1 || ax + az > 23 || ax <= 2 || az <= 2) continue;
			set(CX + x, G + 1, CZ + z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false));
		}
	}

	/** Planted corners: each corner of the square holds a stone planter with a gnarled tree and lamps. */
	private static void corners() {
		for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
			int x = CX + sx * 20, z = CZ + sz * 20;
			fill(x - 2, G + 1, z - 2, x + 2, G + 1, z + 2, Blocks.STONE_BRICKS);
			fill(x - 1, G + 1, z - 1, x + 1, G + 1, z + 1, Blocks.COARSE_DIRT);
			set(x, G + 1, z, Blocks.PODZOL);
			// A tree: a dark oak, trunk and a crown.
			fill(x, G + 2, z, x, G + 6, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
			BlockState l = leaves(Blocks.DARK_OAK_LEAVES);
			for (int dy = 0; dy < 3; dy++) {
				int r = dy == 1 ? 2 : 1;
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && r == 2) continue;
					if (dx == 0 && dz == 0 && dy < 2) continue;
					set(x + dx, G + 6 + dy, z + dz, l);
				}
			}
			// Lamps on the planter's corners.
			for (int cx : new int[]{-2, 2}) for (int cz : new int[]{-2, 2}) {
				fill(x + cx, G + 2, z + cz, x + cx, G + 3, z + cz, Blocks.POLISHED_BLACKSTONE_WALL);
				set(x + cx, G + 4, z + cz, standing());
			}
		}
	}

	/** Market stalls with striped wool awnings, barrels and crates, along the north and south sides of the square. */
	private static void stalls() {
		int[] xs = {CX - 14, CX - 9, CX + 6, CX + 11};
		for (int sz : new int[]{-1, 1}) {
			for (int i = 0; i < xs.length; i++) stall(xs[i], sz < 0 ? CZ - 22 : CZ + 20, i + (sz < 0 ? 0 : 1));
		}
	}

	/** One stall: four posts, a striped roof of wool, a counter of barrels and goods. Four wide, three deep. */
	private static void stall(int x0, int z0, int colour) {
		BlockState a = colour % 2 == 0 ? Blocks.GREEN_WOOL.defaultBlockState() : Blocks.BLACK_WOOL.defaultBlockState();
		BlockState b = Blocks.WHITE_WOOL.defaultBlockState();
		for (int x : new int[]{x0, x0 + 3}) for (int z : new int[]{z0, z0 + 2}) fill(x, G + 1, z, x, G + 3, z, Blocks.SPRUCE_FENCE);
		for (int x = x0; x <= x0 + 3; x++) for (int z = z0; z <= z0 + 2; z++) set(x, G + 4, z, ((x - x0) % 2 == 0) ? a : b);
		// The counter in the middle: barrels, hay and a rug of goods; a lantern from the roof.
		set(x0 + 1, G + 1, z0 + 1, barrel());
		set(x0 + 2, G + 1, z0 + 1, hay());
		set(x0 + 1, G + 2, z0 + 1, Blocks.GREEN_CARPET);
		set(x0 + 2, G + 2, z0 + 1, Blocks.PUMPKIN);
		set(x0 + 2, G + 3, z0 + 2, hanging());
	}

	private static void benches() {
		// Benches of stairs and slabs along the middle of each side, facing the statue.
		for (int s : new int[]{-1, 1}) {
			for (int i : new int[]{-8, -5, 5, 8}) {
				bench(CX + i, CZ + s * 19, s < 0 ? Direction.SOUTH : Direction.NORTH);
				bench(CX + s * 19, CZ + i, s < 0 ? Direction.EAST : Direction.WEST);
			}
		}
	}

	private static void bench(int x, int z, Direction facing) {
		set(x, G + 1, z, stairs(Blocks.SPRUCE_STAIRS, facing, false));
		Direction side = facing.getClockWise();
		set(x + side.getStepX(), G + 1, z + side.getStepZ(), stairs(Blocks.SPRUCE_STAIRS, facing, false));
		set(x + side.getStepX() * 2, G + 1, z + side.getStepZ() * 2, Blocks.SPRUCE_FENCE);
		set(x - side.getStepX(), G + 1, z - side.getStepZ(), Blocks.SPRUCE_FENCE);
	}

	/** Flagpoles on the diagonals: a mast of black stone with the flag of Latveria and a gold finial. */
	private static void flagpoles() {
		for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
			int x = CX + sx * 11, z = CZ + sz * 18;
			fill(x, G + 1, z, x, G + 24, z, Blocks.POLISHED_BLACKSTONE_WALL);
			set(x, G + 25, z, Blocks.GOLD_BLOCK);
			// The flag at the mast's head, hung toward the statue (half size), facing the middle of the square.
			int w = LatveriaFlag.width(2);
			int fx = sx < 0 ? x + 1 : x - w;
			LatveriaFlag.draw(fx, G + 16, z, sz < 0 ? Direction.SOUTH : Direction.NORTH, 2);
		}
	}
}
