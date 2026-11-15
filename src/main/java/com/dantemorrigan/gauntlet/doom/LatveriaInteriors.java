package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * What is inside the houses: every storey is a room with a purpose, and furnished to it. The street floor is a hall with a
 * hearth, a table and chairs, a kitchen, a workshop or a storeroom; above it come bedrooms and a study with shelves to
 * the ceiling. Everything stands on the floor or against a wall, nothing blocks the door or the ladder, and every
 * piece is put down only where nothing else is.
 */
final class LatveriaInteriors {
	private LatveriaInteriors() {}

	static final class Room {
		final Frame f;
		final int w, d, y;
		final boolean[][] used;

		Room(Frame f, int w, int d, int y) {
			this.f = f;
			this.w = w;
			this.d = d;
			this.y = y;
			this.used = new boolean[w][d];
		}

		boolean ok(int u, int v) { return u >= 1 && u <= w - 2 && v >= 1 && v <= d - 2 && !used[u][v]; }

		void block(int u, int v, BlockState s) { if (ok(u, v)) { used[u][v] = true; f.set(u, y, v, s); } }

		void block(int u, int v, Block b) { block(u, v, b.defaultBlockState()); }

		/** A two-high piece (shelf, wardrobe, tall plant). */
		void tall(int u, int v, BlockState lower, BlockState upper) {
			if (ok(u, v)) { used[u][v] = true; f.set(u, y, v, lower); f.set(u, y + 1, v, upper); }
		}

		void atop(int u, int v, BlockState s) { if (u == 1 && v >= d - 3) return; f.set(u, y + 1, v, s); }

		void keep(int u, int v) { if (u >= 0 && u < w && v >= 0 && v < d) used[u][v] = true; }

		void furn(int u, int v, BlockState s) { if (ok(u, v)) { used[u][v] = true; f.furn(u, y, v, s); } }

		void bed(int u, int v, Block bed) {
			if (!ok(u, v) || !ok(u, v + 1)) return;
			used[u][v] = used[u][v + 1] = true;
			BlockState b = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, 1));
			f.furn(u, y, v, b.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			f.furn(u, y, v + 1, b.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}

		void chest(int u, int v, Direction facing, int loot) {
			if (!ok(u, v)) return;
			used[u][v] = true;
			int x = f.x(u, v), z = f.z(u, v);
			LatveriaPlan.FURNITURE.add(new LatveriaPlan.Op(x, y, z, x, y, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)));
			LatveriaPlan.CHESTS.add(new int[]{x, y, z, loot});
		}

		/** A table: a post with a plate on it, a candle or a pot on top, and chairs of stairs facing it. */
		void table(int u, int v) {
			if (!ok(u, v)) return;
			block(u, v, Blocks.SPRUCE_FENCE);
			atop(u, v, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
			f.set(u, y + 2, v, chance(0.5) ? Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true) : pick(Blocks.POTTED_FERN, Blocks.POTTED_POPPY, Blocks.POTTED_ALLIUM));
			// The plate is a thin top: the candle stands on the post's plate, one block above the floor.
			f.set(u, y + 2, v, AIR);
			f.set(u, y + 1, v, Blocks.SPRUCE_PRESSURE_PLATE);
			for (int[] c : new int[][]{{1, 0, -1, 0}, {-1, 0, 1, 0}, {0, 1, 0, -1}, {0, -1, 0, 1}}) {
				if (chance(0.5)) block(u + c[0], v + c[1], stairs(Blocks.SPRUCE_STAIRS, f.dir(c[2], c[3]), false));
			}
		}

		void rug(int u, int v, int w2, int d2, Block a, Block b) {
			for (int i = 0; i < w2; i++) for (int j = 0; j < d2; j++) {
				int uu = u + i, vv = v + j;
				if (uu < 1 || uu > w - 2 || vv < 1 || vv > d - 2 || used[uu][vv]) continue;
				f.set(uu, y, vv, (i == 0 || j == 0 || i == w2 - 1 || j == d2 - 1) ? b : a);
			}
		}
	}

	/** Furnishes every storey of a house. */
	static void furnish(Frame f, int w, int d, int floors, int wc, boolean inhabited) {
		for (int k = 0; k < floors; k++) {
			Room r = new Room(f, w, d, G + 1 + 4 * k);
			r.keep(1, d - 2);   // the ladder
			r.keep(1, d - 3);   // and its landing
			if (k == 0) { r.keep(wc, 1); r.keep(wc, 2); }
			int theme = k == 0 ? (inhabited ? RND.nextInt(3) : 3) : k == 1 ? 4 : 5;
			if (floors == 2 && k == 1 && chance(0.4)) theme = 5;
			switch (theme) {
				case 0 -> hall(r);
				case 1 -> kitchen(r);
				case 2 -> workshop(r);
				case 3 -> storeroom(r);
				case 4 -> bedroom(r);
				default -> study(r);
			}
			// Every room: a rug where the floor is free and a lantern overhead (the street floor's hangs from the plain ceiling).
			r.rug(Math.max(1, w / 2 - 1), Math.max(1, d / 2 - 1), 3, 2, pick(Blocks.GREEN_CARPET, Blocks.GRAY_CARPET, Blocks.BROWN_CARPET).getBlock(), pick(Blocks.BLACK_CARPET, Blocks.RED_CARPET, Blocks.LIME_CARPET).getBlock());
			// A banner of green wool on a wall at the back, between the shelves.
			if (chance(0.5) && w >= 8) {
				int u = w / 2;
				if (r.ok(u, d - 2)) { f.fill(u, r.y + 1, d - 2, u, r.y + 2, d - 2, Blocks.GREEN_WOOL); r.keep(u, d - 2); }
			}
		}
	}

	private static void shelves(Room r, int from, int to, int v, int height) {
		for (int u = from; u <= to; u++) for (int h = 0; h < height; h++) {
			if (!r.ok(u, v)) break;
			r.f.set(u, r.y + h, v, Blocks.BOOKSHELF);
			if (h == height - 1) r.used[u][v] = true;
		}
	}

	private static void hearth(Room r, int v) {
		int u = r.w - 2;
		if (!r.ok(u, v)) return;
		r.furn(u, v, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.f.dir(-1, 0)));
		r.block(u, v - 1, Blocks.STONE_BRICKS);
		r.block(u, v + 1, Blocks.STONE_BRICKS);
		r.f.set(u, r.y + 1, v, Blocks.STONE_BRICKS);
		r.f.set(u, r.y + 1, v - 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, r.f.dir(-1, 0)));
		r.f.set(u, r.y + 1, v + 1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, r.f.dir(-1, 0)));
	}

	private static void hall(Room r) {
		int w = r.w, d = r.d;
		r.bed(w - 2, d - 3, Blocks.GREEN_BED);
		hearth(r, Math.max(3, d / 2 - 1));
		r.table(Math.max(2, w / 2 - 1), d / 2 + 1);
		shelves(r, 2, Math.min(4, w - 4), d - 2, 3);
		r.chest(1, 2, r.f.dir(1, 0), 0);
		r.block(2, 1, Blocks.BARREL);
		r.block(w - 2, 1, Blocks.CRAFTING_TABLE);
		r.block(w - 3, 1, Blocks.POTTED_FERN);
	}

	private static void kitchen(Room r) {
		int w = r.w, d = r.d;
		r.furn(w - 2, 1, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.f.dir(-1, 0)));
		r.furn(w - 2, 2, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.f.dir(-1, 0)));
		r.block(w - 2, 3, Blocks.CAULDRON.defaultBlockState());
		r.block(w - 2, 4, Blocks.CRAFTING_TABLE);
		r.block(w - 2, d - 3, Blocks.COMPOSTER);
		for (int u = 2; u <= Math.min(w - 4, 4); u++) { r.block(u, d - 2, Blocks.BARREL); }
		r.f.set(2, r.y + 1, d - 2, Blocks.HAY_BLOCK);
		r.table(Math.max(2, w / 2 - 1), d / 2);
		r.chest(1, 2, r.f.dir(1, 0), 0);
		r.block(2, 1, Blocks.HAY_BLOCK);
	}

	private static void workshop(Room r) {
		int w = r.w, d = r.d;
		r.block(w - 2, 1, Blocks.ANVIL);
		r.block(w - 2, 3, Blocks.GRINDSTONE);
		r.block(w - 2, 4, Blocks.SMITHING_TABLE);
		r.block(w - 3, d - 2, Blocks.LOOM);
		r.block(w - 4, d - 2, Blocks.CARTOGRAPHY_TABLE);
		r.furn(w - 2, 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.f.dir(-1, 0)));
		r.table(Math.max(2, w / 2 - 1), d / 2);
		r.chest(2, 1, r.f.dir(0, 1), 1);
		r.block(3, 1, Blocks.BARREL);
		r.block(2, d - 4, Blocks.BARREL);
		r.bed(w - 2, d - 3, Blocks.WHITE_BED);
	}

	private static void storeroom(Room r) {
		int w = r.w, d = r.d;
		for (int u = 1; u <= w - 2; u += 1) {
			if (chance(0.7)) { r.block(u, d - 2, Blocks.BARREL); if (chance(0.5)) r.atop(u, d - 2, Blocks.BARREL.defaultBlockState()); }
		}
		for (int v = 1; v <= d - 3; v += 1) {
			if (chance(0.5)) { r.block(w - 2, v, chance(0.5) ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState()); }
		}
		r.chest(2, 1, r.f.dir(0, 1), 1);
		r.chest(3, 1, r.f.dir(0, 1), 0);
		r.block(w / 2, d / 2, Blocks.BARREL);
	}

	private static void bedroom(Room r) {
		int w = r.w, d = r.d;
		r.bed(w - 2, d - 3, Blocks.GREEN_BED);
		if (w >= 8) r.bed(w - 3, d - 3, Blocks.LIME_BED);
		r.block(w - 4, d - 2, Blocks.BARREL);
		r.atop(w - 4, d - 2, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
		r.tall(2, d - 2, Blocks.BARREL.defaultBlockState(), Blocks.BARREL.defaultBlockState());
		r.tall(3, d - 2, Blocks.BARREL.defaultBlockState(), Blocks.BARREL.defaultBlockState());
		shelves(r, w - 2, w - 2, 1, 2);
		r.chest(1, 2, r.f.dir(1, 0), 0);
		r.block(2, 1, Blocks.POTTED_FERN);
	}

	private static void study(Room r) {
		int w = r.w, d = r.d;
		shelves(r, 2, w - 3, d - 2, 3);
		for (int v = 2; v <= d - 4; v++) shelves(r, w - 2, w - 2, v, 3);
		r.table(Math.max(2, w / 2 - 1), d / 2);
		r.furn(2, 1, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, r.f.dir(0, 1)));
		r.block(3, 1, Blocks.CARTOGRAPHY_TABLE);
		r.block(w - 2, 1, Blocks.ENCHANTING_TABLE);
		r.chest(1, 1, r.f.dir(1, 0), 0);
	}

	// ------------------------------------------------------------------ rooms in the towers and the castle

	/** Kinds of tower room. */
	static final int GUARD = 0, ARMORY = 1, STORE = 2, LAB = 3, LIBRARY = 4, WAR = 5, SHRINE = 6, BAY = 7;

	/**
	 * Furnishes one floor of a tower or a hall: {@code f} is the room's frame (u east, v south), {@code w} by {@code d} cells
	 * with walls on the border, people standing at {@code y}. The ladder's corner (1, 1) and its landing stay clear.
	 */
	static void towerRoom(Frame f, int w, int d, int y, int kind) {
		Room r = new Room(f, w, d, y);
		r.keep(1, 1);
		r.keep(1, 2);
		r.keep(2, 1);
		int mu = w / 2, mv = d / 2;
		switch (kind) {
			case GUARD -> {
				r.bed(w - 2, 1, Blocks.GREEN_BED);
				if (w >= 7) r.bed(w - 3, 1, Blocks.GREEN_BED);
				r.chest(1, d - 2, f.dir(1, 0), 1);
				r.block(2, d - 2, Blocks.BARREL);
				r.block(w - 2, d - 2, Blocks.GRINDSTONE);
				r.table(mu, mv);
				r.block(w - 2, mv, Blocks.CRAFTING_TABLE);
			}
			case ARMORY -> {
				for (int u = 2; u <= w - 2; u++) r.tall(u, d - 2, Blocks.IRON_BLOCK.defaultBlockState(), Blocks.LIGHTNING_ROD.defaultBlockState());
				r.block(w - 2, 2, Blocks.ANVIL);
				r.block(w - 2, 3, Blocks.SMITHING_TABLE);
				r.chest(1, mv, f.dir(1, 0), 1);
				r.chest(1, mv + 1, f.dir(1, 0), 1);
				r.block(mu, mv, Blocks.GRINDSTONE);
			}
			case STORE -> {
				for (int u = 2; u <= w - 2; u++) { r.block(u, d - 2, Blocks.BARREL); r.atop(u, d - 2, Blocks.BARREL.defaultBlockState()); }
				for (int v = 2; v <= d - 3; v++) if (chance(0.7)) r.block(w - 2, v, chance(0.5) ? hay() : barrel());
				r.chest(1, d - 3, f.dir(1, 0), 0);
				r.block(mu, mv, Blocks.HAY_BLOCK);
			}
			case LAB -> {
				r.furn(w - 2, 2, Blocks.BREWING_STAND.defaultBlockState());
				r.block(w - 2, 3, Blocks.CAULDRON);
				for (int u = 2; u <= w - 2; u++) {
					if (!r.ok(u, d - 2)) continue;
					r.block(u, d - 2, ((u & 1) == 0) ? Blocks.WAXED_COPPER_BLOCK : Blocks.REDSTONE_LAMP);
					r.atop(u, d - 2, Blocks.GLASS.defaultBlockState());
				}
				r.block(mu, mv, Blocks.WAXED_CUT_COPPER);
				r.atop(mu, mv, Blocks.LIGHTNING_ROD.defaultBlockState());
				r.chest(1, mv, f.dir(1, 0), 2);
				r.block(w - 2, d - 3, Blocks.SEA_LANTERN);
			}
			case LIBRARY -> {
				for (int u = 2; u <= w - 2; u++) for (int h = 0; h < 3; h++) if (r.ok(u, d - 2)) { f.set(u, y + h, d - 2, Blocks.BOOKSHELF); if (h == 2) r.keep(u, d - 2); }
				for (int v = 3; v <= d - 3; v++) for (int h = 0; h < 3; h++) if (r.ok(w - 2, v)) { f.set(w - 2, y + h, v, Blocks.BOOKSHELF); if (h == 2) r.keep(w - 2, v); }
				r.furn(mu, mv - 1, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, 1)));
				r.table(mu, mv + 1);
				r.chest(1, d - 2, f.dir(1, 0), 3);
			}
			case WAR -> {
				// A long table under a map of Latveria: slabs on posts, green and black carpet for the land and the roads.
				for (int u = mu - 1; u <= mu + 1; u++) for (int v = mv - 1; v <= mv + 1; v++) {
					if (!r.ok(u, v)) continue;
					r.block(u, v, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
				}
				r.block(mu - 2, mv, stairs(Blocks.SPRUCE_STAIRS, f.dir(1, 0), false));
				r.block(mu + 2, mv, stairs(Blocks.SPRUCE_STAIRS, f.dir(-1, 0), false));
				r.block(w - 2, 2, Blocks.CARTOGRAPHY_TABLE);
				r.furn(w - 2, d - 2, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(-1, 0)));
				r.chest(1, d - 2, f.dir(1, 0), 3);
				for (int v = 2; v <= d - 3; v += 3) if (r.ok(w - 2, v)) { f.fill(w - 2, y + 1, v, w - 2, y + 2, v, Blocks.GREEN_WOOL); r.keep(w - 2, v); }
			}
			case SHRINE -> {
				// Doom's trophies: gold on a black plinth, green carpet to it, lanterns on posts.
				r.block(mu, d - 2, Blocks.POLISHED_BLACKSTONE_BRICKS);
				r.atop(mu, d - 2, Blocks.GOLD_BLOCK.defaultBlockState());
				r.block(mu - 1, d - 2, Blocks.POLISHED_BLACKSTONE_WALL);
				r.atop(mu - 1, d - 2, standing());
				r.block(mu + 1, d - 2, Blocks.POLISHED_BLACKSTONE_WALL);
				r.atop(mu + 1, d - 2, standing());
				r.rug(mu, 1, 1, d - 3, Blocks.GREEN_CARPET, Blocks.GREEN_CARPET);
				r.chest(1, d - 2, f.dir(1, 0), 3);
			}
			default -> {
				// A Doombot bay: charging pods of iron under redstone lamps, chains from the ceiling, tools.
				for (int u = 2; u <= w - 2; u += 2) {
					if (!r.ok(u, d - 2)) continue;
					r.block(u, d - 2, Blocks.IRON_BLOCK);
					r.atop(u, d - 2, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
				}
				r.block(w - 2, 2, Blocks.ANVIL);
				r.block(w - 2, 3, Blocks.SMITHING_TABLE);
				r.chest(1, mv, f.dir(1, 0), 2);
				r.block(mu, mv, Blocks.WAXED_COPPER_BLOCK);
			}
		}
		r.rug(Math.max(1, mu - 1), Math.max(1, mv - 1), 3, 3, Blocks.GREEN_CARPET, Blocks.BLACK_CARPET);
		// Light: lamps on posts in the free corners.
		for (int[] c : new int[][]{{w - 2, 1}, {1, d - 2}, {w - 2, d - 2}, {2, d - 2}, {w - 2, 2}}) {
			if (!r.ok(c[0], c[1])) continue;
			r.block(c[0], c[1], Blocks.POLISHED_BLACKSTONE_WALL);
			r.atop(c[0], c[1], standing());
		}
	}

	/** A floor of a square shaft: x0..x0+size-1, the walls on the border; floor slab at {@code floorY}, and a ladder through it. */
	static void shaftFloor(int x0, int z0, int size, int floorY, int ceilingY, int kind) {
		Frame f = new Frame(x0, z0, Direction.NORTH);
		// The ladder: up the west wall in the north-west corner, through a hole in the floor above.
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
		f.fill(1, floorY + 1, 1, 1, ceilingY, 1, ladder);
		if (ceilingY > floorY + 3) f.set(1, ceilingY, 1, ladder);
		towerRoom(f, size, size, floorY + 1, kind);
		f.set(size / 2, ceilingY - 1, size / 2, hanging());
	}
}
