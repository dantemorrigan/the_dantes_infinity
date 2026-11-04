package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Bedrock geometry file (format 1.12, per-face UV) from the Thanos pack, drawn with our own quads.
 *
 * <p>Space: the pack's native Blockbench space, which is also Java's entity space after the usual (-1,-1,1) flip:
 * Y up, the model faces -Z, its right hand is +X, 16 units per block, origin at the feet. Bone rotations follow the
 * Bedrock convention (all three axes counter to the right-hand rule here), applied Z·Y·X about the pivot.
 */
public final class GeoModel {
	public static final class Cube {
		final float x0, y0, z0, x1, y1, z1;
		/** north, south, east, west, up, down: u0, v0, u1, v1 (0..1), or null if the face is absent. */
		final float[][] uv = new float[6][];
		/** Stable per-cube value 0..1 (dust order and such). */
		public final float seed;
		public final float centerY;

		Cube(float x0, float y0, float z0, float x1, float y1, float z1, float seed) {
			this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1;
			this.seed = seed;
			this.centerY = (y0 + y1) * 0.5f;
		}

		public Vector3f center() {
			return new Vector3f((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f);
		}
	}

	public static final class Bone {
		public final String name;
		public final Bone parent;
		public final float px, py, pz;
		final List<Cube> cubes = new ArrayList<>();
		final List<Bone> children = new ArrayList<>();

		Bone(String name, Bone parent, float px, float py, float pz) {
			this.name = name; this.parent = parent; this.px = px; this.py = py; this.pz = pz;
		}

		public List<Cube> cubes() { return cubes; }
	}

	/** Per-bone animated transform: rotation in degrees (Bedrock sign), offset in model units. */
	public static final class BonePose {
		public float rx, ry, rz, ox, oy, oz;
		public boolean hidden;

		public void reset() { rx = ry = rz = ox = oy = oz = 0; hidden = false; }

		public BonePose add(float[] rot, float[] pos) {
			if (rot != null) { rx += rot[0]; ry += rot[1]; rz += rot[2]; }
			if (pos != null) { ox += pos[0]; oy += pos[1]; oz += pos[2]; }
			return this;
		}
	}

	/** Called per cube: return the vertex color (ARGB) or 0 to skip the cube. */
	public interface CubeTint {
		int color(Bone bone, Cube cube);
	}

	public final int texW, texH;
	private final List<Bone> roots = new ArrayList<>();
	private final Map<String, Bone> bones = new HashMap<>();
	private final List<Bone> order = new ArrayList<>();

	private GeoModel(int texW, int texH) {
		this.texW = texW;
		this.texH = texH;
	}

	public Bone bone(String name) { return bones.get(name); }

	public List<Bone> bones() { return order; }

	public static GeoModel load(String file) {
		JsonObject root = VfxPresets.readJson(InfinityGauntlet.id("thanos/" + file));
		if (root == null) {
			InfinityGauntlet.LOGGER.warn("Missing Thanos model {}", file);
			return new GeoModel(256, 256);
		}
		JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = geo.getAsJsonObject("description");
		GeoModel model = new GeoModel(desc.get("texture_width").getAsInt(), desc.get("texture_height").getAsInt());
		int cubeIndex = 0;
		for (JsonElement e : geo.getAsJsonArray("bones")) {
			JsonObject b = e.getAsJsonObject();
			String name = b.get("name").getAsString();
			Bone parent = b.has("parent") ? model.bones.get(b.get("parent").getAsString()) : null;
			float[] pivot = b.has("pivot") ? vec(b.getAsJsonArray("pivot")) : new float[3];
			Bone bone = new Bone(name, parent, pivot[0], pivot[1], pivot[2]);
			model.bones.put(name, bone);
			model.order.add(bone);
			if (parent != null) parent.children.add(bone);
			else model.roots.add(bone);
			if (!b.has("cubes")) continue;
			for (JsonElement ce : b.getAsJsonArray("cubes")) {
				JsonObject c = ce.getAsJsonObject();
				float[] o = vec(c.getAsJsonArray("origin")), s = vec(c.getAsJsonArray("size"));
				float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
				float seed = (float) (((cubeIndex++ * 0x9E3779B9L) >>> 8) & 0xFFFF) / 65535f;
				Cube cube = new Cube(o[0] - inflate, o[1] - inflate, o[2] - inflate,
						o[0] + s[0] + inflate, o[1] + s[1] + inflate, o[2] + s[2] + inflate, seed);
				JsonObject uv = c.has("uv") && c.get("uv").isJsonObject() ? c.getAsJsonObject("uv") : null;
				String[] names = {"north", "south", "east", "west", "up", "down"};
				for (int f = 0; f < 6; f++) {
					if (uv == null || !uv.has(names[f])) continue;
					JsonObject face = uv.getAsJsonObject(names[f]);
					float[] u = vec(face.getAsJsonArray("uv")), size = vec(face.getAsJsonArray("uv_size"));
					cube.uv[f] = new float[]{u[0] / model.texW, u[1] / model.texH, (u[0] + size[0]) / model.texW, (u[1] + size[1]) / model.texH};
				}
				bone.cubes.add(cube);
			}
		}
		return model;
	}

	private static float[] vec(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	// ------------------------------------------------------------------ transforms

	/** Applies this bone's transform (relative to its parent) to the pose. */
	public static void applyBone(PoseStack pose, Bone bone, BonePose p) {
		float s = 1f / 16f;
		if (p != null && (p.ox != 0 || p.oy != 0 || p.oz != 0)) pose.translate(-p.ox * s, p.oy * s, p.oz * s);
		pose.translate(bone.px * s, bone.py * s, bone.pz * s);
		if (p != null) {
			if (p.rz != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-p.rz));
			if (p.ry != 0) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-p.ry));
			if (p.rx != 0) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-p.rx));
		}
		pose.translate(-bone.px * s, -bone.py * s, -bone.pz * s);
	}

	/** Moves the pose into the bone's space (all parents included), e.g. to attach the sword to its socket. */
	public void transformTo(PoseStack pose, String boneName, Map<String, BonePose> poses) {
		Bone bone = bones.get(boneName);
		if (bone == null) return;
		List<Bone> chain = new ArrayList<>();
		for (Bone b = bone; b != null; b = b.parent) chain.add(0, b);
		for (Bone b : chain) applyBone(pose, b, poses == null ? null : poses.get(b.name));
	}

	/** World-space (pose-space) point of a bone's pivot after animation. */
	public Vector3f pivotOf(String boneName, Map<String, BonePose> poses, PoseStack base) {
		Bone bone = bones.get(boneName);
		if (bone == null) return new Vector3f();
		base.pushPose();
		transformTo(base, boneName, poses);
		Vector3f out = base.last().pose().transformPosition(new Vector3f(bone.px / 16f, bone.py / 16f, bone.pz / 16f));
		base.popPose();
		return out;
	}

	// ------------------------------------------------------------------ drawing

	public void render(PoseStack pose, VertexConsumer out, Map<String, BonePose> poses, int light, int overlay, CubeTint tint) {
		for (Bone root : roots) renderBone(pose, out, root, poses, light, overlay, tint);
	}

	private void renderBone(PoseStack pose, VertexConsumer out, Bone bone, Map<String, BonePose> poses, int light, int overlay, CubeTint tint) {
		BonePose p = poses == null ? null : poses.get(bone.name);
		if (p != null && p.hidden) return;
		pose.pushPose();
		applyBone(pose, bone, p);
		PoseStack.Pose last = pose.last();
		for (Cube c : bone.cubes) {
			int color = tint == null ? -1 : tint.color(bone, c);
			if (color == 0) continue;
			cube(out, last, c, color, light, overlay);
		}
		for (Bone child : bone.children) renderBone(pose, out, child, poses, light, overlay, tint);
		pose.popPose();
	}

	private static void cube(VertexConsumer out, PoseStack.Pose pose, Cube c, int color, int light, int overlay) {
		float s = 1f / 16f;
		float x0 = c.x0 * s, y0 = c.y0 * s, z0 = c.z0 * s, x1 = c.x1 * s, y1 = c.y1 * s, z1 = c.z1 * s;
		Matrix4f m = pose.pose();
		// Corners per face in the order TL, TR, BR, BL as seen from outside; emitted BL, BR, TR, TL (counter-clockwise).
		face(out, pose, m, c.uv[0], color, light, overlay, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
		face(out, pose, m, c.uv[1], color, light, overlay, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
		face(out, pose, m, c.uv[2], color, light, overlay, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
		face(out, pose, m, c.uv[3], color, light, overlay, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
		face(out, pose, m, c.uv[4], color, light, overlay, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
		face(out, pose, m, c.uv[5], color, light, overlay, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
	}

	private static void face(VertexConsumer out, PoseStack.Pose pose, Matrix4f m, float[] uv, int color, int light, int overlay,
			float nx, float ny, float nz,
			float tlx, float tly, float tlz, float trx, float try_, float trz, float brx, float bry, float brz, float blx, float bly, float blz) {
		if (uv == null) return;
		out.addVertex(m, blx, bly, blz).setColor(color).setUv(uv[0], uv[3]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
		out.addVertex(m, brx, bry, brz).setColor(color).setUv(uv[2], uv[3]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
		out.addVertex(m, trx, try_, trz).setColor(color).setUv(uv[2], uv[1]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
		out.addVertex(m, tlx, tly, tlz).setColor(color).setUv(uv[0], uv[1]).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
