package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bedrock animation keyframes (format 1.8.0) from the pack: linear interpolation between keys, rotation in degrees,
 * position in model units. Animations can also be built in code with the same structure ({@link #builder}).
 */
public final class GeoAnimation {
	/** time (s) -> xyz */
	private final Map<String, TreeMap<Float, float[]>> rotation = new HashMap<>();
	private final Map<String, TreeMap<Float, float[]>> position = new HashMap<>();
	public final float length;
	public final boolean loop;

	private GeoAnimation(float length, boolean loop) {
		this.length = length;
		this.loop = loop;
	}

	public static Map<String, GeoAnimation> load(String file) {
		Map<String, GeoAnimation> out = new HashMap<>();
		JsonObject root = VfxPresets.readJson(InfinityGauntlet.id("thanos/" + file));
		if (root == null || !root.has("animations")) {
			InfinityGauntlet.LOGGER.warn("Missing Thanos animations {}", file);
			return out;
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
			JsonObject a = e.getValue().getAsJsonObject();
			GeoAnimation anim = new GeoAnimation(a.has("animation_length") ? a.get("animation_length").getAsFloat() : 1f,
					a.has("loop") && a.get("loop").getAsBoolean());
			if (a.has("bones")) {
				for (Map.Entry<String, JsonElement> b : a.getAsJsonObject("bones").entrySet()) {
					JsonObject channels = b.getValue().getAsJsonObject();
					if (channels.has("rotation")) anim.rotation.put(b.getKey(), keys(channels.get("rotation")));
					if (channels.has("position")) anim.position.put(b.getKey(), keys(channels.get("position")));
				}
			}
			String name = e.getKey();
			int dot = name.lastIndexOf('.');
			out.put(dot >= 0 ? name.substring(dot + 1) : name, anim);
		}
		return out;
	}

	private static TreeMap<Float, float[]> keys(JsonElement el) {
		TreeMap<Float, float[]> map = new TreeMap<>();
		if (el.isJsonArray()) {
			map.put(0f, xyz(el));
			return map;
		}
		for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
			JsonElement v = k.getValue();
			if (v.isJsonObject()) v = v.getAsJsonObject().has("post") ? v.getAsJsonObject().get("post") : v.getAsJsonObject().get("pre");
			map.put(Float.parseFloat(k.getKey()), xyz(v));
		}
		return map;
	}

	private static float[] xyz(JsonElement v) {
		var a = v.getAsJsonArray();
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	/** Adds this animation at time {@code t} (seconds) with the given weight into the bone poses. */
	public void apply(Map<String, GeoModel.BonePose> poses, float t, float weight) {
		if (weight <= 0) return;
		float time = loop && length > 0 ? t % length : Math.min(t, length);
		for (Map.Entry<String, TreeMap<Float, float[]>> e : rotation.entrySet()) {
			float[] v = sample(e.getValue(), time);
			poses.computeIfAbsent(e.getKey(), k -> new GeoModel.BonePose()).add(scale(v, weight), null);
		}
		for (Map.Entry<String, TreeMap<Float, float[]>> e : position.entrySet()) {
			float[] v = sample(e.getValue(), time);
			poses.computeIfAbsent(e.getKey(), k -> new GeoModel.BonePose()).add(null, scale(v, weight));
		}
	}

	public boolean touches(String bone) {
		return rotation.containsKey(bone) || position.containsKey(bone);
	}

	private static float[] scale(float[] v, float w) {
		return w == 1 ? v : new float[]{v[0] * w, v[1] * w, v[2] * w};
	}

	private static float[] sample(TreeMap<Float, float[]> keys, float t) {
		Map.Entry<Float, float[]> lo = keys.floorEntry(t), hi = keys.ceilingEntry(t);
		if (lo == null) return hi.getValue();
		if (hi == null || hi.getKey().equals(lo.getKey())) return lo.getValue();
		float k = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
		// Smoothstep between keys: the pack's keys are poses, an eased blend reads less robotic than a straight line.
		k = k * k * (3 - 2 * k);
		float[] a = lo.getValue(), b = hi.getValue();
		return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
	}

	public static Builder builder(float length, boolean loop) {
		return new Builder(new GeoAnimation(length, loop));
	}

	public static final class Builder {
		private final GeoAnimation anim;

		private Builder(GeoAnimation anim) { this.anim = anim; }

		public Builder rot(String bone, float time, float x, float y, float z) {
			anim.rotation.computeIfAbsent(bone, k -> new TreeMap<>()).put(time, new float[]{x, y, z});
			return this;
		}

		public Builder pos(String bone, float time, float x, float y, float z) {
			anim.position.computeIfAbsent(bone, k -> new TreeMap<>()).put(time, new float[]{x, y, z});
			return this;
		}

		public GeoAnimation build() { return anim; }
	}
}
