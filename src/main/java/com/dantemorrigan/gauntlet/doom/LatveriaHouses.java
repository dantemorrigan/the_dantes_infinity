package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * Latveria's townhouses. One generator builds them all in a building's own coordinates (u along the front, v into the
 * house, y up from the street), so a house of any width and depth stands the same way facing any side: a stone plinth, a
 * half-timbered or stone wall, one to three storeys with real floors and a ladder, windows with panes and shutters, a
 * door under a hood with its lantern, and a steep gable roof of stairs with eaves and a chimney.
 */
final class LatveriaHouses {
	private LatveriaHouses() {}

	/** What a house is made of. */
	private record Palette(BlockState base, BlockState infill, Block frame, boolean timber, Block roof, BlockState roofCap, BlockState floor, Block trim,
			BlockState canopy) {}

	private static Palette palette() {
		boolean timber = chance(0.55);
		BlockState base = pick(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.DEEPSLATE_BRICKS, Blocks.ANDESITE);
		BlockState infill = timber
				? pick(Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.GRAY_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.MUD_BRICKS)
				: pick(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.TUFF_BRICKS);
		Block frame = pick(Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG).getBlock();
		int r = RND.nextInt(10);
		Block roof = r < 4 ? Blocks.DARK_OAK_STAIRS : r < 7 ? Blocks.DEEPSLATE_TILE_STAIRS : r < 9 ? Blocks.OXIDIZED_CUT_COPPER_STAIRS : Blocks.SPRUCE_STAIRS;
		BlockState cap = roof == Blocks.DARK_OAK_STAIRS ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
				: roof == Blocks.DEEPSLATE_TILE_STAIRS ? Blocks.DEEPSLATE_TILES.defaultBlockState()
				: roof == Blocks.OXIDIZED_CUT_COPPER_STAIRS ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
		return new Palette(base, infill, frame, timber, roof, cap, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS,
				Blocks.DARK_OAK_PLANKS.defaultBlockState());
	}

	/**
	 * A house whose front-left corner (seen from the street) is at the frame's origin. {@code abutLeft}/{@code abutRight}: a
	 * neighbour shares that side wall, so the roof ends flush and nothing hangs over.
	 */
	static void house(Frame f, int w, int d, int floors, boolean abutLeft, boolean abutRight, boolean inhabited) {
		Palette p = palette();
		int y0 = G + 1, h = 4 * floors, top = y0 + h - 1;
		int wc = w / 2;
		// Footprint, floor and shell.
		f.fill(0, y0 - 4, 0, w - 1, y0 - 1, d - 1, p.base);
		f.fill(0, y0, 0, w - 1, top, d - 1, p.infill);
		f.fill(1, y0, 1, w - 2, top, d - 2, AIR);
		f.fill(1, y0 - 1, 1, w - 2, y0 - 1, d - 2, p.floor);
		// Plinth course.
		ring(f, 0, y0, 0, w - 1, y0, d - 1, p.base);
		// Corner posts, and beams at each floor line.
		for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) f.fill(c[0], y0 + 1, c[1], c[0], top, c[1], log(p.frame, Direction.Axis.Y));
		if (p.timber) {
			for (int k = 0; k < floors; k++) {
				int by = y0 + 4 * k + 3;
				f.fill(1, by, 0, w - 2, by, 0, f.beam(p.frame, true));
				f.fill(1, by, d - 1, w - 2, by, d - 1, f.beam(p.frame, true));
				f.fill(0, by, 1, 0, by, d - 2, f.beam(p.frame, false));
				f.fill(w - 1, by, 1, w - 1, by, d - 2, f.beam(p.frame, false));
			}
			for (int u = 4; u < w - 2; u += 4) {
				f.fill(u, y0 + 1, 0, u, top, 0, log(p.frame, Direction.Axis.Y));
				f.fill(u, y0 + 1, d - 1, u, top, d - 1, log(p.frame, Direction.Axis.Y));
			}
		} else {
			// Stone houses wear a trim of dark wood at the floor lines instead.
			for (int k = 0; k < floors; k++) {
				int by = y0 + 4 * k + 3;
				ring(f, 0, by, 0, w - 1, by, d - 1, p.trim.defaultBlockState());
			}
		}
		// Floors above the street one, a hole for the ladder, and the ladder to the top floor.
		for (int k = 0; k < floors; k++) {
			int by = y0 + 4 * k + 3;
			f.fill(1, by, 1, w - 2, by, d - 2, p.floor);
			if (k < floors - 1) f.set(1, by, d - 2, AIR);
		}
		if (floors > 1) {
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1));
			f.fill(1, y0, d - 2, 1, y0 + 4 * (floors - 1) + 1, d - 2, ladder);
		}
		// The door and its hood, lantern and frame.
		f.fill(wc, y0, 0, wc, y0 + 2, 0, AIR);
		f.fill(wc - 1, y0, 0, wc - 1, y0 + 3, 0, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
		f.fill(wc + 1, y0, 0, wc + 1, y0 + 3, 0, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
		f.set(wc, y0 + 3, 0, p.canopy);
		f.set(wc, y0 + 3, -1, p.canopy);
		f.set(wc - 1, y0 + 3, -1, slab(Blocks.DARK_OAK_SLAB, true));
		f.set(wc + 1, y0 + 3, -1, slab(Blocks.DARK_OAK_SLAB, true));
		f.set(wc, y0 + 2, -1, hanging());
		// Windows: panes in the wall, shutters flat against it.
		for (int k = 0; k < floors; k++) {
			int wy = y0 + 4 * k + 1;
			for (int u = 2; u <= w - 3; u += 3) {
				if (k == 0 && Math.abs(u - wc) <= 1) continue;
				window(f, u, wy, 0, f.alongU(), true, p);
			}
			if (w >= 7) window(f, w / 2 + (w % 2 == 0 ? -1 : 0), wy, d - 1, f.alongU(), false, p);
			if (!abutLeft) window(f, 0, wy, d / 2, f.alongV(), false, p);
			if (!abutRight) window(f, w - 1, wy, d / 2, f.alongV(), false, p);
		}
		BlockState gableWall = p.base.getBlock() == Blocks.COBBLESTONE || p.base.getBlock() == Blocks.MOSSY_COBBLESTONE ? Blocks.STONE_BRICKS.defaultBlockState() : p.base;
		roof(f, w, d, top + 1, p.roof, p.roofCap, gableWall, abutLeft || abutRight || chance(0.6), !abutLeft, !abutRight);
		// A chimney through the roof and a pot on it.
		int cu = Math.max(1, w - 3), cv = d / 2;
		f.fill(cu, top + 1, cv, cu, top + 1 + d / 2 + 2, cv, Blocks.BRICKS);
		f.set(cu, top + 2 + d / 2 + 2, cv, Blocks.COBBLESTONE_WALL);
		// Inside: lantern, bed, table, a rug, sometimes a chest.
		f.set(wc, y0 + 2, d / 2, hanging());
		if (floors > 1) f.set(wc, y0 + 4 + 2, d / 2, hanging());
		if (inhabited) {
			f.furn(w - 2, y0, d - 3, bed(f, Blocks.GREEN_BED, BedPart.FOOT));
			f.furn(w - 2, y0, d - 2, bed(f, Blocks.GREEN_BED, BedPart.HEAD));
			f.set(w - 2, y0, 1, Blocks.CRAFTING_TABLE);
			f.set(wc, y0, d / 2 + 1, Blocks.GREEN_CARPET);
			f.set(wc - 1, y0, d / 2 + 1, Blocks.GREEN_CARPET);
			if (chance(0.5)) {
				int cx = f.x(1, 2), cz = f.z(1, 2);
				LatveriaPlan.FURNITURE.add(new LatveriaPlan.Op(cx, y0, cz, cx, y0, cz,
						Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(1, 0))));
				LatveriaPlan.CHESTS.add(new int[]{cx, y0, cz, 0});
			}
			LatveriaPlan.VILLAGERS.add(new BlockPos(f.x(wc, -2), G, f.z(wc, -2)));
		} else {
			f.set(w - 2, y0, 1, Blocks.BARREL);
		}
		// What the house takes of the ground: its footprint and the eaves and hood around it, and the spot a villager steps out on.
		f.reserve(abutLeft ? 0 : -1, -1, w - 1 + (abutRight ? 0 : 1), d);
		f.reserve(wc, -2, wc, -2);
		LatveriaProps.houseDressing(f, w, d, floors, wc, abutLeft, abutRight);
	}

	private static BlockState bed(Frame f, Block bed, BedPart part) {
		return bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, 1)).setValue(BlockStateProperties.BED_PART, part);
	}

	/** Four walls of a footprint, one row. */
	private static void ring(Frame f, int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
		f.fill(u0, y0, v0, u1, y1, v0, state);
		f.fill(u0, y0, v1, u1, y1, v1, state);
		f.fill(u0, y0, v0, u0, y1, v1, state);
		f.fill(u1, y0, v0, u1, y1, v1, state);
	}

	/**
	 * A window of two panes in the wall cell at (u, v); on the front wall (v = 0) it gets shutters on either side.
	 * {@code along} is the wall's own axis, so the panes join up.
	 */
	private static void window(Frame f, int u, int y, int v, Direction.Axis along, boolean shutters, Palette p) {
		f.fill(u, y, v, u, y + 1, v, pane(Blocks.GLASS_PANE, along));
		if (shutters && v == 0) {
			Direction out = f.dir(0, -1);
			for (int du : new int[]{-1, 1}) {
				for (int yy = y; yy <= y + 1; yy++) f.set(u + du, yy, -1, shutter(Blocks.SPRUCE_TRAPDOOR, out));
			}
		}
	}

	/**
	 * A gable roof of stairs: slopes of one block up per block in, eaves a block out, the ends closed with the wall's own
	 * stuff, a ridge of full blocks (or a pair of stairs meeting) along the top.
	 */
	static void roof(Frame f, int w, int d, int yBase, Block roofStairs, BlockState roofCap, BlockState gable, boolean ridgeAlongU, boolean overhangLeft, boolean overhangRight) {
		int span = ridgeAlongU ? d : w;   // across the ridge
		int run = ridgeAlongU ? w : d;    // along the ridge
		int r0 = ridgeAlongU ? (overhangLeft ? -1 : 0) : -1, r1 = ridgeAlongU ? w - 1 + (overhangRight ? 1 : 0) : d;
		for (int i = -1; ; i++) {
			int a = i, b = span - 1 - i;     // the two slopes' cells across the ridge at this height
			if (a > b) break;
			int y = yBase + i;
			if (a == b) {
				// The ridge: one full row.
				for (int r = r0; r <= r1; r++) put(f, ridgeAlongU, r, a, y, roofCap);
			} else {
				for (int r = r0; r <= r1; r++) {
					put(f, ridgeAlongU, r, a, y, stairAcross(f, roofStairs, ridgeAlongU, +1));
					put(f, ridgeAlongU, r, b, y, stairAcross(f, roofStairs, ridgeAlongU, -1));
				}
			}
			// The gable ends, closed under the slopes.
			if (i >= 0 && a + 1 <= b - 1) {
				for (int s = a + 1; s <= b - 1; s++) {
					put(f, ridgeAlongU, 0, s, y, gable);
					put(f, ridgeAlongU, run - 1, s, y, gable);
				}
			}
		}
	}

	/** Stairs rising toward the ridge: {@code toward} is +1 for the slope on the low-s side (it climbs to +s). */
	private static BlockState stairAcross(Frame f, Block stairs, boolean ridgeAlongU, int toward) {
		return ridgeAlongU ? f.stairs(stairs, 0, toward, false) : f.stairs(stairs, toward, 0, false);
	}

	/** (r along the ridge, s across it) back to the building's (u, v). */
	private static void put(Frame f, boolean ridgeAlongU, int r, int s, int y, BlockState state) {
		if (ridgeAlongU) f.set(r, y, s, state);
		else f.set(s, y, r, state);
	}
}
