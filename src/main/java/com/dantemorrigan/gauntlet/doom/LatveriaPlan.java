package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Latveria as pure functions of the coordinates: the terrain height, and every block of the city, the road and
 * Castle Doom as a list of boxes. The chunk generator replays the boxes that touch a chunk, so the land is the same in
 * every world and nothing has to be remembered about what was built. Blocks that need a block entity (beds, chests,
 * furnaces) are kept in a second list and placed once by the server.
 */
public final class LatveriaPlan {
	public static final int SEA = 62, VALLEY_Y = 68, CASTLE_Y = 104, FLOOR = CASTLE_Y + 1;
	public static final int CASTLE_Z = -250;
	/** Where travellers appear, facing the city and the castle. */
	public static final Vec3 ARRIVAL = new Vec3(0.5, VALLEY_Y + 1, 122.5);
	/** The gate back: standing in it returns the traveller to where they came from. */
	public static final AABB RETURN_GATE = new AABB(-2, VALLEY_Y + 1, 127, 3, VALLEY_Y + 6, 130);
	public static final Vec3 CASTLE_HALL = new Vec3(0.5, FLOOR, -258.5);
	public static final Vec3 ARENA = new Vec3(0.5, FLOOR, -272.5);
	public static final Vec3 THRONE = new Vec3(0.5, FLOOR + 2, -290.5);
	/** The throne room's inside, where the fight is held. */
	public static final AABB ARENA_BOX = new AABB(-21, FLOOR - 1, -294, 22, FLOOR + 17, -253);
	public static final BlockPos GENERATOR = new BlockPos(32, FLOOR + 2, -274);
	/** Seal nodes of the Edict: north, east, south, west of the arena's centre. */
	public static final BlockPos[] SEAL_NODES = {
			new BlockPos(0, FLOOR + 1, -287), new BlockPos(15, FLOOR + 1, -272),
			new BlockPos(0, FLOOR + 1, -257), new BlockPos(-15, FLOOR + 1, -272)};
	/** Barriers the assault removes: the portcullis, the throne room doors, the laboratory's side door. */
	public static final int[] GATE = {-3, FLOOR, -205, 3, FLOOR + 6, -205};
	public static final int[] THRONE_DOOR = {-3, FLOOR, -253, 3, FLOOR + 7, -252};
	public static final int[] LAB_DOOR = {22, FLOOR, -262, 23, FLOOR + 3, -260};

	public record Op(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {}

	public static final List<Op> OPS = new ArrayList<>();
	public static final List<Op> FURNITURE = new ArrayList<>();
	/** Chests to fill: position and a loot kind (0 pantry, 1 armory, 2 laboratory, 3 treasury). */
	public static final List<int[]> CHESTS = new ArrayList<>();
	public static final List<BlockPos> VILLAGERS = new ArrayList<>();

	// ------------------------------------------------------------------ terrain

	private static double hash(int x, int z, int seed) {
		int h = x * 374761393 + z * 668265263 + seed * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
	}

	private static double noise(double x, double z, int seed) {
		int x0 = Mth.floor(x), z0 = Mth.floor(z);
		double fx = x - x0, fz = z - z0;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = Mth.lerp(fx, hash(x0, z0, seed), hash(x0 + 1, z0, seed));
		double b = Mth.lerp(fx, hash(x0, z0 + 1, seed), hash(x0 + 1, z0 + 1, seed));
		return Mth.lerp(fz, a, b);
	}

	private static double fbm(double x, double z, int seed) {
		double sum = 0, amp = 0.5, total = 0;
		for (int i = 0; i < 4; i++) {
			sum += noise(x, z, seed + i) * amp;
			total += amp;
			x *= 2.03;
			z *= 2.03;
			amp *= 0.5;
		}
		return sum / total;
	}

	private static double smooth(double a, double b, double v) {
		double t = Mth.clamp((v - a) / (b - a), 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** Height of the road from the city up to the castle gate, in blocks and fractions. */
	public static double roadHeight(int z) {
		return Mth.lerp(Mth.clamp((-50 - z) / 138.0, 0, 1), VALLEY_Y, CASTLE_Y);
	}

	public static boolean onRoad(int x, int z) {
		return Math.abs(x) <= 3 && z <= -50 && z >= -190;
	}

	/** Y of the highest solid terrain block in this column (buildings not counted). */
	public static int surface(int x, int z) {
		return (int) Math.floor(height(x, z));
	}

	public static double height(int x, int z) {
		double ex = x / 170.0, ez = (z - 10) / 190.0;
		double ring = smooth(0.75, 1.6, Math.sqrt(ex * ex + ez * ez));
		double ridge = 1 - Math.abs(fbm(x / 150.0, z / 150.0, 11) * 2 - 1);
		double h = VALLEY_Y + (noise(x / 60.0, z / 60.0, 3) - 0.5) * 5
				+ ring * (26 + 100 * ridge * ridge + 18 * fbm(x / 40.0, z / 40.0, 23));
		// The river along the east side of the valley.
		if (z > -130 && z < 200) {
			double dr = Math.abs(x - (108 + 16 * Math.sin(z / 55.0)));
			double fade = smooth(-130, -100, z) * (1 - smooth(170, 200, z));
			if (dr < 16) h = Mth.lerp(fade * (1 - smooth(4, 16, dr)), h, 57);
		}
		// The city stands on level ground.
		double city = Math.max(Math.max(Math.abs(x) - 80, z - 132), -52 - z);
		if (city < 14) h = Mth.lerp(1 - smooth(0, 14, city), h, VALLEY_Y);
		if (z <= -50 && z >= -196) {
			double side = Math.abs(x);
			if (side < 18) h = Mth.lerp(1 - smooth(4, 18, side), h, roadHeight(z));
		}
		double d = Math.sqrt(x * x + (double) (z - CASTLE_Z) * (z - CASTLE_Z));
		if (d < 104) h = Mth.lerp(1 - smooth(66, 104, d), h, CASTLE_Y);
		return h;
	}

	// ------------------------------------------------------------------ building blocks

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
	private static final BlockState WALL_BASE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	private static final BlockState TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
	private static final BlockState GREEN = Blocks.GREEN_WOOL.defaultBlockState();
	private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
	private static final BlockState GLASS = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
	private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
	private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	private static final BlockState ROOF = Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
	private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();

	private static void fill(List<Op> to, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		to.add(new Op(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), state));
	}

	private static void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		fill(OPS, x0, y0, z0, x1, y1, z1, state);
	}

	private static void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
	}

	private static void set(int x, int y, int z, BlockState state) { fill(x, y, z, x, y, z, state); }

	private static void set(int x, int y, int z, Block block) { fill(x, y, z, x, y, z, block.defaultBlockState()); }

	private static void furn(int x, int y, int z, BlockState state) { fill(FURNITURE, x, y, z, x, y, z, state); }

	/** Four walls around a footprint. */
	private static void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		fill(x0, y0, z0, x1, y1, z0, state);
		fill(x0, y0, z1, x1, y1, z1, state);
		fill(x0, y0, z0, x0, y1, z1, state);
		fill(x1, y0, z0, x1, y1, z1, state);
	}

	/** A room: walls of the given thickness around air. */
	private static void shell(int x0, int y0, int z0, int x1, int y1, int z1, int thick, BlockState state) {
		fill(x0, y0, z0, x1, y1, z1, state);
		fill(x0 + thick, y0, z0 + thick, x1 - thick, y1, z1 - thick, AIR);
	}

	private static BlockState stairs(Block block, Direction facing, boolean top) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	private static BlockState slab(Block block, boolean top) {
		return block.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	private static BlockState log(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static void crenels(int x0, int y, int z0, int x1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
			if (edge && ((x + z) & 1) == 0) set(x, y, z, state);
		}
	}

	private static void tower(int cx, int cz, int r, int y0, int y1, int roof) {
		for (int dz = -r; dz <= r; dz++) {
			int half = (int) Math.round(Math.sqrt(r * r + r - dz * dz));
			fill(cx - half, y0, cz + dz, cx + half, y1, cz + dz, WALL);
		}
		for (int dz = -r + 2; dz <= r - 2; dz++) {
			int half = (int) Math.round(Math.sqrt((r - 2) * (r - 2) + r - 2 - dz * dz));
			fill(cx - half, y0 + 1, cz + dz, cx + half, y1 - 1, cz + dz, AIR);
		}
		for (int i = 0; i <= roof; i++) {
			int rr = r + 1 - (r + 1) * i / roof;
			for (int dz = -rr; dz <= rr; dz++) {
				int half = (int) Math.round(Math.sqrt(Math.max(0, rr * rr + rr - dz * dz)));
				fill(cx - half, y1 + 1 + i, cz + dz, cx + half, y1 + 1 + i, cz + dz, ROOF);
			}
		}
		for (int y = y0 + 4; y < y1 - 2; y += 6) {
			set(cx - r, y, cz, GLASS); set(cx + r, y, cz, GLASS); set(cx, y, cz - r, GLASS); set(cx, y, cz + r, GLASS);
		}
		set(cx, y1 + roof + 2, cz, GOLD);
	}

	/** A banner of green cloth under a gold cap, flat against a wall. */
	private static void banner(int x, int y, int z, boolean alongX) {
		fill(x, y - 5, z, x + (alongX ? 1 : 0), y - 1, z + (alongX ? 0 : 1), GREEN);
		fill(x, y, z, x + (alongX ? 1 : 0), y, z + (alongX ? 0 : 1), GOLD);
	}

	private static void lamp(int x, int y, int z) {
		fill(x, y, z, x, y + 2, z, Blocks.SPRUCE_FENCE);
		set(x, y + 3, z, LANTERN);
	}

	private static void bed(int x, int y, int z, Direction facing, Block bed) {
		BlockState base = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		furn(x, y, z, base.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		furn(x + facing.getStepX(), y, z + facing.getStepZ(),
				base.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
	}

	private static void chest(int x, int y, int z, Direction facing, int loot) {
		furn(x, y, z, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
		CHESTS.add(new int[]{x, y, z, loot});
	}

	// ------------------------------------------------------------------ the city

	/** A house whose door faces the given side; odd `style` is timber, even is stone. */
	private static void house(int x0, int z0, int w, int d, int h, Direction door, int style) {
		int y = VALLEY_Y + 1, x1 = x0 + w - 1, z1 = z0 + d - 1;
		BlockState wall = style % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : PLANKS;
		Block roof = style % 3 == 0 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.DARK_OAK_STAIRS;
		BlockState ridge = style % 3 == 0 ? TILE : Blocks.DARK_OAK_PLANKS.defaultBlockState();
		// The ground is the block row at y - 1 (VALLEY_Y); the floor is planks there and people stand at y.
		fill(x0, y - 4, z0, x1, y - 1, z1, Blocks.COBBLESTONE);
		fill(x0, y, z0, x1, y + h - 1, z1, wall);
		fill(x0, y, z0, x1, y, z1, Blocks.COBBLESTONE);
		fill(x0 + 1, y, z0 + 1, x1 - 1, y + h - 1, z1 - 1, AIR);
		fill(x0 + 1, y - 1, z0 + 1, x1 - 1, y - 1, z1 - 1, PLANKS);
		for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], y, c[1], c[0], y + h - 1, c[1], log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		// The roof's ridge runs along the longer side.
		boolean alongX = w >= d;
		int span = alongX ? d : w;
		for (int i = 0; i <= span / 2; i++) {
			int yy = y + h + i;
			if (alongX) {
				fill(x0 - 1, yy, z0 - 1 + i, x1 + 1, yy, z0 - 1 + i, stairs(roof, Direction.SOUTH, false));
				fill(x0 - 1, yy, z1 + 1 - i, x1 + 1, yy, z1 + 1 - i, stairs(roof, Direction.NORTH, false));
				if (z0 + i <= z1 - i) { fill(x0, yy, z0 + i, x0, yy, z1 - i, wall); fill(x1, yy, z0 + i, x1, yy, z1 - i, wall); }
			} else {
				fill(x0 - 1 + i, yy, z0 - 1, x0 - 1 + i, yy, z1 + 1, stairs(roof, Direction.EAST, false));
				fill(x1 + 1 - i, yy, z0 - 1, x1 + 1 - i, yy, z1 + 1, stairs(roof, Direction.WEST, false));
				if (x0 + i <= x1 - i) { fill(x0 + i, yy, z0, x1 - i, yy, z0, wall); fill(x0 + i, yy, z1, x1 - i, yy, z1, wall); }
			}
		}
		if (span % 2 == 1) {
			int mid = span / 2, yy = y + h + mid;
			if (alongX) fill(x0 - 1, yy, z0 + mid, x1 + 1, yy, z0 + mid, ridge);
			else fill(x0 + mid, yy, z0 - 1, x0 + mid, yy, z1 + 1, ridge);
		}
		int dx = door.getAxis() == Direction.Axis.X ? (door == Direction.EAST ? x1 : x0) : x0 + w / 2;
		int dz = door.getAxis() == Direction.Axis.Z ? (door == Direction.SOUTH ? z1 : z0) : z0 + d / 2;
		// The doorway is at ground level: no sill to jump over, a cobblestone step outside.
		fill(dx, y, dz, dx, y + 1, dz, AIR);
		set(dx, y - 1, dz, Blocks.COBBLESTONE);
		set(dx + door.getStepX(), y - 1, dz + door.getStepZ(), Blocks.COBBLESTONE);
		// Windows on the walls beside the door's.
		if (door.getAxis() == Direction.Axis.X) {
			set(x0 + w / 2, y + 2, z0, Blocks.GLASS); set(x0 + w / 2, y + 2, z1, Blocks.GLASS);
			set(door == Direction.EAST ? x0 : x1, y + 2, z0 + d / 2, Blocks.GLASS);
		} else {
			set(x0, y + 2, z0 + d / 2, Blocks.GLASS); set(x1, y + 2, z0 + d / 2, Blocks.GLASS);
			set(x0 + w / 2, y + 2, door == Direction.SOUTH ? z0 : z1, Blocks.GLASS);
		}
		set(x0 + w / 2, y + h - 1, z0 + d / 2, HANGING);
		set(x0 + w / 2, y + h, z0 + d / 2, wall);
		// Inside: a bed in the far corner, a table and a chest.
		Direction in = door.getOpposite();
		int bx = in == Direction.EAST ? x1 - 2 : in == Direction.WEST ? x0 + 2 : x0 + 1;
		int bz = in == Direction.SOUTH ? z1 - 2 : in == Direction.NORTH ? z0 + 2 : z0 + 1;
		bed(bx, y, bz, in, style % 2 == 0 ? Blocks.GREEN_BED : Blocks.WHITE_BED);
		int cx = in.getAxis() == Direction.Axis.X ? bx : x1 - 1, cz = in.getAxis() == Direction.Axis.Z ? bz : z1 - 1;
		furn(cx, y, cz, Blocks.CRAFTING_TABLE.defaultBlockState());
		VILLAGERS.add(new BlockPos(dx + door.getStepX() * 2, y - 1, dz + door.getStepZ() * 2));
	}

	private static void city() {
		int y = VALLEY_Y;
		// Streets: the main one runs north to the castle road, the cross street through the square.
		fill(-2, y, -50, 2, y, 126, Blocks.COBBLESTONE);
		fill(-1, y, -50, 1, y, 126, Blocks.STONE_BRICKS);
		fill(-74, y, 28, 74, y, 32, Blocks.COBBLESTONE);
		fill(-74, y, 29, 74, y, 31, Blocks.STONE_BRICKS);
		// The square with Doom's statue over a fountain.
		fill(-10, y, 20, 10, y, 40, Blocks.POLISHED_ANDESITE);
		fill(-9, y, 21, 9, y, 39, Blocks.STONE_BRICKS);
		fill(-4, y + 1, 26, 4, y + 1, 34, WALL_BASE);
		fill(-3, y + 1, 27, 3, y + 1, 33, Blocks.WATER);
		fill(-1, y + 1, 29, 1, y + 3, 31, WALL);
		fill(-1, y + 4, 30, 1, y + 8, 30, GREEN);
		fill(0, y + 4, 30, 0, y + 9, 30, WALL_BASE);
		fill(-1, y + 7, 30, 1, y + 7, 30, Blocks.IRON_BLOCK);
		set(0, y + 10, 30, Blocks.IRON_BLOCK);
		set(0, y + 10, 29, Blocks.CARVED_PUMPKIN.defaultBlockState());
		fill(-1, y + 11, 30, 1, y + 11, 30, GREEN);
		set(0, y + 12, 30, GREEN);
		for (int[] p : new int[][]{{-9, 21}, {9, 21}, {-9, 39}, {9, 39}}) lamp(p[0], y + 1, p[1]);
		// Market stalls.
		for (int i = 0; i < 3; i++) {
			int x = -8 + i * 6, z = 36;
			fill(x, y + 1, z, x, y + 3, z, Blocks.SPRUCE_FENCE); fill(x + 3, y + 1, z, x + 3, y + 3, z, Blocks.SPRUCE_FENCE);
			fill(x, y + 1, z + 2, x, y + 3, z + 2, Blocks.SPRUCE_FENCE); fill(x + 3, y + 1, z + 2, x + 3, y + 3, z + 2, Blocks.SPRUCE_FENCE);
			fill(x, y + 4, z, x + 3, y + 4, z + 2, i % 2 == 0 ? GREEN : Blocks.WHITE_WOOL.defaultBlockState());
			fill(x + 1, y + 1, z + 1, x + 2, y + 1, z + 1, Blocks.BARREL);
		}
		int style = 0;
		for (int z : new int[]{104, 88, 72, 54, 6, -10, -26, -42}) {
			house(-14 - style % 3, z, 9 + style % 3, 9, 4 + style % 2, Direction.EAST, style++);
			house(6, z, 9 + style % 3, 9, 4 + style % 2, Direction.WEST, style++);
			lamp(-4, y + 1, z + 12); lamp(4, y + 1, z + 12);
		}
		for (int x : new int[]{22, 38, 54}) {
			house(x, 16, 11, 9 + style % 2, 4, Direction.SOUTH, style++);
			house(x, 36, 11, 9, 5, Direction.NORTH, style++);
			house(-x - 10, 16, 11, 9 + style % 2, 5, Direction.SOUTH, style++);
			house(-x - 10, 36, 11, 9, 4, Direction.NORTH, style++);
			lamp(x - 2, y + 1, 27); lamp(-x + 2, y + 1, 33);
		}
		// The watch tower at the head of the main street.
		fill(7, y - 8, -63, 15, y, -57, WALL_BASE);
		shell(8, y + 1, -62, 14, y + 16, -56, 1, WALL);
		fill(11, y + 1, -56, 11, y + 2, -56, AIR);
		fill(7, y + 17, -63, 15, y + 17, -57, TILE);
		crenels(7, y + 18, -63, 15, -57, WALL);
		banner(10, y + 14, -55, true);
		// The gate back to the wider world.
		fill(-4, y + 1, 127, -3, y + 7, 129, WALL); fill(3, y + 1, 127, 4, y + 7, 129, WALL);
		fill(-4, y + 8, 127, 4, y + 9, 129, WALL);
		fill(-2, y + 7, 128, 2, y + 7, 128, Blocks.EMERALD_BLOCK);
		set(-3, y + 8, 126, Blocks.SEA_LANTERN); set(3, y + 8, 126, Blocks.SEA_LANTERN);
		crenels(-4, y + 10, 127, 4, 129, WALL);
		fill(-3, y, 121, 3, y, 130, Blocks.POLISHED_DEEPSLATE);
		banner(-4, y + 6, 126, true); banner(3, y + 6, 126, true);
	}

	private static void road() {
		for (int z = -50; z >= -190; z--) {
			double h = roadHeight(z);
			int y = (int) Math.floor(h);
			fill(-3, y, z, 3, y, z, z % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
			fill(-3, y + 1, z, 3, y + 6, z, AIR);
			if (h - y >= 0.5) fill(-3, y + 1, z, 3, y + 1, z, slab(Blocks.STONE_BRICK_SLAB, false));
			if (z % 14 == 0) { lamp(-4, y + 1, z); lamp(4, y + 1, z); set(-4, y, z, Blocks.STONE_BRICKS); set(4, y, z, Blocks.STONE_BRICKS); }
		}
		fill(-3, CASTLE_Y, -203, 3, CASTLE_Y, -190, Blocks.STONE_BRICKS);
	}

	// ------------------------------------------------------------------ the castle

	private static void castle() {
		int f = FLOOR, g = CASTLE_Y;
		// Yard paving and the outer wall with its towers.
		fill(-43, g, -251, 43, g, -207, Blocks.COBBLESTONE);
		fill(-3, g, -251, 3, g, -204, Blocks.POLISHED_ANDESITE);
		for (int[] w : new int[][]{{-46, -206, 46, -204}, {-46, -298, 46, -296}, {-46, -298, -44, -204}, {44, -298, 46, -204}}) {
			fill(w[0], f, w[1], w[2], f + 1, w[3], WALL_BASE);
			fill(w[0], f + 2, w[1], w[2], f + 11, w[3], WALL);
			crenels(w[0], f + 12, w[1], w[2], w[3], WALL);
		}
		for (int[] c : new int[][]{{-45, -205}, {45, -205}, {-45, -297}, {45, -297}}) tower(c[0], c[1], 6, f, f + 19, 7);
		// The gatehouse: two square towers, the passage and the portcullis.
		fill(-9, f, -209, -4, f + 16, -201, WALL); fill(4, f, -209, 9, f + 16, -201, WALL);
		fill(-9, f + 8, -209, 9, f + 13, -201, WALL);
		crenels(-9, f + 17, -209, -4, -201, WALL); crenels(4, f + 17, -209, 9, -201, WALL);
		crenels(-3, f + 14, -209, 3, -201, WALL);
		fill(-3, f, -209, 3, f + 6, -201, AIR);
		fill(GATE[0], GATE[1], GATE[2], GATE[3], GATE[4], GATE[5], Blocks.IRON_BARS);
		banner(-7, f + 13, -200, true); banner(6, f + 13, -200, true);
		set(-4, f + 4, -200, LANTERN.setValue(BlockStateProperties.HANGING, false)); set(4, f + 4, -200, LANTERN);
		fill(-5, f + 3, -200, -4, f + 3, -200, WALL); fill(4, f + 3, -200, 5, f + 3, -200, WALL);

		// Barracks on the west of the yard, the forge on the east.
		for (int side = -1; side <= 1; side += 2) {
			int x0 = side < 0 ? -42 : 24, x1 = side < 0 ? -24 : 42, door = side < 0 ? x1 : x0;
			shell(x0, f, -246, x1, f + 6, -214, 1, side < 0 ? Blocks.STONE_BRICKS.defaultBlockState() : WALL);
			fill(x0, g, -246, x1, g, -214, Blocks.SPRUCE_PLANKS);
			fill(x0 - 1, f + 7, -247, x1 + 1, f + 7, -213, TILE);
			crenels(x0 - 1, f + 8, -247, x1 + 1, -213, WALL);
			fill(door, f, -231, door, f + 2, -229, AIR);
			for (int z = -242; z <= -218; z += 6) {
				set(side < 0 ? x0 : x1, f + 3, z, Blocks.GLASS); set(door, f + 3, z == -230 ? z + 3 : z, Blocks.GLASS);
				set((x0 + x1) / 2, f + 5, z, HANGING); set((x0 + x1) / 2, f + 6, z, TILE);
			}
			banner(door + (side < 0 ? 1 : -1), f + 6, -227, false);
		}
		for (int z = -243; z <= -219; z += 6) {  // bunks along the barracks' west wall
			bed(-40, f, z, Direction.WEST, Blocks.GREEN_BED);
			bed(-40, f, z + 2, Direction.WEST, Blocks.GREEN_BED);
			chest(-39, f, z + 3, Direction.EAST, 1);
		}
		fill(-27, f, -244, -26, f, -240, Blocks.HAY_BLOCK);
		for (int z = -242; z <= -224; z += 6) {  // the forge
			furn(40, f, z, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
			furn(40, f, z + 1, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
			set(36, f, z, Blocks.ANVIL);
		}
		set(30, f, -218, Blocks.SMITHING_TABLE); set(31, f, -218, Blocks.CRAFTING_TABLE); set(32, f, -218, Blocks.GRINDSTONE.defaultBlockState());
		chest(34, f, -216, Direction.NORTH, 1); chest(35, f, -216, Direction.NORTH, 1);
		fill(26, f, -244, 28, f + 1, -242, Blocks.IRON_BLOCK);
		// Yard dressing.
		for (int[] p : new int[][]{{-12, -212}, {12, -212}, {-12, -246}, {12, -246}, {-20, -229}, {20, -229}}) lamp(p[0], f, p[1]);
		for (int x = -18; x <= -12; x += 3) { fill(x, f, -238, x, f + 1, -238, Blocks.SPRUCE_FENCE); set(x, f + 2, -238, Blocks.HAY_BLOCK); }

		// The keep: throne room in the middle, two wings of two floors.
		fill(-42, f, -295, 42, f + 17, -252, WALL);
		fill(-42, f, -295, 42, f + 1, -252, WALL_BASE);
		fill(-42, g, -295, 42, g, -252, Blocks.POLISHED_DEEPSLATE);
		fill(-21, f, -293, 21, f + 16, -254, AIR);
		for (int side = -1; side <= 1; side += 2) {
			int x0 = side < 0 ? -40 : 24, x1 = side < 0 ? -24 : 40;
			fill(x0, f, -293, x1, f + 8, -254, AIR);
			fill(x0, f + 10, -293, x1, f + 15, -254, AIR);
			fill(x0, f + 9, -293, x1, f + 9, -254, Blocks.SPRUCE_PLANKS);
			// Stairs along the north wall, rising away from the throne room.
			for (int i = 0; i < 10; i++) {
				int x = side < 0 ? -26 - i : 26 + i;
				fill(x, f + i, -293, x, f + i, -292, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, side < 0 ? Direction.WEST : Direction.EAST, false));
				if (i > 0) fill(x, f, -293, x, f + i - 1, -292, WALL);
				fill(x, f + i + 1, -293, x, f + 15, -292, AIR);
			}
			for (int z = -290; z <= -258; z += 8) {
				set(side < 0 ? -41 : 41, f + 3, z, GLASS); set(side < 0 ? -42 : 42, f + 3, z, GLASS);
				fill(side < 0 ? -42 : 41, f + 12, z, side < 0 ? -41 : 42, f + 13, z, GLASS);
				set((x0 + x1) / 2, f + 8, z + 2, HANGING); set((x0 + x1) / 2, f + 15, z + 2, HANGING);
			}
			// A door from each wing into the throne room.
			int wx = side < 0 ? -23 : 22;
			if (side < 0) fill(wx, f, -262, wx + 1, f + 3, -260, AIR);
		}
		fill(LAB_DOOR[0], LAB_DOOR[1], LAB_DOOR[2], LAB_DOOR[3], LAB_DOOR[4], LAB_DOOR[5], GLASS);
		// Roof, battlements and the keep's two towers.
		fill(-43, f + 18, -296, 43, f + 18, -251, TILE);
		crenels(-43, f + 19, -296, 43, -251, WALL);
		tower(-38, -291, 5, f + 18, f + 30, 6); tower(38, -291, 5, f + 18, f + 30, 6);
		// Throne room: doors, pillars, runner, dais, throne, windows, chandeliers.
		fill(THRONE_DOOR[0], THRONE_DOOR[1], THRONE_DOOR[2], THRONE_DOOR[3], THRONE_DOOR[4], THRONE_DOOR[5], GLASS);
		fill(-5, f, -251, -4, f + 9, -250, WALL); fill(4, f, -251, 5, f + 9, -250, WALL); fill(-5, f + 9, -251, 5, f + 10, -250, WALL);
		banner(-7, f + 12, -251, true); banner(6, f + 12, -251, true);
		fill(-21, g, -293, 21, g, -254, Blocks.POLISHED_DEEPSLATE);
		fill(-2, g, -287, 2, g, -254, Blocks.GREEN_TERRACOTTA);
		for (int z = -262; z >= -284; z -= 11) for (int side = -1; side <= 1; side += 2) {
			int x = side * 18;
			fill(x, f, z, x + side, f + 16, z - 1, WALL_BASE);
			set(x - side, f + 5, z, LANTERN.setValue(BlockStateProperties.HANGING, false));
			fill(x - side, f + 4, z, x - side, f + 4, z, WALL);
			banner(x - side, f + 13, z - 1, false);
		}
		fill(-8, f, -293, 8, f, -288, WALL_BASE);
		fill(-5, f + 1, -293, 5, f + 1, -290, WALL_BASE);
		fill(-8, f, -287, 8, f, -287, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
		fill(-5, f + 1, -289, 5, f + 1, -289, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
		fill(-1, f + 2, -292, 1, f + 2, -291, GOLD);
		fill(-1, f + 3, -292, 1, f + 6, -292, GREEN);
		fill(-2, f + 2, -292, -2, f + 4, -291, GOLD); fill(2, f + 2, -292, 2, f + 4, -291, GOLD);
		set(0, f + 7, -292, GOLD);
		for (int x = -15; x <= 15; x += 10) {
			fill(x, f + 7, -295, x + 1, f + 13, -294, GLASS);
			if (Math.abs(x) > 6) fill(x, f + 9, -253, x + 1, f + 13, -252, GLASS);
		}
		for (int[] p : new int[][]{{-9, -264}, {9, -264}, {-9, -280}, {9, -280}, {0, -272}}) {
			fill(p[0], f + 13, p[1], p[0], f + 16, p[1], Blocks.IRON_CHAIN.defaultBlockState());
			set(p[0], f + 12, p[1], HANGING);
			set(p[0] - 1, f + 13, p[1], Blocks.SEA_LANTERN); set(p[0] + 1, f + 13, p[1], Blocks.SEA_LANTERN);
		}
		for (int[] p : new int[][]{{-20, -292}, {20, -292}, {-20, -255}, {20, -255}}) set(p[0], g, p[1], Blocks.SEA_LANTERN);

		// Light: glowing tiles in the hall's floor and ceiling, and in the floors and ceilings of the wings.
		for (int x = -16; x <= 16; x += 8) for (int z = -290; z <= -258; z += 8) {
			if (Math.abs(x) > 2) set(x, g, z, Blocks.SEA_LANTERN);
			set(x, f + 17, z, Blocks.SEA_LANTERN);
		}
		for (int z = -291; z <= -255; z += 4) for (int y = f + 2; y <= f + 10; y += 8) {
			set(-22, y, z, Blocks.SEA_LANTERN); set(22, y, z, Blocks.SEA_LANTERN);
		}
		for (int x = -19; x <= 19; x += 4) {
			set(x, f + 3, -294, Blocks.SEA_LANTERN);
			if (Math.abs(x) > 5) set(x, f + 3, -253, Blocks.SEA_LANTERN);
		}
		for (int side = -1; side <= 1; side += 2) for (int x = 28; x <= 36; x += 4) for (int z = -288; z <= -258; z += 6) {
			set(side * x, f + 9, z, Blocks.SEA_LANTERN);
			set(side * x, f + 16, z, Blocks.SEA_LANTERN);
		}

		// West wing, ground floor: armoury and dining hall.
		fill(-40, f, -274, -25, f + 8, -274, Blocks.STONE_BRICKS);
		fill(-33, f, -274, -31, f + 2, -274, AIR);
		for (int z = -290; z <= -278; z += 4) { chest(-39, f, z, Direction.EAST, 1); set(-39, f, z + 1, Blocks.IRON_BLOCK); }
		fill(-34, f, -268, -29, f, -260, Blocks.SPRUCE_FENCE);
		fill(-34, f + 1, -268, -29, f + 1, -260, Blocks.SPRUCE_PRESSURE_PLATE);
		for (int z = -268; z <= -260; z += 2) {
			set(-36, f, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false)); set(-27, f, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		}
		furn(-39, f, -257, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		furn(-39, f, -258, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		chest(-39, f, -260, Direction.EAST, 0); chest(-39, f, -261, Direction.EAST, 0);
		set(-39, f, -263, Blocks.CRAFTING_TABLE); set(-39, f, -264, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));

		// East wing, ground floor: the laboratory, its generator and the service door through the outer wall.
		int gx = GENERATOR.getX(), gz = GENERATOR.getZ();
		fill(gx - 2, f, gz - 2, gx + 2, f, gz + 2, Blocks.WAXED_CUT_COPPER);
		fill(gx - 1, f + 1, gz - 1, gx + 1, f + 1, gz + 1, Blocks.WAXED_COPPER_BLOCK);
		set(gx, f + 2, gz, Blocks.SEA_LANTERN);
		for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			fill(gx + c[0], f + 1, gz + c[1], gx + c[0], f + 5, gz + c[1], Blocks.LIGHTNING_ROD.defaultBlockState());
			set(gx + c[0], f + 6, gz + c[1], Blocks.REDSTONE_LAMP);
		}
		fill(gx, f + 3, gz, gx, f + 7, gz, GLASS);
		for (int z = -290; z <= -280; z += 5) {
			fill(38, f, z, 39, f + 2, z + 2, Blocks.GLASS); fill(38, f, z + 1, 38, f + 1, z + 1, Blocks.WATER);
			set(26, f, z, Blocks.CAULDRON); set(26, f, z + 1, Blocks.SMITHING_TABLE);
		}
		fill(26, f, -258, 26, f, -256, Blocks.POLISHED_DEEPSLATE); furn(26, f + 1, -257, Blocks.BREWING_STAND.defaultBlockState());
		chest(39, f, -257, Direction.WEST, 2); chest(39, f, -259, Direction.WEST, 2);
		fill(41, f, -285, 46, f + 2, -283, AIR);
		fill(43, f + 3, -286, 43, f + 3, -282, WALL); fill(43, f, -286, 43, f + 2, -286, WALL); fill(43, f, -282, 43, f + 2, -282, WALL);
		fill(42, g, -286, 47, g, -282, Blocks.POLISHED_DEEPSLATE);
		set(47, f + 2, -287, LANTERN); set(47, f + 1, -287, WALL); set(47, f, -287, WALL);

		// Upper floor, west: three bedrooms off a corridor.
		int u = f + 10;
		fill(-33, u, -288, -33, u + 5, -254, PLANKS);
		for (int z = -282; z <= -258; z += 12) {
			fill(-40, u, z + 6, -34, u + 5, z + 6, PLANKS);
			fill(-33, u, z, -33, u + 1, z, AIR);
			bed(-38, u, z, Direction.WEST, Blocks.GREEN_BED);
			chest(-39, u, z + 2, Direction.EAST, 0);
			set(-39, u, z - 2, Blocks.CRAFTING_TABLE); set(-35, u, z + 4, Blocks.BOOKSHELF);
			fill(-40, g + 10, z - 4, -34, g + 10, z + 5, Blocks.GREEN_TERRACOTTA);
		}
		// Upper floor, east: the library and the master bedroom.
		fill(24, u, -272, 40, u + 5, -272, PLANKS);
		fill(31, u, -272, 33, u + 2, -272, AIR);
		fill(39, u, -290, 39, u + 3, -274, Blocks.BOOKSHELF); fill(25, u, -286, 25, u + 3, -274, Blocks.BOOKSHELF);
		furn(32, u, -281, Blocks.ENCHANTING_TABLE.defaultBlockState());
		fill(30, u, -283, 34, u, -283, Blocks.BOOKSHELF); fill(30, u, -279, 34, u, -279, Blocks.BOOKSHELF);
		furn(28, u, -276, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		fill(27, g + 10, -269, 37, g + 10, -257, Blocks.GREEN_TERRACOTTA);
		bed(37, u, -263, Direction.EAST, Blocks.GREEN_BED); bed(37, u, -262, Direction.EAST, Blocks.GREEN_BED);
		chest(39, u, -258, Direction.WEST, 3); chest(39, u, -267, Direction.WEST, 3);
		set(26, u, -256, Blocks.CRAFTING_TABLE);
		furn(26, u, -258, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		set(26, u, -260, Blocks.ANVIL);
		fill(30, u, -262, 31, u, -261, Blocks.SPRUCE_FENCE); fill(30, u + 1, -262, 31, u + 1, -261, Blocks.SPRUCE_PRESSURE_PLATE);
	}

	static {
		city();
		road();
		castle();
	}

	private LatveriaPlan() {}
}
