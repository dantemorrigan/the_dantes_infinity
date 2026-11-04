package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sanctuary II as a real wreck in the world. The ship's model (the very same geometry file the client draws) is
 * voxelized in the pose it ends the crash in: the hull half buried, the four wings torn off and lying around it,
 * each resting on the ground. Blocks are placed a slice of the budget per tick, so nothing stalls the server, and
 * they stay in the world like any build, in whichever dimension the ship came down.
 */
public final class ShipWreck {
	/** Blocks per model unit: the ship is drawn 13x the model's block size (16 units per block). */
	static final float SCALE = 13f / 16f;
	/** The hull sits this deep below the ground level of the impact site. */
	public static final int SINK = 5;

	/** One piece (the hull, or a wing): voxel offsets from the ship origin and a material id per voxel. */
	static final class Part {
		final String name;
		final int[] dx, dy, dz;
		final byte[] mat;
		final boolean wing;

		Part(String name, boolean wing, int[] dx, int[] dy, int[] dz, byte[] mat) {
			this.name = name; this.wing = wing; this.dx = dx; this.dy = dy; this.dz = dz; this.mat = mat;
		}

		int size() { return dx.length; }
	}

	private record Cube(float x0, float y0, float z0, float x1, float y1, float z1, byte mat) {}

	private record Bone(String name, float px, float py, float pz, List<Cube> cubes) {}

	/** Material classes; the block is picked per position so the plates look weathered and uneven. */
	static final byte PANEL_A = 0, PANEL_B = 1, PANEL_C = 2, LIGHT_ORANGE = 3, LIGHT_BLUE = 4;

	private static List<Bone> bones;

	private static synchronized List<Bone> bones() {
		if (bones != null) return bones;
		List<Bone> out = new ArrayList<>();
		try (Reader r = new InputStreamReader(ShipWreck.class.getResourceAsStream("/assets/" + InfinityGauntlet.MOD_ID + "/thanos/sanctuary_ii.geo.json"), StandardCharsets.UTF_8)) {
			JsonObject geo = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
			for (JsonElement e : geo.getAsJsonArray("bones")) {
				JsonObject b = e.getAsJsonObject();
				JsonArray pv = b.has("pivot") ? b.getAsJsonArray("pivot") : null;
				float px = pv == null ? 0 : pv.get(0).getAsFloat(), py = pv == null ? 0 : pv.get(1).getAsFloat(), pz = pv == null ? 0 : pv.get(2).getAsFloat();
				List<Cube> cubes = new ArrayList<>();
				if (b.has("cubes")) {
					for (JsonElement ce : b.getAsJsonArray("cubes")) {
						JsonObject c = ce.getAsJsonObject();
						JsonArray o = c.getAsJsonArray("origin"), s = c.getAsJsonArray("size");
						float ox = o.get(0).getAsFloat(), oy = o.get(1).getAsFloat(), oz = o.get(2).getAsFloat();
						float sx = s.get(0).getAsFloat(), sy = s.get(1).getAsFloat(), sz = s.get(2).getAsFloat();
						// A plate thinner than a block would vanish between the voxel centers: thicken it to one block.
						float min = 1.3f;
						float gx = Math.max(0, (min - sx) / 2), gy = Math.max(0, (min - sy) / 2), gz = Math.max(0, (min - sz) / 2);
						byte mat = PANEL_A;
						if (c.has("uv") && c.get("uv").isJsonObject()) {
							for (var face : c.getAsJsonObject("uv").entrySet()) {
								float u = face.getValue().getAsJsonObject().getAsJsonArray("uv").get(0).getAsFloat();
								mat = u < 8 ? PANEL_A : u < 24 ? PANEL_B : u < 40 ? PANEL_C : u < 56 ? LIGHT_ORANGE : LIGHT_BLUE;
								break;
							}
						}
						cubes.add(new Cube(ox - gx, oy - gy, oz - gz, ox + sx + gx, oy + sy + gy, oz + sz + gz, mat));
					}
				}
				out.add(new Bone(b.get("name").getAsString(), px, py, pz, cubes));
			}
		} catch (Exception ex) {
			InfinityGauntlet.LOGGER.error("Could not read the Sanctuary II geometry for the wreck", ex);
		}
		bones = out;
		return out;
	}

	/** The wings' final pose (model units / degrees): the same numbers the client uses while they tear off. */
	private static float[] wingPose(String name) {
		String[] wings = {"port_wing_1", "port_wing_2", "starboard_wing_1", "starboard_wing_2"};
		float[][] how = {{1, 1, 1}, {1, -1, 0.6f}, {-1, 1, 0.8f}, {-1, -1, 1.2f}};
		for (int i = 0; i < 4; i++) {
			if (!wings[i].equals(name)) continue;
			float side = how[i][0], fore = how[i][1], m = how[i][2];
			// ox, oy, oz, rx, ry, rz
			return new float[]{-side * 22 * m, -14 * m, -fore * 8, fore * 9, 0, side * 24 * m};
		}
		return null;
	}

	/** Voxelizes the ship in its final pose. Pure math, safe on any thread; roughly 50-100k blocks. */
	static List<Part> plan(float yawDeg, float pitchDeg, float rollDeg) {
		Quaternionf rot = new Quaternionf().rotateY(yawDeg * (float) Math.PI / 180f).rotateX(pitchDeg * (float) Math.PI / 180f)
				.rotateZ(rollDeg * (float) Math.PI / 180f);
		Quaternionf inv = new Quaternionf(rot).invert();
		List<Part> parts = new ArrayList<>();
		Int2ByteOpenHashMap hull = new Int2ByteOpenHashMap();
		for (Bone bone : bones()) {
			if (bone.cubes.isEmpty()) continue;
			float[] wp = wingPose(bone.name);
			Matrix4f m = new Matrix4f();
			if (wp != null) {
				float d = (float) Math.PI / 180f;
				m.translate(-wp[0], wp[1], wp[2]).translate(bone.px, bone.py, bone.pz)
						.rotateZ(-wp[5] * d).rotateY(-wp[4] * d).rotateX(-wp[3] * d).translate(-bone.px, -bone.py, -bone.pz);
			}
			Matrix4f mInv = new Matrix4f(m).invert();
			Int2ByteOpenHashMap set = wp != null ? new Int2ByteOpenHashMap() : hull;
			for (Cube c : bone.cubes) fill(set, c, m, mInv, rot, inv);
			if (wp != null) parts.add(shell(bone.name, true, set));
		}
		parts.add(0, shell("hull", false, hull));
		return parts;
	}

	private static int key(int dx, int dy, int dz) {
		return ((dx + 512) & 0x3FF) << 20 | ((dy + 512) & 0x3FF) << 10 | ((dz + 512) & 0x3FF);
	}

	private static int kx(int k) { return ((k >> 20) & 0x3FF) - 512; }
	private static int ky(int k) { return ((k >> 10) & 0x3FF) - 512; }
	private static int kz(int k) { return (k & 0x3FF) - 512; }

	private static void fill(Int2ByteOpenHashMap set, Cube c, Matrix4f m, Matrix4f mInv, Quaternionf rot, Quaternionf inv) {
		// World-space box of the transformed cube.
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		Vector3f v = new Vector3f();
		for (int i = 0; i < 8; i++) {
			v.set((i & 1) == 0 ? c.x0 : c.x1, (i & 2) == 0 ? c.y0 : c.y1, (i & 4) == 0 ? c.z0 : c.z1);
			m.transformPosition(v).mul(SCALE).rotate(rot);
			minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
			minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
			minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
		}
		Vector3f q = new Vector3f();
		for (int dx = (int) Math.floor(minX) - 1; dx <= (int) Math.ceil(maxX) + 1; dx++) {
			for (int dz = (int) Math.floor(minZ) - 1; dz <= (int) Math.ceil(maxZ) + 1; dz++) {
				for (int dy = (int) Math.floor(minY - 0.5) - 1; dy <= (int) Math.ceil(maxY - 0.5) + 1; dy++) {
					// The block's center, back into the model.
					q.set(dx, dy + 0.5f, dz).rotate(inv).div(SCALE);
					mInv.transformPosition(q);
					if (q.x >= c.x0 && q.x <= c.x1 && q.y >= c.y0 && q.y <= c.y1 && q.z >= c.z0 && q.z <= c.z1) set.put(key(dx, dy, dz), c.mat);
				}
			}
		}
	}

	/** Keeps the skin two blocks thick: the inside of the hull stays hollow, open to whoever digs in. */
	private static Part shell(String name, boolean wing, Int2ByteOpenHashMap set) {
		List<Integer> keep = new ArrayList<>();
		for (int k : set.keySet()) {
			int x = kx(k), y = ky(k), z = kz(k);
			if (!(solid(set, x, y, z, 1) && solid(set, x, y, z, 2))) keep.add(k);
		}
		int n = keep.size();
		int[] dx = new int[n], dy = new int[n], dz = new int[n];
		byte[] mat = new byte[n];
		// Lowest first: it is built up from the ground.
		keep.sort((a, b) -> Integer.compare(ky(a), ky(b)));
		for (int i = 0; i < n; i++) {
			int k = keep.get(i);
			dx[i] = kx(k); dy[i] = ky(k); dz[i] = kz(k);
			mat[i] = set.get(k);
		}
		return new Part(name, wing, dx, dy, dz, mat);
	}

	private static boolean solid(Int2ByteOpenHashMap s, int x, int y, int z, int r) {
		return s.containsKey(key(x + r, y, z)) && s.containsKey(key(x - r, y, z)) && s.containsKey(key(x, y + r, z))
				&& s.containsKey(key(x, y - r, z)) && s.containsKey(key(x, y, z + r)) && s.containsKey(key(x, y, z - r));
	}

	// ------------------------------------------------------------------ placing

	private static BlockState block(byte mat, int x, int y, int z) {
		int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
		int r = Math.floorMod(h >> 3, 100);
		return switch (mat) {
			case LIGHT_ORANGE -> (r < 55 ? Blocks.SHROOMLIGHT : Blocks.POLISHED_BLACKSTONE_BRICKS).defaultBlockState();
			case LIGHT_BLUE -> (r < 55 ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK).defaultBlockState();
			case PANEL_B -> (r < 45 ? Blocks.BLACKSTONE : r < 70 ? Blocks.IRON_BLOCK : r < 85 ? Blocks.POLISHED_BLACKSTONE : Blocks.COAL_BLOCK).defaultBlockState();
			case PANEL_C -> (r < 40 ? Blocks.DEEPSLATE_TILES : r < 60 ? Blocks.IRON_BLOCK : r < 80 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.CRACKED_DEEPSLATE_TILES).defaultBlockState();
			default -> (r < 50 ? Blocks.POLISHED_BLACKSTONE_BRICKS : r < 68 ? Blocks.IRON_BLOCK : r < 84 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.BLACKSTONE).defaultBlockState();
		};
	}

	private static boolean replaceable(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return !s.is(Blocks.BEDROCK) && !s.hasBlockEntity() && s.getDestroySpeed(level, p) >= 0;
	}

	/** A wreck being built: step() places blocks until everything is down. */
	public static final class Build {
		private final ServerLevel level;
		private final BlockPos origin;
		private final List<Part> parts;
		private final int[] shift;
		private int part, index;
		private final List<long[]> deferred = new ArrayList<>();
		private int deferredScan;
		private int total, placed, waited;
		private boolean decorated;

		/** {@code origin} is the impact point on the ground; the ship's center sits {@link #SINK} blocks below it. */
		Build(ServerLevel level, BlockPos origin, List<Part> parts) {
			this.level = level;
			this.origin = origin;
			this.parts = parts;
			this.shift = new int[parts.size()];
			for (Part p : parts) total += p.size();
			settleWings();
		}

		public int total() { return total; }

		/** Each wing lies on the ground, not in the air: it falls to the highest ground under it. */
		private void settleWings() {
			for (int i = 0; i < parts.size(); i++) {
				Part p = parts.get(i);
				if (!p.wing || p.size() == 0) continue;
				int baseY = origin.getY() - SINK;
				int minDy = Integer.MAX_VALUE;
				for (int dy : p.dy) minDy = Math.min(minDy, dy);
				int worst = Integer.MIN_VALUE;
				int step = Math.max(1, p.size() / 40);
				for (int j = 0; j < p.size(); j += step) {
					int x = origin.getX() + p.dx[j], z = origin.getZ() + p.dz[j];
					if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) continue;
					int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					// How far the voxel at this column's lowest point must move to touch the ground.
					worst = Math.max(worst, ground - (baseY + p.dy[j]) - 1);
				}
				if (worst == Integer.MIN_VALUE) continue;
				shift[i] = Math.max(-70, Math.min(6, worst));
			}
		}

		public boolean done() { return part >= parts.size() && deferred.isEmpty(); }

		public int placed() { return placed; }

		/** Places up to {@code budget} blocks. Blocks in chunks that are not loaded wait and are tried again. */
		public void step(int budget) {
			int baseY = origin.getY() - SINK;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (budget > 0 && part < parts.size()) {
				Part p = parts.get(part);
				if (index >= p.size()) { part++; index = 0; continue; }
				int i = index++;
				pos.set(origin.getX() + p.dx[i], baseY + p.dy[i] + shift[part], origin.getZ() + p.dz[i]);
				budget--;
				if (!level.hasChunkAt(pos)) {
					deferred.add(new long[]{pos.asLong(), p.mat[i]});
					continue;
				}
				put(pos, p.mat[i]);
			}
			if (part >= parts.size() && !deferred.isEmpty()) {
				// Late chunks: look again every few ticks, give up after a while.
				if (++waited % 10 == 0) {
					List<long[]> again = new ArrayList<>();
					for (long[] d : deferred) {
						BlockPos p = BlockPos.of(d[0]);
						if (level.hasChunkAt(p)) put(p, (byte) d[1]);
						else again.add(d);
					}
					deferred.clear();
					deferred.addAll(again);
				}
				if (waited > 600) deferred.clear();
			}
			if (part >= parts.size() && !decorated) {
				decorated = true;
				decorate();
			}
		}

		private void put(BlockPos p, byte mat) {
			if (!replaceable(level, p)) return;
			level.setBlock(p, block(mat, p.getX(), p.getY(), p.getZ()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			placed++;
		}

		/** Lit campfires on the hull keep smoking for good; a few fires lick the plates. */
		private void decorate() {
			Part hull = parts.get(0);
			var rnd = level.getRandom();
			int campfires = 0;
			for (int tries = 0; tries < 400 && campfires < 4; tries++) {
				int j = rnd.nextInt(Math.max(1, hull.size()));
				if (hull.size() == 0) break;
				BlockPos top = new BlockPos(origin.getX() + hull.dx[j], origin.getY() - SINK + hull.dy[j], origin.getZ() + hull.dz[j]);
				if (!level.hasChunkAt(top)) continue;
				// Only the highest block of its column, and only up on the hull.
				if (!level.getBlockState(top.above()).isAir() || !level.getBlockState(top.above(2)).isAir() || top.getY() < origin.getY() - 1) continue;
				if (!level.getBlockState(top).isSolidRender()) continue;
				level.setBlock(top.above(), Blocks.CAMPFIRE.defaultBlockState()
						.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true)
						.setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true), Block.UPDATE_ALL);
				campfires++;
			}
			for (int i = 0; i < 60 && hull.size() > 0; i++) {
				int j = rnd.nextInt(hull.size());
				BlockPos top = new BlockPos(origin.getX() + hull.dx[j], origin.getY() - SINK + hull.dy[j] + 1, origin.getZ() + hull.dz[j]);
				if (level.hasChunkAt(top) && level.getBlockState(top).isAir() && level.getBlockState(top.below()).isSolidRender()) {
					level.setBlock(top, Blocks.FIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** Voxel counts for logs and tests. */
	static String describe(List<Part> parts) {
		StringBuilder sb = new StringBuilder();
		for (Part p : parts) {
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			for (int i = 0; i < p.size(); i++) {
				minX = Math.min(minX, p.dx[i]); maxX = Math.max(maxX, p.dx[i]);
				minY = Math.min(minY, p.dy[i]); maxY = Math.max(maxY, p.dy[i]);
				minZ = Math.min(minZ, p.dz[i]); maxZ = Math.max(maxZ, p.dz[i]);
			}
			sb.append(p.name).append(": ").append(p.size()).append(" blocks, x ").append(minX).append("..").append(maxX)
					.append(", y ").append(minY).append("..").append(maxY).append(", z ").append(minZ).append("..").append(maxZ).append("; ");
		}
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private static int[] copy(int[] a) { return Arrays.copyOf(a, a.length); }

	private ShipWreck() {}
}
