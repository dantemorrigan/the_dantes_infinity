package com.dantemorrigan.gauntlet.client.space;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loader for the pack's custom formats (assets/infinity_gauntlet/planet_destruction/*): block blueprints,
 * asset catalog (scale/centers), planet stages, cinematic timeline and sky orientation. Read through the
 * resource manager, so resource packs may override them. Cached until {@link #invalidate()}.
 */
public final class PackData {
	public record Blueprint(String id, List<BlockPos> positions, List<BlockState> states, float cx, float cy, float cz) {}

	public record PlanetPart(String model, float tx, float ty, float tz, float rx, float ry, float rz) {}

	public record Stage(String name, List<PlanetPart> parts) {}

	public record TimelineEvent(int tick, String event, JsonObject data) {}

	private static final Map<String, Blueprint> BLUEPRINTS = new HashMap<>();
	private static Map<String, float[]> centers;
	private static List<Stage> stages;
	private static float modelScalePerLogicalBlock = 11.034483f;
	private static List<TimelineEvent> timeline;
	private static JsonObject sky;

	public static void invalidate() {
		BLUEPRINTS.clear();
		centers = null;
		stages = null;
		timeline = null;
		sky = null;
		VfxPresets.invalidate();
	}

	private static JsonObject read(String file) {
		return VfxPresets.readJson(InfinityGauntlet.id("planet_destruction/" + file));
	}

	private static float[] center(String id) {
		if (centers == null) {
			centers = new HashMap<>();
			JsonElement root = readRaw("asset_catalog.json");
			if (root != null && root.isJsonArray()) {
				for (JsonElement e : root.getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					JsonArray c = o.getAsJsonArray("blueprint_center");
					centers.put(o.get("id").getAsString(), new float[]{c.get(0).getAsFloat(), c.get(1).getAsFloat(), c.get(2).getAsFloat()});
				}
			}
		}
		return centers.getOrDefault(id, new float[]{0.5f, 0.5f, 0.5f});
	}

	private static JsonElement readRaw(String file) {
		try {
			var res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(InfinityGauntlet.id("planet_destruction/" + file));
			if (res.isEmpty()) return null;
			try (var reader = res.get().openAsReader()) {
				return com.google.gson.JsonParser.parseReader(reader);
			}
		} catch (Exception e) {
			InfinityGauntlet.LOGGER.warn("Failed to read {}", file, e);
			return null;
		}
	}

	/** Blueprint (format infinity_gauntlet:block_fragment_v1): palette of block ids + per-block pos/state/properties. */
	public static Blueprint blueprint(String id) {
		return BLUEPRINTS.computeIfAbsent(id, key -> {
			JsonObject root = read("blueprints/" + key + ".json");
			List<BlockPos> pos = new ArrayList<>();
			List<BlockState> states = new ArrayList<>();
			if (root != null) {
				JsonArray palette = root.getAsJsonArray("palette");
				for (JsonElement e : root.getAsJsonArray("blocks")) {
					JsonObject b = e.getAsJsonObject();
					JsonArray p = b.getAsJsonArray("pos");
					String blockId = palette.get(b.get("state").getAsInt()).getAsString();
					BlockState state = parseState(blockId, b.has("properties") ? b.getAsJsonObject("properties") : null);
					if (state.isAir()) continue;
					pos.add(new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()));
					states.add(state);
				}
			}
			float[] c = center(key);
			// blueprint_center (asset_catalog.json) is the pivot in block coordinates.
			return new Blueprint(key, pos, states, c[0], c[1], c[2]);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static BlockState parseState(String id, JsonObject props) {
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
		BlockState state = block.map(Block::defaultBlockState).orElse(Blocks.STONE.defaultBlockState());
		if (props != null) {
			for (Map.Entry<String, JsonElement> e : props.entrySet()) {
				Property prop = state.getBlock().getStateDefinition().getProperty(e.getKey());
				if (prop == null) continue;
				Optional value = prop.getValue(e.getValue().getAsString());
				if (value.isPresent()) state = state.setValue(prop, (Comparable) value.get());
			}
		}
		return state;
	}

	public static List<Stage> stages() {
		if (stages == null) {
			stages = new ArrayList<>();
			JsonObject root = read("planet_stages.json");
			if (root != null) {
				if (root.has("renderer_model_scale_for_one_logical_block")) modelScalePerLogicalBlock = root.get("renderer_model_scale_for_one_logical_block").getAsFloat();
				for (JsonElement se : root.getAsJsonArray("stages")) {
					JsonObject so = se.getAsJsonObject();
					List<PlanetPart> parts = new ArrayList<>();
					for (JsonElement pe : so.getAsJsonArray("parts")) {
						JsonObject po = pe.getAsJsonObject();
						JsonArray t = po.getAsJsonArray("translation_logical_blocks");
						JsonArray r = po.getAsJsonArray("rotation_degrees");
						String model = po.get("model").getAsString();
						parts.add(new PlanetPart(model.substring(model.lastIndexOf('/') + 1),
								t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat(),
								r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat()));
					}
					stages.add(new Stage(so.get("stage").getAsString(), parts));
				}
			}
		}
		return stages;
	}

	public static float modelScalePerLogicalBlock() {
		stages();
		return modelScalePerLogicalBlock;
	}

	public static List<TimelineEvent> timeline() {
		if (timeline == null) {
			timeline = new ArrayList<>();
			JsonObject root = read("cinematic_timeline.json");
			if (root != null) {
				for (JsonElement e : root.getAsJsonArray("events")) {
					JsonObject o = e.getAsJsonObject();
					timeline.add(new TimelineEvent(o.get("tick").getAsInt(), o.get("event").getAsString(), o));
				}
			}
			if (timeline.isEmpty()) {
				String[][] def = {{"0", "impact"}, {"4", "ground_cracks"}, {"20", "ground_cracks"}, {"35", "lift_debris"}, {"55", "planet_rift"},
						{"65", "flash_to_white"}, {"68", "server_transfer_to_void_dimension"}, {"75", "fade_from_white"}, {"80", "reveal_planet"}, {"180", "idle_space"}};
				for (String[] d : def) timeline.add(new TimelineEvent(Integer.parseInt(d[0]), d[1], new JsonObject()));
			}
		}
		return timeline;
	}

	public static int timelineTick(String event, int fallback) {
		for (TimelineEvent e : timeline()) if (e.event().equals(event)) return e.tick();
		return fallback;
	}

	/** Sky cube orientation (sky_orientation.json): for each face, how image (u,v) maps to a direction. */
	public static JsonObject sky() {
		if (sky == null) {
			sky = read("sky_orientation.json");
			if (sky == null) sky = new JsonObject();
		}
		return sky;
	}

	private PackData() {}
}
