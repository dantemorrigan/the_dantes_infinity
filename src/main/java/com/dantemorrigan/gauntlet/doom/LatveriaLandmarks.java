package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/** The buildings that give the city its places: the inn and the guildhalls on the square, the clock tower, the church. */
final class LatveriaLandmarks {
	private LatveriaLandmarks() {}

	/** Built after the square, before the ordinary houses claim the rest of the ground. */
	static void build() {
		flanks();
		clockTower();
		church();
	}

	/** Four great houses on the square's east and west sides, facing the statue. */
	private static void flanks() {
		LatveriaHouses.house(new Frame(25, 24, Direction.WEST), 17, 11, 3, false, false, true);
		LatveriaHouses.house(new Frame(25, 52, Direction.WEST), 17, 11, 3, false, false, true);
		LatveriaHouses.house(new Frame(-25, 8, Direction.EAST), 17, 11, 3, false, false, true);
		LatveriaHouses.house(new Frame(-25, 36, Direction.EAST), 17, 11, 3, false, false, true);
	}

	private static void clockTower() {
		int x0 = -21, z0 = -10, size = 7, y0 = G + 1, h = 34;
		reserve(x0 - 3, z0 - 3, x0 + size + 2, z0 + size + 2);
		fill(x0 - 1, y0 - 4, z0 - 1, x0 + size, y0 - 1, z0 + size, Blocks.STONE_BRICKS);
		fill(x0 - 1, y0, z0 - 1, x0 + size, y0, z0 + size, Blocks.STONE_BRICKS);
		fill(x0 + 1, y0 - 1, z0 + 1, x0 + size - 2, y0 - 1, z0 + size - 2, Blocks.POLISHED_DEEPSLATE);
		int tip = LatveriaTowers.square(x0, z0, size, y0 + 1, h, 12, Blocks.OXIDIZED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
		// The door on the south face, under an arch, with a lantern.
		int mx = x0 + size / 2, y = y0 + 1;
		set(mx, y, z0 + size - 1, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, false, false));
		set(mx, y + 1, z0 + size - 1, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, true, false));
		set(mx, y + 2, z0 + size - 1, AIR);
		set(mx, y + 3, z0 + size - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, true));
		set(mx, y + 2, z0 + size, hanging());
		set(mx, y + 3, z0 + size, Blocks.DEEPSLATE_BRICKS);
		// Four clock faces: white, with black hands, high on each side.
		int cy = y + 22;
		for (int d = -2; d <= 2; d++) for (int e = -2; e <= 2; e++) {
			boolean ring = Math.abs(d) == 2 || Math.abs(e) == 2;
			BlockState face = ring && Math.abs(d) + Math.abs(e) < 4 ? Blocks.WHITE_CONCRETE.defaultBlockState() : ring ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
			if (Math.abs(d) == 2 && Math.abs(e) == 2) face = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
			set(mx + d, cy + e, z0, face);
			set(mx + d, cy + e, z0 + size - 1, face);
			set(x0, cy + e, z0 + size / 2 + d, face);
			set(x0 + size - 1, cy + e, z0 + size / 2 + d, face);
		}
		// The hands, one set of them on each face: twelve and a little past three.
		for (int e = 0; e <= 2; e++) {
			set(mx, cy + e, z0, Blocks.BLACK_CONCRETE); set(mx, cy + e, z0 + size - 1, Blocks.BLACK_CONCRETE);
			set(x0, cy + e, z0 + size / 2, Blocks.BLACK_CONCRETE); set(x0 + size - 1, cy + e, z0 + size / 2, Blocks.BLACK_CONCRETE);
		}
		for (int d = 1; d <= 2; d++) {
			set(mx + d, cy, z0, Blocks.BLACK_CONCRETE); set(mx - d, cy, z0 + size - 1, Blocks.BLACK_CONCRETE);
			set(x0, cy, z0 + size / 2 + d, Blocks.BLACK_CONCRETE); set(x0 + size - 1, cy, z0 + size / 2 - d, Blocks.BLACK_CONCRETE);
		}
		// Banners down the south face on either side of the door.
		for (int s : new int[]{-1, 1}) fill(mx + s * 2, y + 6, z0 + size, mx + s * 2, y + 13, z0 + size, Blocks.GREEN_WOOL);
	}

	/** The church in the west: a long nave under a deep-slate roof, buttressed, with a bell tower beside its front, a graveyard about it. */
	private static void church() {
		Frame f = new Frame(-50, 67, Direction.SOUTH);
		int w = 11, d = 25, y0 = G + 1, top = y0 + 11;
		reserve(-72, 36, -46, 68);
		BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		f.fill(0, y0 - 4, 0, w - 1, y0 - 1, d - 1, Blocks.STONE_BRICKS);
		f.fill(0, y0, 0, w - 1, top, d - 1, wall);
		f.fill(1, y0, 1, w - 2, top + 8, d - 2, AIR);
		f.fill(1, y0 - 1, 1, w - 2, y0 - 1, d - 2, Blocks.POLISHED_DEEPSLATE);
		// A dark plinth, and corner piers.
		f.fill(0, y0, 0, w - 1, y0 + 1, d - 1, Blocks.POLISHED_DEEPSLATE);
		f.fill(1, y0, 1, w - 2, y0 + 1, d - 2, AIR);
		for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) f.fill(c[0], y0 + 2, c[1], c[0], top, c[1], Blocks.POLISHED_DEEPSLATE);
		// Buttresses along both sides and tall windows of coloured glass between them.
		for (int v = 3; v <= d - 4; v += 6) {
			for (int u : new int[]{-1, w}) {
				f.fill(u, y0, v, u, y0 + 8, v, Blocks.POLISHED_DEEPSLATE);
				f.set(u, y0 + 9, v, f.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, u < 0 ? 1 : -1, 0, false));
			}
		}
		for (int v = 6; v <= d - 6; v += 6) {
			for (int u : new int[]{0, w - 1}) {
				for (int y = y0 + 3; y <= y0 + 9; y++) f.set(u, y, v, pane(((v / 6) & 1) == 0 ? Blocks.GREEN_STAINED_GLASS_PANE : Blocks.CYAN_STAINED_GLASS_PANE, f.alongV()));
			}
		}
		// The front: a portal three wide, a pointed arch of stairs, and a rose window above it.
		f.fill(4, y0, 0, 6, y0 + 4, 0, AIR);
		f.set(4, y0 + 5, 0, f.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, 1, 0, true));
		f.set(6, y0 + 5, 0, f.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, -1, 0, true));
		f.fill(3, y0, 0, 3, y0 + 5, 0, Blocks.POLISHED_DEEPSLATE);
		f.fill(7, y0, 0, 7, y0 + 5, 0, Blocks.POLISHED_DEEPSLATE);
		f.set(5, y0 + 5, -1, wall);
		f.set(5, y0 + 4, -1, hanging());
		for (int du = -2; du <= 2; du++) for (int dy = -2; dy <= 2; dy++) {
			if (du * du + dy * dy > 6) continue;
			f.set(5 + du, y0 + 8 + dy, 0, ((du + dy) & 1) == 0 ? Blocks.GREEN_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS);
		}
		// The roof and the bell tower beside the front.
		LatveriaHouses.roof(f, w, d, top + 1, Blocks.DEEPSLATE_TILE_STAIRS, TILE_CAP, wall, false, true, true);
		int tip = LatveriaTowers.square(-67, 61, 7, y0, 30, 24, Blocks.DEEPSLATE_TILE_STAIRS, TILE_CAP);
		fill(-64, y0, 67, -64, y0 + 2, 67, AIR);
		set(-64, y0 + 3, 67, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, true));
		set(-64, y0 + 2, 68, hanging());
		set(-64, y0 + 3, 68, Blocks.DEEPSLATE_BRICKS);
		// Inside: a red runner, pews on either side, the altar, and chandeliers from the ridge.
		f.fill(5, y0, 2, 5, y0, d - 6, Blocks.RED_CARPET);
		for (int v = 5; v <= d - 8; v += 2) {
			for (int u : new int[]{1, 2, 3, 7, 8, 9}) f.set(u, y0, v, slab(Blocks.SPRUCE_SLAB, false));
			f.set(1, y0 + 1, v, Blocks.SPRUCE_FENCE);
			f.set(9, y0 + 1, v, Blocks.SPRUCE_FENCE);
		}
		f.fill(3, y0, d - 5, 7, y0, d - 3, Blocks.POLISHED_BLACKSTONE);
		f.fill(4, y0 + 1, d - 4, 6, y0 + 1, d - 3, Blocks.POLISHED_BLACKSTONE_BRICKS);
		f.set(4, y0 + 2, d - 3, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
		f.set(6, y0 + 2, d - 3, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
		for (int v : new int[]{5, 11, 17}) {
			f.fill(5, y0 + 13, v, 5, y0 + 16, v, Blocks.IRON_CHAIN.defaultBlockState());
			f.set(5, y0 + 12, v, hanging());
		}
		// The graveyard round it: headstones, a few trees, boulders, a hedge of mossy wall along the lane.
		LatveriaProps.graveyard(-72, 36, -62, 59);
		LatveriaProps.graveyard(-62, 36, -46, 41);
		LatveriaProps.graveyard(-49, 43, -46, 66);
	}

	private static final BlockState TILE_CAP = Blocks.DEEPSLATE_TILES.defaultBlockState();
}
