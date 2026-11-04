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

	private static final Map<String, Spec> DEFAULTS = Map.ofEntries(
            Map.entry("cosmos_speed_streak",new Spec(4,2,12,false,.18f,true,Orientation.CAMERA)),
            Map.entry("cosmos_boost_flash",new Spec(4,2,8,false,2.2f,true,Orientation.CAMERA)),
            Map.entry("cosmos_warp_ring",new Spec(4,3,12,false,6f,true,Orientation.FLAT)),
            Map.entry("cosmos_star_dust",new Spec(4,3,36,false,.12f,true,Orientation.CAMERA)),
			Map.entry("power_charge", new Spec(4, 4, 16, true, 0.55f, true, Orientation.CAMERA)),
			Map.entry("power_impact", new Spec(4, 1, 4, false, 1.2f, true, Orientation.CAMERA)),
			Map.entry("power_shockwave", new Spec(4, 2, 8, false, 3.0f, true, Orientation.FLAT)),
			Map.entry("power_sparks_smoke", new Spec(4, 3, 12, false, 1.1f, false, Orientation.CAMERA)),
			Map.entry("ground_fracture", new Spec(4, 5, 20, false, 12f, true, Orientation.FLAT)),
			Map.entry("planet_rift", new Spec(4, 4, 16, false, 6f, true, Orientation.CAMERA)),
			Map.entry("detonation_flash", new Spec(4, 1, 4, false, 10f, true, Orientation.CAMERA)),
			Map.entry("void_dissolve", new Spec(4, 3, 12, false, 2f, true, Orientation.CAMERA)),
			Map.entry("celestial_meteor_trail", new Spec(4, 2, 12, false, 2.2f, true, Orientation.CAMERA)),
			Map.entry("celestial_meteor_impact", new Spec(4, 2, 8, false, 4f, true, Orientation.CAMERA)),
			Map.entry("space_teleport_flash", new Spec(4, 1, 4, false, 1.2f, true, Orientation.CAMERA)),
			Map.entry("space_energy_wisps", new Spec(4, 2, 8, false, 0.5f, true, Orientation.CAMERA)),
			Map.entry("quest_soul_ritual_beam", new Spec(8, 2, 16, false, 4.0f, true, Orientation.CAMERA)),
			Map.entry("quest_soul_cloud_pulse", new Spec(8, 3, 24, false, 6.0f, true, Orientation.CAMERA)),
			Map.entry("quest_soul_water_ripple", new Spec(8, 3, 24, false, 4.0f, true, Orientation.FLAT)),
			Map.entry("quest_soul_stone_rise", new Spec(8, 2, 16, false, 1.6f, true, Orientation.CAMERA)),
			Map.entry("quest_soul_return_flare", new Spec(8, 2, 16, false, 5.0f, true, Orientation.CAMERA)),
			Map.entry("quest_time_gear_lock", new Spec(8, 2, 16, false, 2.2f, true, Orientation.CAMERA)),
			Map.entry("quest_time_clock_start", new Spec(8, 2, 16, false, 4.2f, true, Orientation.CAMERA)),
			Map.entry("quest_time_reward_spiral", new Spec(8, 2, 16, false, 2.0f, true, Orientation.CAMERA)),
			Map.entry("quest_mind_zombie_aura", new Spec(8, 2, 16, false, 1.9f, true, Orientation.CAMERA)),
			Map.entry("quest_mind_essence", new Spec(8, 2, 16, false, 1.3f, true, Orientation.CAMERA)),
			Map.entry("quest_mind_convergence", new Spec(8, 2, 16, false, 3.2f, true, Orientation.CAMERA)),
			Map.entry("quest_mind_reward_burst", new Spec(8, 2, 16, false, 4.2f, true, Orientation.CAMERA)),
			Map.entry("quest_reality_fog", new Spec(8, 6, 96, true, 7.0f, false, Orientation.CAMERA)),
			Map.entry("quest_reality_stone_trail", new Spec(8, 2, 16, false, 0.9f, true, Orientation.CAMERA)),
			Map.entry("quest_reality_arrow_hit", new Spec(8, 2, 16, false, 3.0f, true, Orientation.CAMERA)),
			Map.entry("quest_reality_fog_dispel", new Spec(8, 2, 16, false, 12.0f, true, Orientation.CAMERA)),
			Map.entry("quest_space_portal", new Spec(8, 2, 16, false, 2.4f, true, Orientation.CAMERA)),
			Map.entry("quest_space_frost_shard", new Spec(8, 3, 24, false, 0.45f, true, Orientation.CAMERA)),
			Map.entry("quest_space_return_fold", new Spec(8, 2, 16, false, 3.5f, true, Orientation.CAMERA)),
			Map.entry("quest_space_lava_release", new Spec(8, 2, 16, false, 5.0f, true, Orientation.CAMERA)),
			Map.entry("quest_space_obsidian_wave", new Spec(8, 2, 16, false, 13.0f, true, Orientation.FLAT)),
			Map.entry("reality_charge", new Spec(4, 3, 12, false, 0.8f, true, Orientation.CAMERA)),
			Map.entry("reality_fracture", new Spec(4, 5, 20, false, 5.0f, true, Orientation.CAMERA)),
			Map.entry("reality_rewrite", new Spec(4, 2, 8, false, 2.5f, true, Orientation.CAMERA)),
			Map.entry("reality_restore", new Spec(4, 3, 12, false, 8.0f, true, Orientation.CAMERA)),
			Map.entry("reality_butterfly", new Spec(4, 2, 80, true, 0.35f, true, Orientation.CAMERA)),
			Map.entry("reality_petal", new Spec(4, 3, 60, true, 0.18f, true, Orientation.CAMERA)),
			Map.entry("reality_glass", new Spec(4, 3, 36, true, 0.4f, true, Orientation.CAMERA)),
			Map.entry("reality_aether", new Spec(4, 3, 48, true, 0.55f, true, Orientation.CAMERA)),
			Map.entry("mind_hold", new Spec(4, 3, 36, true, 1.8f, true, Orientation.CAMERA)),
			Map.entry("mind_lift_fracture", new Spec(4, 3, 12, false, 5f, true, Orientation.FLAT)),
			Map.entry("mind_throw_trail", new Spec(4, 2, 36, true, 1.4f, true, Orientation.CAMERA)),
			Map.entry("mind_rupture", new Spec(4, 2, 8, false, 3f, true, Orientation.CAMERA)),
			Map.entry("soul_wisp", new Spec(4, 3, 36, true, 0.7f, true, Orientation.CAMERA)),
			Map.entry("soul_siphon", new Spec(4, 2, 36, true, 1.3f, true, Orientation.CAMERA)),
			Map.entry("soul_mass_wave", new Spec(4, 3, 12, false, 8f, true, Orientation.FLAT)),
			Map.entry("soul_absorption", new Spec(4, 3, 12, false, 1.2f, true, Orientation.CAMERA)),
			Map.entry("uru_guard", new Spec(5, 2, 10, false, 1.5f, true, Orientation.CAMERA)),
			Map.entry("meteor_reticle", new Spec(4, 3, 9, true, 1.0f, true, Orientation.FLAT)),
			Map.entry("extractor_tether", new Spec(4, 1, 6, true, 1.4f, true, Orientation.CAMERA)),
			Map.entry("extractor_gather", new Spec(2, 1, 14, false, 1.1f, true, Orientation.CAMERA)),
			Map.entry("extractor_success", new Spec(2, 1, 10, false, 0.45f, true, Orientation.CAMERA)),
			Map.entry("extractor_seal", new Spec(2, 1, 16, false, 0.5f, true, Orientation.CAMERA)),
			Map.entry("extractor_mote", new Spec(2, 1, 12, false, 0.45f, true, Orientation.CAMERA)),
			Map.entry("thanos/arrival_ring", new Spec(8, 2, 16, false, 7.0f, true, Orientation.FLAT)),
			Map.entry("thanos/arrival_flash", new Spec(8, 2, 16, false, 5.0f, true, Orientation.CAMERA)),
			Map.entry("thanos/arrival_streak", new Spec(8, 1, 8, false, 0.7f, true, Orientation.CAMERA)),
			Map.entry("thanos/ship_mist", new Spec(8, 5, 40, false, 9.0f, false, Orientation.CAMERA)),
			Map.entry("thanos/rift_sparks", new Spec(8, 2, 16, false, 1.4f, true, Orientation.CAMERA)),
			Map.entry("thanos/rift_close", new Spec(8, 2, 16, false, 1.4f, true, Orientation.CAMERA)),
			Map.entry("thanos/dragon_charge", new Spec(8, 1, 8, false, 1.0f, true, Orientation.CAMERA)),
			Map.entry("thanos/dragon_impact", new Spec(8, 3, 24, false, 1.2f, true, Orientation.CAMERA)),
			Map.entry("thanos/dragon_shockwave", new Spec(8, 3, 24, false, 1.4f, true, Orientation.CAMERA)),
			Map.entry("thanos/end_hull_halo", new Spec(8, 4, 32, false, 4.0f, true, Orientation.CAMERA)),
			Map.entry("thanos/sword_arc", new Spec(8, 1, 8, false, 4.6f, true, Orientation.CAMERA)),
			Map.entry("thanos/sword_sparks", new Spec(8, 1, 8, false, 1.7f, true, Orientation.CAMERA)),
			Map.entry("thanos/slam_wave", new Spec(8, 2, 16, false, 6.0f, true, Orientation.FLAT)),
			Map.entry("thanos/slam_dust", new Spec(8, 3, 24, false, 4.0f, false, Orientation.CAMERA)),
			Map.entry("thanos/armor_hit", new Spec(8, 1, 8, false, 1.5f, true, Orientation.CAMERA)));

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
		for (String file : new String[]{"power_vfx_presets.json", "destruction_vfx_presets.json", "../celestial_apocalypse/meteor_vfx_presets.json", "../space_stone/particle_presets.json", "../reality_stone/particle_presets.json", "../cosmos/particle_presets.json", "../mind_stone/particle_presets.json", "../soul_stone/particle_presets.json"}) {
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
				if (o.has("orientation")) orient = (o.get("orientation").getAsString().contains("terrain") || o.get("orientation").getAsString().contains("surface")) ? Orientation.FLAT : Orientation.CAMERA;
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

