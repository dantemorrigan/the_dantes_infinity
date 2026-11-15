package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * Latveria's city: a grid of streets, the great square, and between the streets blocks of houses built shoulder to
 * shoulder around their courtyards, every house facing a street (or the wall's lane), with the landmarks standing in
 * their own lots. The ground is level, so everything is laid out on plain coordinates; nothing is placed where
 * something already is (see {@link LatveriaKit#free}).
 */
final class LatveriaCity {
	/** Street centres: north-south, and east-west; all five blocks wide. */
	static final int[] NS = {-40, 0, 40};
	static final int[] EW = {-18, 30, 72, 100};
	static final int MAIN_Z0 = -50, MAIN_Z1 = 126;

	private LatveriaCity() {}

	static void build() {
		LatveriaWalls.build();
		streets();
		landmarks();
		blocks();
		LatveriaProps.dress();
		// Extra townsfolk strolling the streets, square, and market lanes.
		for (int[] p : new int[][]{
				{-12, 22}, {12, 22}, {-14, 38}, {14, 38}, {-10, 54}, {10, 54},
				{0, 14}, {0, 62}, {-38, 12}, {38, 12}, {-38, 52}, {38, 52},
				{-22, 72}, {22, 72}, {-16, 100}, {16, 100}
		}) {
			LatveriaPlan.VILLAGERS.add(new net.minecraft.core.BlockPos(p[0], G + 1, p[1]));
		}
	}

	// ------------------------------------------------------------------ streets

	private static void streets() {
		int y = G;
		// The roadway: cobblestone with a brick spine on the main street, and worn patches.
		for (int cx : NS) road(cx - 2, MAIN_Z0, cx + 2, MAIN_Z1);
		for (int cz : EW) road(-74, cz - 2, 74, cz + 2);
		fill(-1, y, MAIN_Z0, 1, y, MAIN_Z1, Blocks.STONE_BRICKS);
		fill(-74, y, 29, 74, y, 31, Blocks.STONE_BRICKS);
		// Sidewalks of stone brick either side of every street, one block of kerb away from the road.
		for (int cx : NS) {
			walk(cx - 4, MAIN_Z0, cx - 3, MAIN_Z1);
			walk(cx + 3, MAIN_Z0, cx + 4, MAIN_Z1);
		}
		for (int cz : EW) {
			walk(-74, cz - 4, 74, cz - 3);
			walk(-74, cz + 3, 74, cz + 4);
		}
		// Worn patches of gravel, moss and andesite in the cobbles.
		for (int i = 0; i < 700; i++) {
			int x = rand(-74, 74), z = rand(MAIN_Z0, MAIN_Z1);
			if (!onRoad(x, z) || Math.abs(x) <= 1 || z >= 29 && z <= 31) continue;
			set(x, y, z, pick(Blocks.GRAVEL, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.COBBLED_DEEPSLATE, Blocks.COBBLESTONE));
		}
	}

	static boolean onRoad(int x, int z) {
		for (int cx : NS) if (Math.abs(x - cx) <= 2 && z >= MAIN_Z0 && z <= MAIN_Z1) return true;
		for (int cz : EW) if (Math.abs(z - cz) <= 2 && Math.abs(x) <= 74) return true;
		return false;
	}

	private static void road(int x0, int z0, int x1, int z1) {
		fill(x0, G, z0, x1, G, z1, Blocks.COBBLESTONE);
		reserve(x0, z0, x1, z1);
	}

	private static void walk(int x0, int z0, int x1, int z1) {
		fill(x0, G, z0, x1, G, z1, Blocks.STONE_BRICKS);
	}

	// ------------------------------------------------------------------ landmarks (given their ground before the houses)

	private static void landmarks() {
		// The great square with the statue of Doom in the middle of its moat.
		LatveriaSquare.build();
		LatveriaLandmarks.build();
	}

	// ------------------------------------------------------------------ blocks of houses

	/** Rows of zones: front-wall lines of the north and south rows. {x0, x1} for the four columns. */
	private static final int[][] COLUMNS = {{-73, -45}, {-35, -5}, {5, 35}, {45, 73}};

	private static void blocks() {
		int[][] zones = {{-45, -23}, {-13, 5}, {-13, 25}, {35, 67}, {55, 67}, {77, 95}, {105, 123}};
		for (int c = 0; c < COLUMNS.length; c++) {
			boolean inner = c == 1 || c == 2;
			int x0 = COLUMNS[c][0], x1 = COLUMNS[c][1];
			ring(x0, -45, x1, -23);
			if (inner) ring(x0, -13, x1, 5);
			else ring(x0, -13, x1, 25);
			if (inner) ring(x0, 55, x1, 67);
			else ring(x0, 35, x1, 67);
			ring(x0, 77, x1, 95);
			ring(x0, 105, x1, 123);
		}
	}

	/** A block of houses around a courtyard; the rectangle is the line of the front walls. */
	private static void ring(int x0, int z0, int x1, int z1) {
		int dz = z1 - z0 + 1, dx = x1 - x0 + 1;
		int dN = Math.min(rand(8, 11), Math.max(6, (dz - 3) / 2)), dS = Math.min(rand(8, 11), Math.max(6, (dz - 3) / 2));
		if (dz < 14) { dN = dz; dS = 0; }
		int dW = Math.min(rand(8, 10), Math.max(6, (dx - 3) / 2)), dE = Math.min(rand(8, 10), Math.max(6, (dx - 3) / 2));
		row(Direction.NORTH, z0, x0, x1, dN);
		if (dS > 0) row(Direction.SOUTH, z1, x0, x1, dS);
		int a0 = z0 + dN + 1, a1 = dS > 0 ? z1 - dS - 1 : z1;
		if (a1 - a0 >= 6 && dx >= 18) {
			row(Direction.WEST, x0, a0, a1, dW);
			row(Direction.EAST, x1, a0, a1, dE);
		}
		LatveriaProps.courtyard(x0 + dW + 1, z0 + dN + 1, x1 - dE - 1, (dS > 0 ? z1 - dS : z1) - 1);
	}

	private record Lot(int a, int w, boolean gapAfter) {}

	/** A row of houses along one side: fronts on the line {@code fixed}, frontage from {@code a0} to {@code a1}. */
	private static void row(Direction front, int fixed, int a0, int a1, int depth) {
		List<Lot> lots = new ArrayList<>();
		int a = a0;
		while (a1 - a + 1 >= 6) {
			int w = rand(7, 11), left = a1 - a + 1;
			if (left - w < 6) w = Math.min(left, 14);
			boolean gap = chance(0.35);
			int next = a + w + (gap ? rand(1, 2) : 0);
			lots.add(new Lot(a, w, gap));
			a = next;
		}
		boolean forward = front == Direction.NORTH || front == Direction.EAST;
		for (int i = 0; i < lots.size(); i++) {
			Lot lot = lots.get(i);
			boolean abutPrev = i > 0 && !lots.get(i - 1).gapAfter, abutNext = i < lots.size() - 1 && !lot.gapAfter;
			int floors = depth >= 8 && chance(0.4) ? 3 : 2;
			int d = Math.max(6, depth - (chance(0.3) ? 1 : 0));
			int ox, oz;
			switch (front) {
				case NORTH -> { ox = lot.a; oz = fixed; }
				case SOUTH -> { ox = lot.a + lot.w - 1; oz = fixed; }
				case WEST -> { ox = fixed; oz = lot.a + lot.w - 1; }
				default -> { ox = fixed; oz = lot.a; }
			}
			Frame f = new Frame(ox, oz, front);
			if (!f.free(0, 0, lot.w - 1, d - 1)) continue;
			boolean abutLeft = forward ? abutPrev : abutNext, abutRight = forward ? abutNext : abutPrev;
			LatveriaHouses.house(f, lot.w, d, floors, abutLeft, abutRight, chance(0.4));
		}
	}
}
