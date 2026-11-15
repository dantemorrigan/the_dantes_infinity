package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * Castle Doom as it stands in the comics: a black fortress on the mountain that climbs the sky. The old halls, yard and
 * keep keep their places (the throne room is where it was, the doors and the generator too); what is built here goes up
 * round and above them: round towers on the outer wall, gate towers with their spires, great square towers on the keep's
 * four corners, the central tower of Doom rising in stages through the clouds to a needle with a golden point, flying
 * buttresses, a pierced and pillared front, and weathering, banners and lanterns over everything.
 */
final class LatveriaCastle {
	private static final int F = LatveriaPlan.FLOOR;
	/** Tips of the spires the storm strikes (filled in as they are built). */
	static final List<int[]> SPIRE_TIPS = new ArrayList<>();

	private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), WALL2 = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
			CRACK = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), TILE = Blocks.DEEPSLATE_TILES.defaultBlockState(),
			BLACK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), GREEN = Blocks.GREEN_WOOL.defaultBlockState(), GOLD = Blocks.GOLD_BLOCK.defaultBlockState(),
			GLASS = Blocks.GREEN_STAINED_GLASS.defaultBlockState();

	private LatveriaCastle() {}

	static void build() {
		wingRoofs();
		outerTowers();
		gateTowers();
		keepTowers();
		centralTower();
		buttresses();
		facade();
		banners();
		weather();
	}

	// ------------------------------------------------------------------ the outer wall

	private static void outerTowers() {
		// The four corners: round towers of the old plan, much taller, the front ones lower than the back.
		LatveriaPlan.tower(-45, -205, 6, F, F + 36, 12);
		LatveriaPlan.tower(45, -205, 6, F, F + 36, 12);
		LatveriaPlan.tower(-45, -297, 6, F, F + 46, 14);
		LatveriaPlan.tower(45, -297, 6, F, F + 46, 14);
		roundFloors(-45, -205, 6, F, F + 36);
		roundFloors(45, -205, 6, F, F + 36);
		roundFloors(-45, -297, 6, F, F + 46);
		roundFloors(45, -297, 6, F, F + 46);
		SPIRE_TIPS.add(new int[]{-45, F + 36 + 12 + 2, -205});
		SPIRE_TIPS.add(new int[]{45, F + 36 + 12 + 2, -205});
		SPIRE_TIPS.add(new int[]{-45, F + 46 + 14 + 2, -297});
		SPIRE_TIPS.add(new int[]{45, F + 46 + 14 + 2, -297});
		// Square towers astride the wall at the middle of the west, east and north sides.
		midTower(-49, -255);
		midTower(41, -255);
		midTower(-4, -301);
	}

	private static void midTower(int x0, int z0) {
		int size = x0 == -4 ? 9 : 9;
		int tip = LatveriaTowers.square(x0, z0, size, F, 30, 18, Blocks.DEEPSLATE_TILE_STAIRS, TILE);
		SPIRE_TIPS.add(new int[]{x0 + size / 2, tip, z0 + size / 2});
	}

	// ------------------------------------------------------------------ the gatehouse

	private static void gateTowers() {
		for (int s : new int[]{-1, 1}) {
			int x0 = s < 0 ? -9 : 4;
			fill(x0, F + 17, -208, x0 + 5, F + 17, -203, WALL);
			int tip = LatveriaTowers.square(x0, -208, 6, F + 18, 26, 24, Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
			SPIRE_TIPS.add(new int[]{x0 + 3, tip, -206});
		}
	}

	// ------------------------------------------------------------------ the keep's great towers and the central tower

	private static void keepTowers() {
		// Back corners: big, 11 wide. Front corners: slighter, 9 wide.
		int[][] t = {{-38, -296, 11, 66, 30}, {28, -296, 11, 66, 30}, {-43, -260, 9, 48, 22}, {35, -260, 9, 48, 22}};
		for (int[] a : t) {
			fill(a[0] - 1, F + 18, a[1] - 1, a[0] + a[2], F + 19, a[1] + a[2], WALL2);
			int tip = LatveriaTowers.square(a[0], a[1], a[2], F + 20, a[3], a[4], Blocks.DEEPSLATE_TILE_STAIRS, TILE);
			SPIRE_TIPS.add(new int[]{a[0] + a[2] / 2, tip, a[1] + a[2] / 2});
		}
	}

	/** Doom's tower: three stages stepping in, then the needle. */
	private static void centralTower() {
		int y = F + 19;
		// Stage A: 25 by 25, fifty-two high, buttressed at the corners.
		stage(-12, -285, 25, y, 52, true);
		int yb = y + 52;
		// Stage B: 17 by 17, fifty-six high.
		stage(-8, -281, 17, yb, 56, false);
		int yc = yb + 56;
		// Stage C and the needle: 11 by 11, forty high, then a spire of thirty-six.
		int tip = LatveriaTowers.square(-5, -278, 11, yc, 40, 36, Blocks.DEEPSLATE_TILE_STAIRS, TILE);
		SPIRE_TIPS.add(new int[]{0, tip, -273});
		// A lightning rod above the golden point, and green lanterns along the needle.
		fill(0, tip + 1, -273, 0, Math.min(tip + 6, 316), -273, Blocks.LIGHTNING_ROD.defaultBlockState());
	}

	/** One stage: a hollow shaft with a belt of arched windows, piers on the corners, a chandelier inside. */
	private static void stage(int x0, int z0, int size, int y0, int height, boolean wide) {
		int x1 = x0 + size - 1, z1 = z0 + size - 1, top = y0 + height - 1, mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
		fill(x0, y0, z0, x1, top, z1, WALL);
		fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, top, z1 - 1, AIR);
		// Weathering: scattered cracked bricks, and belt courses of polished stone every twelve blocks.
		for (int i = 0; i < height * size; i++) {
			int x = x0 + RND.nextInt(size), z = z0 + RND.nextInt(size), y = y0 + RND.nextInt(height);
			if (x != x0 && x != x1 && z != z0 && z != z1) continue;
			set(x, y, z, CRACK);
		}
		for (int y = y0 + 11; y < top - 1; y += 12) fill(x0, y, z0, x1, y, z1, WALL2);
		// Piers on the corners and the faces' thirds, standing a block proud.
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			int px = c[0] == x0 ? x0 - 1 : x1, pz = c[1] == z0 ? z0 - 1 : z1;
			fill(px, y0, pz, px + 1, top, pz + 1, WALL2);
		}
		// Windows: tall pointed lights of green glass, lit from inside, three to a face in the broad stage.
		int count = wide ? 3 : 2;
		for (int band = 0; band < height / 12; band++) {
			int wy = y0 + 2 + band * 12;
			for (int k = 0; k < count; k++) {
				int off = (k - (count - 1) / 2) * (size / (count + 1)) - (count % 2 == 0 ? size / (2 * (count + 1)) : 0);
				window(mx + off, wy, z0, Direction.Axis.X);
				window(mx + off, wy, z1, Direction.Axis.X);
				window(x0, wy, mz + off, Direction.Axis.Z);
				window(x1, wy, mz + off, Direction.Axis.Z);
			}
		}
		// Floors: on each, four rooms round the middle, and a ladder in the north-west corner up through the slab.
		int floor = y0;
		for (int y = y0 + 11; ; y += 12) {
			int ceiling = y < top - 1 ? y : top;
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
			fill(x0 + 1, floor + 1, z0 + 1, x0 + 1, ceiling, z0 + 1, ladder);
			int half = size / 2;
			for (int qx = 0; qx < 2; qx++) for (int qz = 0; qz < 2; qz++) {
				LatveriaInteriors.towerRoom(new Frame(x0 + qx * half, z0 + qz * half, Direction.NORTH), half + 1, half + 1, floor + 1, RND.nextInt(8));
			}
			if (ceiling >= top) break;
			floor = ceiling;
		}
		// Light inside: under each floor slab a chandelier hangs on a chain.
		for (int y = y0 + 11; y < top - 1; y += 12) {
			fill(mx, y - 4, mz, mx, y - 1, mz, Blocks.IRON_CHAIN.defaultBlockState());
			set(mx, y - 5, mz, hanging());
		}
		// The cap: a gallery of upside-down stairs and a crenellated parapet, then the floor of the next stage.
		for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
			boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
			if (!edge) continue;
			Direction d = x == x0 - 1 ? Direction.WEST : x == x1 + 1 ? Direction.EAST : z == z0 - 1 ? Direction.NORTH : Direction.SOUTH;
			set(x, top, z, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, d.getOpposite(), true));
			set(x, top + 1, z, WALL2);
			if (((x + z) & 1) == 0) set(x, top + 2, z, WALL);
		}
		fill(x0, top, z0, x1, top, z1, WALL2);
		// Pinnacles on the four corners of the gallery.
		for (int[] c : new int[][]{{x0 - 1, z0 - 1}, {x1 + 1, z0 - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}}) pinnacle(c[0], c[1], top + 2, 9);
	}

	/** A tall pointed light of green glass, two wide and eight high, in a wall running along the given axis. */
	private static void window(int c, int y, int wall, Direction.Axis along) {
		BlockState pane = pane(Blocks.GREEN_STAINED_GLASS_PANE, along);
		for (int dy = 0; dy < 8; dy++) {
			if (along == Direction.Axis.X) { set(c, y + dy, wall, pane); set(c + 1, y + dy, wall, pane); }
			else { set(wall, y + dy, c, pane); set(wall, y + dy, c + 1, pane); }
		}
	}

	/** A small spire: a square shaft with a stepped point and a finial, set on a gallery corner. */
	private static void pinnacle(int x, int z, int y, int h) {
		fill(x, y, z, x, y + h - 3, z, WALL2);
		set(x, y + h - 2, z, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, false));
		set(x, y + h - 1, z, Blocks.POLISHED_BLACKSTONE_WALL);
		set(x, y + h, z, GOLD);
	}

	// ------------------------------------------------------------------ flying buttresses

	/** From the central tower's first stage out and down to piers on the keep's roof, on all four corners. */
	private static void buttresses() {
		int topA = F + 19 + 52;
		for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
			int px = sx * 26, pz = -273 + sz * 18;
			// The pier on the roof.
			fill(px - 1, F + 19, pz - 1, px + 1, F + 39, pz + 1, WALL2);
			pinnacle(px, pz, F + 40, 8);
			// The arch: a curve from the pier's head to the stage's wall, built of two-by-two blocks.
			int x1 = sx * 13, z1 = -273 + sz * 8, y1 = topA - 14;
			arch(px, F + 36, pz, x1, y1, z1, 6);
		}
	}

	/** A curved rib between two points, of 2 by 2 blocks, rising to a hump of {@code bulge} blocks over the straight line. */
	private static void arch(int x0, int y0, int z0, int x1, int y1, int z1, int bulge) {
		int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(z1 - z0), Math.abs(y1 - y0))) * 2;
		int px = x0, py = y0, pz = z0;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			int x = (int) Math.round(x0 + (x1 - x0) * t), z = (int) Math.round(z0 + (z1 - z0) * t);
			int y = (int) Math.round(y0 + (y1 - y0) * t + Math.sin(Math.PI * t) * bulge);
			// Fill between the last cell and this one so the rib stays one connected piece.
			fill(Math.min(px, x), Math.min(py, y), Math.min(pz, z), Math.max(px, x) + 1, Math.max(py, y) + 1, Math.max(pz, z) + 1, WALL2);
			px = x; py = y; pz = z;
		}
	}

	// ------------------------------------------------------------------ the front

	/** The keep's south face: piers a block proud between pointed windows, and gargoyles at their heads. */
	private static void facade() {
		int z = -252;
		for (int x : new int[]{-37, -30, -23, -10, 8, 21, 28, 35}) {
			fill(x, F, z + 1, x + 2, F + 21, z + 2, WALL2);
			fill(x - 1, F, z + 1, x + 3, F + 2, z + 3, BLACK);
			// A gargoyle: a head of chiselled stone with stairs for a jaw, looking out over the yard.
			set(x + 1, F + 20, z + 3, Blocks.CHISELED_DEEPSLATE);
			set(x + 1, F + 19, z + 3, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, true));
			pinnacle(x + 1, z + 1, F + 22, 7);
		}
		// Tall windows between the piers.
		for (int x : new int[]{-34, -27, 25, 32}) fill(x, F + 4, -253, x + 1, F + 15, z, GLASS);
	}

	/** Roofs of stairs over the wings, ridges running north to south, rising above the keep's battlements. */
	private static void wingRoofs() {
		Frame west = new Frame(-40, -284, Direction.NORTH);
		LatveriaHouses.roof(west, 17, 23, F + 19, Blocks.DEEPSLATE_TILE_STAIRS, TILE, WALL, false, true, true);
		Frame east = new Frame(24, -284, Direction.NORTH);
		LatveriaHouses.roof(east, 17, 23, F + 19, Blocks.DEEPSLATE_TILE_STAIRS, TILE, WALL, false, true, true);
	}

	// ------------------------------------------------------------------ the finishing touches

	private static void banners() {
		// Great green banners down the south face of the keep and the gate towers, hung from gold bars.
		// The flag of Latveria, huge, on the central tower's front above the roofs.
		LatveriaFlag.draw(-12, F + 22, -260, Direction.SOUTH, 1);
		for (int x : new int[]{-26, -14, 12, 24}) {
			fill(x, F + 6, -251, x + 1, F + 16, -251, GREEN);
			fill(x, F + 17, -251, x + 1, F + 17, -251, GOLD);
		}
	}

	/** Ivy and moss on the outer walls: strands of vine hanging from the battlements, longer where the wall is old. */
	private static void weather() {
		int top = F + 10;
		// South wall (two stretches, the gatehouse between), north wall, west and east walls; each strand clings to the wall's outer face.
		for (int x = -37; x <= 37; x++) {
			if (Math.abs(x) <= 11) continue;
			if (chance(0.22)) strand(x, -203, Direction.NORTH, top);
			if (chance(0.22)) strand(x, -299, Direction.SOUTH, top);
		}
		for (int z = -289; z <= -213; z++) {
			if (z >= -259 && z <= -243) continue; // the middle towers
			if (chance(0.2)) strand(-47, z, Direction.EAST, top);
			if (chance(0.2)) strand(47, z, Direction.WEST, top);
		}
	}

	/** A strand of vine down the wall from just under the parapet, at (x, z), clinging toward {@code wall}. */
	private static void strand(int x, int z, Direction wall, int top) {
		int len = rand(3, 10);
		for (int i = 0; i < len; i++) set(x, top - i, z, vine(wall));
	}

	/** Floors in a round tower every seven blocks: a disc of planks, a ladder up the north wall, and a room on each. */
	private static void roundFloors(int cx, int cz, int r, int y0, int y1) {
		int in = r - 2;
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		int floor = y0;
		for (int y = y0 + 7; ; y += 7) {
			int ceiling = y < y1 - 3 ? y : y1;
			if (ceiling < y1) {
				for (int dz = -in; dz <= in; dz++) {
					int half = (int) Math.round(Math.sqrt(in * in + in - dz * dz));
					fill(cx - half, ceiling, cz + dz, cx + half, ceiling, cz + dz, Blocks.SPRUCE_PLANKS);
				}
			}
			fill(cx, floor + 1, cz - in, cx, ceiling, cz - in, ladder);
			LatveriaInteriors.towerRoom(new Frame(cx - 3, cz - 3, Direction.NORTH), 7, 7, floor + 1, RND.nextInt(8));
			set(cx, ceiling - 1, cz, hanging());
			if (ceiling >= y1) break;
			floor = ceiling;
		}
	}
}
