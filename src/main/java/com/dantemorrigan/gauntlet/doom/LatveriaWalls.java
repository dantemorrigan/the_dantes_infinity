package com.dantemorrigan.gauntlet.doom;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/** The city wall: three blocks thick, nine high, with a walk and battlements, towers at the corners and the gates. */
final class LatveriaWalls {
	private LatveriaWalls() {}

	private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), BASE = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
			TOP = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

	static void build() {
		// South and north walls leave the gates open; west and east run the whole length.
		segment(-79, 127, -5, 129);
		segment(5, 127, 79, 129);
		segment(-79, -52, -5, -50);
		segment(5, -52, 79, -50);
		segment(-79, -49, -77, 126);
		segment(77, -49, 79, 126);
		// North gate, like the south one: two pillars, a lintel and battlements over the road.
		fill(-4, G + 1, -52, -3, G + 7, -50, WALL);
		fill(3, G + 1, -52, 4, G + 7, -50, WALL);
		fill(-4, G + 8, -52, 4, G + 9, -50, WALL);
		fill(-4, G + 10, -52, 4, G + 10, -50, WALL);
		for (int x = -4; x <= 4; x += 2) fill(x, G + 11, -52, x, G + 11, -50, WALL);
		set(-3, G + 8, -53, Blocks.SEA_LANTERN);
		set(3, G + 8, -53, Blocks.SEA_LANTERN);
		fill(-2, G + 7, -51, 2, G + 7, -51, Blocks.EMERALD_BLOCK);
		reserve(-4, -52, 4, -50);
		// Towers: the four corners, the middle of the east and west walls, and either side of each gate.
		tower(-78, -51);
		tower(78, -51);
		tower(-78, 128);
		tower(78, 128);
		tower(-78, 38);
		tower(78, 38);
		tower(-38, -51);
		tower(38, -51);
		tower(-38, 128);
		tower(38, 128);
		tower(-9, 128 + 1);
		tower(9, 128 + 1);
		tower(-9, -51);
		tower(9, -51);
	}

	private static void segment(int x0, int z0, int x1, int z1) {
		fill(x0, G - 3, z0, x1, G - 1, z1, Blocks.COBBLESTONE);
		fill(x0, G, z0, x1, G + 1, z1, BASE);
		fill(x0, G + 2, z0, x1, G + 8, z1, WALL);
		fill(x0, G + 9, z0, x1, G + 9, z1, TOP);
		// Weathering and cracks, a few mossy courses low down.
		for (int i = 0; i < (x1 - x0 + 1) * (z1 - z0 + 1) / 6; i++) {
			int x = rand(x0, x1), z = rand(z0, z1), y = rand(G + 2, G + 8);
			boolean face = x == x0 || x == x1 || z == z0 || z == z1;
			if (face) set(x, y, z, chance(0.6) ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		}
		// Battlements on the two long edges, every other block.
		boolean alongX = x1 - x0 > z1 - z0;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean edge = alongX ? z == z0 || z == z1 : x == x0 || x == x1;
			if (edge && ((x + z) & 1) == 0) set(x, G + 10, z, WALL);
		}
		reserve(x0, z0, x1, z1);
	}

	/** A seven-wide tower astride the wall with a copper spire, a lantern at its door side and a banner. */
	private static void tower(int cx, int cz) {
		int x0 = cx - 3, z0 = cz - 3;
		reserve(x0, z0, x0 + 6, z0 + 6);
		fill(x0, G, z0, x0 + 6, G, z0 + 6, BASE);
		int tip = LatveriaTowers.square(x0, z0, 7, G + 1, 17, 12, Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
		// A banner on the face that looks into the city.
		int bx = cx < -60 ? x0 + 7 : cx > 60 ? x0 - 1 : cx;
		int bz = cz < -40 ? z0 + 7 : cz > 120 ? z0 - 1 : cz;
		if (Math.abs(cx) > 60) fill(bx, G + 5, cz, bx, G + 12, cz, Blocks.GREEN_WOOL);
		else fill(cx, G + 5, bz, cx, G + 12, bz, Blocks.GREEN_WOOL);
	}
}
