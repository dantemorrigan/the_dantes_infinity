package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;
import java.util.Random;

/**
 * The tools Latveria's builders share: boxes written into the plan, block states with their directions set, and a map
 * of what the city's ground is already used for, so that a lamp, a barrel or a tree never lands on a wall.
 */
final class LatveriaKit {
	static final int G = LatveriaPlan.VALLEY_Y;
	/** The city's land: the ground row is G, people stand at G + 1. */
	static final int CX0 = -96, CX1 = 96, CZ0 = -72, CZ1 = 150;
	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final Random RND = new Random(0xD00D5L);

	private static final boolean[][] TAKEN = new boolean[CX1 - CX0 + 1][CZ1 - CZ0 + 1];

	private LatveriaKit() {}

	// ------------------------------------------------------------------ writing

	static void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		LatveriaPlan.OPS.add(new LatveriaPlan.Op(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), state));
	}

	static void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
	}

	static void set(int x, int y, int z, BlockState state) { fill(x, y, z, x, y, z, state); }

	static void set(int x, int y, int z, Block block) { fill(x, y, z, x, y, z, block.defaultBlockState()); }

	/** Blocks that need a block entity or an owner go through the server's furnishing pass. */
	static void furn(int x, int y, int z, BlockState state) {
		LatveriaPlan.FURNITURE.add(new LatveriaPlan.Op(x, y, z, x, y, z, state));
	}

	// ------------------------------------------------------------------ the map of used ground

	static boolean inCity(int x, int z) { return x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1; }

	static void reserve(int x0, int z0, int x1, int z1) {
		for (int x = Math.max(CX0, Math.min(x0, x1)); x <= Math.min(CX1, Math.max(x0, x1)); x++)
			for (int z = Math.max(CZ0, Math.min(z0, z1)); z <= Math.min(CZ1, Math.max(z0, z1)); z++) TAKEN[x - CX0][z - CZ0] = true;
	}

	static boolean free(int x, int z) { return inCity(x, z) && !TAKEN[x - CX0][z - CZ0]; }

	static boolean free(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) if (!free(x, z)) return false;
		return true;
	}

	// ------------------------------------------------------------------ states

	static BlockState stairs(Block block, Direction facing, boolean top) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block block, boolean top) {
		return block.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState log(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	static BlockState hanging() { return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true); }

	static BlockState standing() { return Blocks.LANTERN.defaultBlockState(); }

	/** A glass pane (or iron bars) in a wall running along the given axis, joined to its neighbours on that axis. */
	static BlockState pane(Block block, Direction.Axis along) {
		BlockState s = block.defaultBlockState();
		if (along == Direction.Axis.X) return s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
		return s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	/** An open trapdoor lying flat against the wall behind it; {@code outward} points away from that wall. */
	static BlockState shutter(Block block, Direction outward) {
		return block.defaultBlockState().setValue(TrapDoorBlock.FACING, outward).setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
	}

	/** A vine clinging to the block on the {@code toward} side of its cell. */
	static BlockState vine(Direction toward) {
		return Blocks.VINE.defaultBlockState().setValue(VineBlock.PROPERTY_BY_DIRECTION.get(toward), true);
	}

	static BlockState leaves(Block block) {
		return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	static BlockState barrel() {
		return Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
	}

	static BlockState hay() { return Blocks.HAY_BLOCK.defaultBlockState(); }

	/** One half of a closed door that opens into the building ({@code inward}), hinged on the given side. */
	static BlockState door(Block door, Direction inward, boolean upper, boolean rightHinge) {
		return door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, inward)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, upper ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
				.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, rightHinge ? net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT : net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
				.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false);
	}

	/** True for a doorway: open air or a door. */
	static boolean doorway(BlockState s) { return s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock; }

	/** One of the given blocks, by chance. */
	static BlockState pick(List<BlockState> states) { return states.get(RND.nextInt(states.size())); }

	static BlockState pick(Block... blocks) { return blocks[RND.nextInt(blocks.length)].defaultBlockState(); }

	static int rand(int lo, int hi) { return lo + RND.nextInt(hi - lo + 1); }

	static boolean chance(double p) { return RND.nextDouble() < p; }

	// ------------------------------------------------------------------ a building's own coordinates

	/**
	 * A frame for building in local terms: {@code u} runs along the front, {@code v} runs into the building from its front
	 * wall (0) to its back. The same code then builds a house facing any of the four ways.
	 */
	static final class Frame {
		final int ox, oz, ux, uz, vx, vz;
		final Direction front;

		/** The building's front-left corner (ox, oz) looking at it from outside; {@code front} is the way its door faces. */
		Frame(int ox, int oz, Direction front) {
			this.ox = ox;
			this.oz = oz;
			this.front = front;
			// Looking out of the door, u increases to the right; v goes opposite to the facing.
			Direction right = front.getClockWise();
			this.ux = right.getStepX();
			this.uz = right.getStepZ();
			this.vx = -front.getStepX();
			this.vz = -front.getStepZ();
		}

		int x(int u, int v) { return ox + u * ux + v * vx; }

		int z(int u, int v) { return oz + u * uz + v * vz; }

		/** A local direction in the world: +u, -u, +v (into the building), -v (toward the street). */
		Direction dir(int du, int dv) {
			int dx = du * ux + dv * vx, dz = du * uz + dv * vz;
			return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
		}

		Direction toStreet() { return front; }

		Direction.Axis alongU() { return ux != 0 ? Direction.Axis.X : Direction.Axis.Z; }

		Direction.Axis alongV() { return ux != 0 ? Direction.Axis.Z : Direction.Axis.X; }

		void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
			LatveriaKit.fill(x(u0, v0), y0, z(u0, v0), x(u1, v1), y1, z(u1, v1), state);
		}

		void fill(int u0, int y0, int v0, int u1, int y1, int v1, Block block) {
			fill(u0, y0, v0, u1, y1, v1, block.defaultBlockState());
		}

		void set(int u, int y, int v, BlockState state) { LatveriaKit.set(x(u, v), y, z(u, v), state); }

		void set(int u, int y, int v, Block block) { LatveriaKit.set(x(u, v), y, z(u, v), block); }

		void furn(int u, int y, int v, BlockState state) { LatveriaKit.furn(x(u, v), y, z(u, v), state); }

		void reserve(int u0, int v0, int u1, int v1) { LatveriaKit.reserve(x(u0, v0), z(u0, v0), x(u1, v1), z(u1, v1)); }

		boolean free(int u0, int v0, int u1, int v1) { return LatveriaKit.free(x(u0, v0), z(u0, v0), x(u1, v1), z(u1, v1)); }

		/** Stairs rising toward local direction (du, dv). */
		BlockState stairs(Block block, int du, int dv, boolean top) { return LatveriaKit.stairs(block, dir(du, dv), top); }

		/** A log running along local u or v. */
		BlockState beam(Block block, boolean alongU) {
			return log(block, alongU ? alongU() : alongV());
		}
	}
}
