package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.doom.LatveriaKit.Frame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * The life of the city: lamps and their pennants, barrels and hay at doors, vines and moss on the walls, courtyards with
 * gardens, orchards, wells and woodpiles, trees, and boulders. Everything is set down only on ground nothing else has claimed.
 */
final class LatveriaProps {
	private LatveriaProps() {}

	private static final int Y = G + 1;

	// ------------------------------------------------------------------ on a house

	/** Vines down the side and back walls, moss and barrels at the door, called by each house as it is built. */
	static void houseDressing(Frame f, int w, int d, int floors, int wc, boolean abutL, boolean abutR) {
		int y0 = Y, top = y0 + 4 * floors - 1;
		int backWindow = w / 2 + (w % 2 == 0 ? -1 : 0);
		// Vines: a few strands down each free wall, hanging from under the eaves.
		for (int side = 0; side < 3; side++) {
			if (side == 0 && abutL || side == 1 && abutR) continue;
			int len = side == 2 ? w : d;
			for (int k = 1; k < len - 1; k++) {
				if (!chance(0.22)) continue;
				if (side < 2 && k == d / 2) continue;          // the side windows
				if (side == 2 && (k == backWindow || Math.abs(k - backWindow) == 0)) continue; // the back window
				int drop = rand(3, 3 + 4 * floors), y = top - 1;
				for (int i = 0; i < drop && y > y0; i++, y--) {
					switch (side) {
						case 0 -> f.set(-1, y, k, vine(f.dir(1, 0)));
						case 1 -> f.set(w, y, k, vine(f.dir(-1, 0)));
						default -> f.set(k, y, d, vine(f.dir(0, -1)));
					}
				}
			}
		}
		// A shop: a striped wool awning on posts over the pavement beside the door, with goods under it.
		if (chance(0.3)) {
			int dir = chance(0.5) ? 1 : -1, u0 = wc + dir * 2, u1 = wc + dir * 5;
			int lo = Math.min(u0, u1), hi = Math.max(u0, u1);
			if (lo >= 1 && hi <= w - 2 && f.free(lo, -2, hi, -2)) {
				f.reserve(lo, -2, hi, -2);
				BlockState a = chance(0.5) ? Blocks.GREEN_WOOL.defaultBlockState() : Blocks.BLACK_WOOL.defaultBlockState(), b = Blocks.WHITE_WOOL.defaultBlockState();
				for (int u = lo; u <= hi; u++) for (int v = -2; v <= -1; v++) f.set(u, y0 + 3, v, ((u - lo) & 1) == 0 ? a : b);
				for (int u : new int[]{lo, hi}) f.fill(u, y0, -2, u, y0 + 2, -2, Blocks.SPRUCE_FENCE);
				f.set(lo + 1, y0, -2, barrel());
				f.set(lo + 2, y0, -2, hay());
				if (hi - lo >= 4) f.set(lo + 3, y0, -2, Blocks.PUMPKIN);
				f.set((lo + hi) / 2, y0 + 2, -2, hanging());
			}
		}
		// Moss on the kerb stones at the foot of the front wall, and barrels or hay at the door.
		for (int u = 1; u < w - 1; u++) {
			if (Math.abs(u - wc) <= 1) continue;
			if (chance(0.22)) f.set(u, y0, -1, Blocks.MOSS_CARPET);
		}
		for (int du : new int[]{-3, 3}) {
			int u = wc + du;
			if (u < 0 || u > w - 1 || !f.free(u, -2, u, -2) || !chance(0.55)) continue;
			f.reserve(u, -2, u, -2);
			if (chance(0.5)) {
				f.set(u, y0, -2, barrel());
				if (chance(0.35)) f.set(u, y0 + 1, -2, barrel());
			} else if (chance(0.5)) {
				f.set(u, y0, -2, hay());
				if (chance(0.4)) f.set(u, y0 + 1, -2, hay());
			} else {
				f.set(u, y0, -2, Blocks.COBBLESTONE_WALL);
				f.set(u, y0 + 1, -2, standing());
			}
		}
	}

	// ------------------------------------------------------------------ the whole city

	static void dress() {
		lamps();
		boulders();
	}

	private static void lamps() {
		// Along every street on both kerbs, and a pair at each crossing.
		for (int cx : LatveriaCity.NS) {
			for (int z = LatveriaCity.MAIN_Z0 + 4; z <= LatveriaCity.MAIN_Z1 - 4; z += 11) {
				lamp(cx - 3, z + (cx == 0 ? 0 : 5), cx > 0 ? 1 : 0);
				lamp(cx + 3, z + 5 + (cx == 0 ? 0 : 5), 0);
			}
		}
		for (int cz : LatveriaCity.EW) {
			for (int x = -70; x <= 70; x += 11) {
				lamp(x + 5, cz - 3, 0);
				lamp(x, cz + 3, 1);
			}
		}
		for (int cx : LatveriaCity.NS) for (int cz : LatveriaCity.EW) {
			for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) lamp(cx + sx * 3, cz + sz * 3, 2);
		}
	}

	/** A lamp post on the kerb at (x, z): fence posts, a lantern, and on some a green pennant on the side of the post. */
	static void lamp(int x, int z, int style) {
		if (!free(x, z)) return;
		reserve(x, z, x, z);
		int y = Y;
		fill(x, y, z, x, y + 3, z, Blocks.SPRUCE_FENCE);
		set(x, y, z, Blocks.STONE_BRICK_WALL);
		set(x, y + 4, z, standing());
		if (style == 2) {
			// A double lamp: a second lantern on a short arm.
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (free(x + d.getStepX(), z + d.getStepZ())) { set(x + d.getStepX(), y + 3, z + d.getStepZ(), Blocks.SPRUCE_FENCE); set(x + d.getStepX(), y + 4, z + d.getStepZ(), standing()); reserve(x + d.getStepX(), z + d.getStepZ(), x + d.getStepX(), z + d.getStepZ()); break; }
			}
		} else if (style == 1) {
			// A pennant hung from the post toward the road, above head height.
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int px = x + d.getStepX(), pz = z + d.getStepZ();
				if (!LatveriaCity.onRoad(px, pz)) continue;
				fill(px, y + 2, pz, px, y + 3, pz, Blocks.GREEN_WOOL);
				break;
			}
		}
	}

	/** Heaps of rubble and boulders where nothing is built: against walls and in corners. */
	private static void boulders() {
		for (int i = 0; i < 160; i++) {
			int x = rand(-72, 72), z = rand(-44, 122);
			if (!free(x - 1, z - 1, x + 1, z + 1)) continue;
			if (LatveriaCity.onRoad(x, z)) continue;
			boulder(x, z);
		}
	}

	static void boulder(int x, int z) {
		int h = rand(1, 3);
		Block m = chance(0.5) ? Blocks.MOSSY_COBBLESTONE : chance(0.5) ? Blocks.COBBLESTONE : Blocks.TUFF;
		fill(x, Y, z, x, Y + h - 1, z, m);
		if (chance(0.7)) set(x + 1, Y, z, pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE));
		if (chance(0.5)) set(x, Y, z + 1, pick(Blocks.MOSSY_COBBLESTONE, Blocks.TUFF));
		if (h > 1 && chance(0.5)) set(x, Y + h, z, Blocks.MOSS_CARPET);
		reserve(x, z, x + 1, z + 1);
	}

	// ------------------------------------------------------------------ courtyards

	/** The yard behind a ring of houses: a garden, an orchard, a yard with a well, or an old graveyard. */
	static void courtyard(int x0, int z0, int x1, int z1) {
		if (x1 - x0 < 2 || z1 - z0 < 2) return;
		int theme = RND.nextInt(5);
		// Worn ground first: podzol, coarse dirt and moss over the grass.
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (!free(x, z)) continue;
			if (chance(0.18)) set(x, G, z, pick(Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT));
		}
		switch (theme) {
			case 0 -> garden(x0, z0, x1, z1);
			case 1 -> orchard(x0, z0, x1, z1);
			case 2 -> yard(x0, z0, x1, z1);
			case 3 -> graveyard(x0, z0, x1, z1);
			default -> { garden(x0, z0, x1, z1); yard(x0, z0, x1, z1); }
		}
		// Whatever is left of the lawn grows wild: ferns, flowers, a few stones.
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (!free(x, z) || !chance(0.16)) continue;
			set(x, Y, z, pick(Blocks.FERN, Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY, Blocks.SWEET_BERRY_BUSH));
			reserve(x, z, x, z);
		}
	}

	private static void garden(int x0, int z0, int x1, int z1) {
		for (int tries = 0; tries < 6; tries++) {
			int w = rand(3, 5), d = rand(3, 5);
			int x = rand(x0, Math.max(x0, x1 - w)), z = rand(z0, Math.max(z0, z1 - d));
			if (x + w - 1 > x1 || z + d - 1 > z1 || !free(x - 1, z - 1, x + w, z + d)) continue;
			reserve(x - 1, z - 1, x + w, z + d);
			// A bed fenced round, with the same flower or a pumpkin patch inside.
			for (int i = 0; i < w; i++) for (int j = 0; j < d; j++) {
				boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
				if (edge && ((i + j) & 1) == 0 && !(i == w / 2 && j == 0)) set(x + i, Y, z + j, Blocks.SPRUCE_FENCE);
				else if (!edge) set(x + i, Y, z + j, chance(0.2) ? pick(Blocks.PUMPKIN, Blocks.MELON, Blocks.HAY_BLOCK) : pick(Blocks.ALLIUM, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.OXEYE_DAISY));
			}
		}
	}

	private static void orchard(int x0, int z0, int x1, int z1) {
		for (int x = x0 + 1; x <= x1 - 1; x += 4) for (int z = z0 + 1; z <= z1 - 1; z += 4) {
			if (!free(x - 2, z - 2, x + 2, z + 2)) continue;
			if (chance(0.7)) tree(x, z, chance(0.5));
		}
	}

	private static void yard(int x0, int z0, int x1, int z1) {
		if (x1 - x0 >= 3 && z1 - z0 >= 3) {
			int wx = rand(x0 + 1, x1 - 1), wz = rand(z0 + 1, z1 - 1);
			if (free(wx - 2, wz - 2, wx + 2, wz + 2)) well(wx, wz);
		}
		for (int i = 0; i < 6; i++) {
			int x = rand(x0, x1), z = rand(z0, z1);
			if (!free(x, z)) continue;
			reserve(x, z, x, z);
			switch (RND.nextInt(4)) {
				case 0 -> { set(x, Y, z, barrel()); if (chance(0.4)) set(x, Y + 1, z, barrel()); }
				case 1 -> { set(x, Y, z, hay()); if (chance(0.5)) set(x, Y + 1, z, hay()); }
				case 2 -> woodpile(x, z);
				default -> set(x, Y, z, Blocks.COBBLESTONE_WALL);
			}
		}
	}

	private static void woodpile(int x, int z) {
		Direction.Axis axis = chance(0.5) ? Direction.Axis.X : Direction.Axis.Z;
		for (int i = 0; i < 2; i++) {
			if (!free(x + (axis == Direction.Axis.X ? i : 0), z + (axis == Direction.Axis.Z ? i : 0))) continue;
			int px = x + (axis == Direction.Axis.X ? i : 0), pz = z + (axis == Direction.Axis.Z ? i : 0);
			reserve(px, pz, px, pz);
			set(px, Y, pz, log(Blocks.STRIPPED_SPRUCE_LOG, axis));
			if (chance(0.6)) set(px, Y + 1, pz, log(Blocks.SPRUCE_LOG, axis));
		}
	}

	static void graveyard(int x0, int z0, int x1, int z1) {
		for (int x = x0 + 1; x <= x1 - 1; x += 2) for (int z = z0 + 1; z <= z1 - 1; z += 3) {
			if (!free(x, z, x, z + 1) || !chance(0.7)) continue;
			reserve(x, z, x, z + 1);
			// A headstone with a mossy mound.
			set(x, Y, z, pick(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_SLAB));
			if (chance(0.5)) set(x, Y + 1, z, Blocks.COBBLESTONE_WALL);
			set(x, Y, z + 1, chance(0.5) ? Blocks.MOSS_CARPET : Blocks.AIR);
		}
		if (free(x0, z0) && chance(0.7)) tree(x0, z0, true);
	}

	static void well(int x, int z) {
		reserve(x - 2, z - 2, x + 2, z + 2);
		fill(x - 1, Y, z - 1, x + 1, Y, z + 1, Blocks.STONE_BRICKS);
		set(x, G, z, Blocks.WATER);
		set(x, Y, z, Blocks.AIR);
		for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
			fill(x + dx, Y + 1, z + dz, x + dx, Y + 3, z + dz, Blocks.SPRUCE_FENCE);
			set(x + dx, Y, z + dz, Blocks.MOSSY_COBBLESTONE);
		}
		fill(x - 1, Y + 4, z - 1, x + 1, Y + 4, z + 1, slab(Blocks.SPRUCE_SLAB, false));
		set(x, Y + 3, z, hanging());
	}

	/** A tree: a conical spruce or a gnarled dark oak with leaves that stay. */
	static void tree(int x, int z, boolean spruce) {
		int reach = spruce ? 3 : 2;
		// The whole crown must fit in free ground: leaves would otherwise cut into a neighbour's wall.
		if (!free(x - reach, z - reach, x + reach, z + reach)) return;
		reserve(x - reach, z - reach, x + reach, z + reach);
		if (spruce) {
			int h = rand(7, 11);
			fill(x, Y, z, x, Y + h - 1, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			BlockState l = leaves(Blocks.SPRUCE_LEAVES);
			int layers = h - 2;
			for (int i = 0; i < layers; i++) {
				int y = Y + h - 1 - i;
				int r = i == 0 ? 0 : i % 3 == 0 ? 1 : i % 3 == 1 ? 2 : 3;
				r = Math.min(r, 1 + i / 2);
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
					if (dx == 0 && dz == 0) continue;
					if (Math.abs(dx) == r && Math.abs(dz) == r && r > 1) continue;
					set(x + dx, y, z + dz, l);
				}
			}
			set(x, Y + h, z, l);
		} else {
			int h = rand(4, 6);
			fill(x, Y, z, x, Y + h - 1, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
			BlockState l = leaves(Blocks.DARK_OAK_LEAVES);
			for (int dy = 0; dy < 3; dy++) {
				int r = dy == 1 ? 2 : 1;
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && r == 2) continue;
					if (dx == 0 && dz == 0 && dy < 2) continue;
					set(x + dx, Y + h - 1 + dy, z + dz, l);
				}
			}
		}
	}
}
