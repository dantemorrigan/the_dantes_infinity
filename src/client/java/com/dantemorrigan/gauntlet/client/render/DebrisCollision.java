package com.dantemorrigan.gauntlet.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Collision of the player with a client-side block mesh that is moved, rotated and scaled around its pivot
 * (the exact transform used to draw it). The player is approximated by three spheres (feet, torso, head);
 * every block of the mesh is a real unit cube in the mesh's local space, so what you hit is what you see.
 */
public final class DebrisCollision {
	/** Contact: unit normal pointing from the debris toward the player, penetration depth in world blocks. */
	public record Hit(Vec3 normal, double depth) {}

	private static final Map<BlockMeshes.Mesh, Float> BOUNDS = new IdentityHashMap<>();
	private static final float[] SPHERE_Y = {0.4f, 0.95f, 1.5f};
	private static final float SPHERE_R = 0.38f;

	/** Radius (mesh units) of a sphere around the pivot that contains the whole mesh. */
	public static float bound(BlockMeshes.Mesh mesh) {
		return BOUNDS.computeIfAbsent(mesh, m -> {
			float max = 0;
			for (BlockMeshes.Baked b : m.blocks()) {
				float dx = Math.max(Math.abs(b.pos().getX() - m.cx()), Math.abs(b.pos().getX() + 1 - m.cx()));
				float dy = Math.max(Math.abs(b.pos().getY() - m.cy()), Math.abs(b.pos().getY() + 1 - m.cy()));
				float dz = Math.max(Math.abs(b.pos().getZ() - m.cz()), Math.abs(b.pos().getZ() + 1 - m.cz()));
				max = Math.max(max, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
			}
			return max;
		});
	}

	/** Deepest contact between the player standing at {@code feet} and the mesh posed at {@code pos}/{@code rot}/{@code scale}, or null. */
	public static Hit test(BlockMeshes.Mesh mesh, Vec3 pos, Quaternionf rot, float scale, Vec3 feet) {
		float reach = bound(mesh) * scale + 2.5f;
		if (feet.add(0, 0.9, 0).distanceToSqr(pos) > reach * reach) return null;
		Quaternionf inv = new Quaternionf(rot).invert();
		Hit best = null;
		for (float sy : SPHERE_Y) {
			Vec3 c = feet.add(0, sy, 0);
			Vector3f q = inv.transform(new Vector3f((float) (c.x - pos.x), (float) (c.y - pos.y), (float) (c.z - pos.z))).div(scale);
			float r = SPHERE_R / scale;
			for (BlockMeshes.Baked b : mesh.blocks()) {
				float minX = b.pos().getX() - mesh.cx(), minY = b.pos().getY() - mesh.cy(), minZ = b.pos().getZ() - mesh.cz();
				float cx = Math.max(minX, Math.min(q.x, minX + 1)), cy = Math.max(minY, Math.min(q.y, minY + 1)), cz = Math.max(minZ, Math.min(q.z, minZ + 1));
				float dx = q.x - cx, dy = q.y - cy, dz = q.z - cz;
				float d2 = dx * dx + dy * dy + dz * dz;
				if (d2 >= r * r) continue;
				Vector3f n;
				float depthLocal;
				if (d2 > 1e-8f) {
					float d = (float) Math.sqrt(d2);
					n = new Vector3f(dx / d, dy / d, dz / d);
					depthLocal = r - d;
				} else { // sphere centre inside the cube: leave through the nearest face
					float[] face = {q.x - minX, minX + 1 - q.x, q.y - minY, minY + 1 - q.y, q.z - minZ, minZ + 1 - q.z};
					int k = 0;
					for (int i = 1; i < 6; i++) if (face[i] < face[k]) k = i;
					n = new Vector3f(k == 0 ? -1 : k == 1 ? 1 : 0, k == 2 ? -1 : k == 3 ? 1 : 0, k == 4 ? -1 : k == 5 ? 1 : 0);
					depthLocal = r + face[k];
				}
				double depth = depthLocal * scale;
				if (best == null || depth > best.depth()) {
					Vector3f w = rot.transform(n);
					best = new Hit(new Vec3(w.x, w.y, w.z).normalize(), depth);
				}
			}
		}
		return best;
	}

	private DebrisCollision() {}
}
