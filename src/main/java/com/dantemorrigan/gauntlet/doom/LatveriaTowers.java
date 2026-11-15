package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * Towers: square or round shafts of stone with corbelled galleries, pointed arch windows, buttresses and steep
 * spires of stairs. Shared by the clock tower, the city wall, the church and the castle's many spires.
 */
final class LatveriaTowers {
	private LatveriaTowers() {}

	static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), WALL2 = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
			MOSSY = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();

	/** A wall material with a little weathering mixed in, chosen per block row so it reads as laid stone. */
	static BlockState stone(int x, int y, int z) {
		int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
		h = (h >>> 8) & 15;
		return h == 0 ? MOSSY : h == 1 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : WALL;
	}

	/**
	 * A square tower: shaft x0..x0+size-1 by z0..z0+size-1 from y0 to y0+height-1, hollow, with a corbelled battlement
	 * gallery at the top (one block out all round), tall arched slits, a belt course every ten blocks, and a stair-roofed
	 * spire of {@code spire} blocks over it. Returns the y of the spire's tip.
	 */
	static int square(int x0, int z0, int size, int y0, int height, int spire, Block roof, BlockState cap) {
		int x1 = x0 + size - 1, z1 = z0 + size - 1, top = y0 + height - 1;
		fill(x0, y0, z0, x1, top, z1, WALL);
		// A little weathering over the faces.
		for (int i = 0; i < height * size / 2; i++) {
			int x = x0 + RND.nextInt(size), z = z0 + RND.nextInt(size), y = y0 + RND.nextInt(height);
			if (x != x0 && x != x1 && z != z0 && z != z1) continue;
			set(x, y, z, stone(x, y, z));
		}
		fill(x0 + 1, y0, z0 + 1, x1 - 1, top, z1 - 1, AIR);
		// Corner piers and belt courses.
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], y0, c[1], c[0], top, c[1], WALL2);
		for (int y = y0 + 9; y < top - 3; y += 10) fill(x0, y, z0, x1, y, z1, WALL2);
		// Floors between the belt courses: a room on each, reached by a ladder.
		if (size >= 6) {
			int floor = y0 - 1;
			for (int y = y0 + 9; ; y += 10) {
				int ceiling = y < top - 3 ? y : top;
				LatveriaInteriors.shaftFloor(x0, z0, size, floor, ceiling, RND.nextInt(8));
				if (ceiling >= top) break;
				floor = ceiling;
			}
		}
		// Tall arched slits on each face.
		int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
		for (int y = y0 + 4; y < top - 6; y += 10) {
			for (int dy = 0; dy < 4; dy++) {
				set(mx, y + dy, z0, AIR); set(mx, y + dy, z1, AIR); set(x0, y + dy, mz, AIR); set(x1, y + dy, mz, AIR);
			}
			set(mx, y + 4, z0, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, true));
			set(mx, y + 4, z1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, true));
			set(x0, y + 4, mz, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST, true));
			set(x1, y + 4, mz, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, true));
		}
		// The gallery: one block out, held up by a course of upside-down stairs, with a crenellated parapet.
		for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
			boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
			if (!edge) continue;
			Direction d = x == x0 - 1 ? Direction.WEST : x == x1 + 1 ? Direction.EAST : z == z0 - 1 ? Direction.NORTH : Direction.SOUTH;
			set(x, top - 1, z, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, d.getOpposite(), true));
			set(x, top, z, WALL2);
			if (((x + z) & 1) == 0) set(x, top + 1, z, WALL);
		}
		fill(x0, top, z0, x1, top, z1, WALL2);
		// The roof of the stair: a pyramid stepping in one block a row, then a finial.
		int y = top + 1;
		int half = size / 2;
		int rep = Math.max(1, spire / (half + 1));
		for (int i = 0; i <= half; i++) {
			int a = x0 + i, b = x1 - i, c = z0 + i, d = z1 - i;
			if (a > b || c > d) break;
			for (int k = 0; k < rep; k++, y++) {
				// A steep roof: a few plain courses, then the course of stairs that turns it in.
				if (k < rep - 1 && a < b && c < d) fill(a, y, c, b, y, d, cap);
				else ring(a, c, b, d, y, roof, cap, a == b || c == d);
			}
		}
		fill(mx, y, mz, mx, y + 2, mz, Blocks.POLISHED_BLACKSTONE_WALL);
		set(mx, y + 3, mz, Blocks.GOLD_BLOCK);
		return y + 3;
	}

	private static void ring(int a, int c, int b, int d, int y, Block roof, BlockState cap, boolean solid) {
		if (solid) {
			fill(a, y, c, b, y, d, cap);
			return;
		}
		for (int x = a; x <= b; x++) {
			set(x, y, c, stairs(roof, Direction.SOUTH, false));
			set(x, y, d, stairs(roof, Direction.NORTH, false));
		}
		for (int z = c + 1; z <= d - 1; z++) {
			set(a, y, z, stairs(roof, Direction.EAST, false));
			set(b, y, z, stairs(roof, Direction.WEST, false));
		}
		// Corners as plain blocks, so the stairs of two sides do not clash.
		set(a, y, c, cap); set(b, y, c, cap); set(a, y, d, cap); set(b, y, d, cap);
		// Under the ring: filled, so the spire is solid and has no hollow to fall into.
		fill(a + 1, y, c + 1, b - 1, y, d - 1, cap);
	}
}
