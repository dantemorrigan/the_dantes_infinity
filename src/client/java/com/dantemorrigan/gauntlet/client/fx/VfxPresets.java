package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the pack's effect presets (power_vfx_presets.json, destruction_vfx_presets.json). These are our own
 * authoring formats, not vanilla files, so we parse them here. Values from the files override the defaults.
 */
public final class VfxPresets {
	public enum Orientation { CAMERA, FLAT }

	public record Spec(int frames, int ticksPerFrame, int lifetime, boolean loop, float width, boolean fullBright, Orientation orientation) {}

	private static final Map<String, Spec> DEFAULTS = Map.of(
			"power_charge", new Spec(4, 4, 16, true, 0.55f, true, Orientation.CAMERA),
			"power_impact", new Spec(4, 1, 4, false, 1.2f, true, Orientation.CAMERA),
			"power_shockwave", new Spec(4, 2, 8, false, 3.0f, true, Orientation.FLAT),
			"power_sparks_smoke", new Spec(4, 3, 12, false, 1.1f, false, Orientation.CAMERA),
			"ground_fracture", new Spec(4, 5, 20, false, 12f, true, Orientation.FLAT),
			"planet_rift", new Spec(4, 4, 16, false, 6f, true, Orientation.CAMERA),
			"detonation_flash", new Spec(4, 1, 4, false, 10f, true, Orientation.CAMERA),
			"void_dissolve", new Spec(4, 3, 12, false, 2f, true, Orientation.CAMERA),
			"celestial_meteor_trail", new Spec(4, 2, 12, false, 2.2f, true, Orientation.CAMERA),
			"celestial_meteor_impact", new Spec(4, 2, 8, false, 4f, true, Orientation.CAMERA));

	private static Map<String, Spec> loaded;

	public static Spec get(String name) {
		if (loaded == null) load();
		return loaded.getOrDefault(name, DEFAULTS.get(name));
	}

	public static void invalidate() {
		loaded = null;
	}

	private static void load() {
		Map<String, Spec> map = new HashMap<>(DEFAULTS);
		for (String file : new String[]{"power_vfx_presets.json", "destruction_vfx_presets.json", "../celestial_apocalypse/meteor_vfx_presets.json"}) {
			JsonObject root = readJson(InfinityGauntlet.id(file.startsWith("../") ? file.substring(3) : "planet_destruction/" + file));
			if (root == null) continue;
			JsonObject effects = root.has("effects") ? root.getAsJsonObject("effects") : root;
			for (Map.Entry<String, JsonElement> e : effects.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				Spec def = DEFAULTS.get(e.getKey());
				if (def == null) continue;
				JsonObject o = e.getValue().getAsJsonObject();
				int frames = o.has("frames") ? o.get("frames").getAsInt() : def.frames();
				int tpf = o.has("ticks_per_frame") ? o.get("ticks_per_frame").getAsInt() : def.ticksPerFrame();
				int life = o.has("lifetime_ticks") ? o.get("lifetime_ticks").getAsInt() : def.lifetime();
				if (e.getKey().equals("celestial_meteor_trail")) life = def.lifetime(); // trail: fade over its own life, not looped
				boolean loop = e.getKey().equals("celestial_meteor_trail") ? false : o.has("loop") ? o.get("loop").getAsBoolean() : def.loop();
				float width = o.has("width_blocks") ? o.get("width_blocks").getAsFloat() : o.has("full_quad_width_blocks") ? o.get("full_quad_width_blocks").getAsFloat()
						: o.has("suggested_full_quad_width_blocks") ? o.get("suggested_full_quad_width_blocks").getAsFloat() : def.width();
				boolean bright = o.has("full_bright") ? o.get("full_bright").getAsBoolean() : def.fullBright();
				Orientation orient = def.orientation();
				if (o.has("orientation")) orient = o.get("orientation").getAsString().contains("terrain") ? Orientation.FLAT : Orientation.CAMERA;
				map.put(e.getKey(), new Spec(frames, tpf, life, loop, width, bright, orient));
			}
		}
		loaded = map;
	}

	public static JsonObject readJson(Identifier id) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
			if (res.isEmpty()) return null;
			try (Reader reader = res.get().openAsReader()) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (Exception e) {
			InfinityGauntlet.LOGGER.warn("Failed to read {}", id, e);
			return null;
		}
	}

	private VfxPresets() {}
}
